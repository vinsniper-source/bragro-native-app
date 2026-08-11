package com.bragro.mobile.data.repo

import android.content.Context
import com.bragro.mobile.data.AppLog
import com.bragro.mobile.data.TokenStore
import com.bragro.mobile.data.local.AppDatabase
import com.bragro.mobile.data.local.PendingSyncEntity
import com.bragro.mobile.data.local.RecordEntity
import com.bragro.mobile.data.model.RecordsRequest
import com.bragro.mobile.data.model.SyncRequest
import com.bragro.mobile.data.model.SyncResponse
import com.bragro.mobile.data.remote.NetworkModule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import retrofit2.Response
import java.util.UUID

sealed class SaveResult {
    data object SavedOnline : SaveResult()
    data object SavedOffline : SaveResult()
    data class Failure(val message: String) : SaveResult()
}

/** Lista/cria/edita registros de QUALQUER um dos 16 modulos -- o mesmo
 * repositorio serve pra todos, guiado pelo DomainConfig (ver
 * ConfigRepository), exatamente como o motor generico do site
 * (components/domain/record-form.tsx). Grava OFFLINE sempre primeiro (Room);
 * quando ha conexao, tenta sincronizar na hora, e o que nao conseguir fica
 * na fila (PendingSyncEntity) pro SyncWorker tentar de novo depois. */
class RecordRepository(private val context: Context) {
    private val db = AppDatabase.get(context)
    private val tokenStore = TokenStore(context)

    fun observeRecords(domainId: String): Flow<List<Map<String, String?>>> =
        db.recordDao().observeByDomain(domainId).map { list -> list.map { jsonStringToMap(it.fieldsJson) } }

    fun observePendingCount(): Flow<Int> = db.pendingSyncDao().observeCount()

    // Lista completa da fila (não só a contagem) -- pedido do usuário
    // ("poderia saber ao clicar na frase quais são os lançamentos"), pro
    // banner "N lançamentos aguardando conexão" do Início abrir um diálogo
    // com o detalhe de cada pendência.
    fun observePending(): Flow<List<PendingSyncEntity>> = db.pendingSyncDao().observeAll()

    suspend fun getRecord(domainId: String, id: String): Map<String, String?>? =
        db.recordDao().byId(domainId, id)?.let { jsonStringToMap(it.fieldsJson) }

    /** orgId da sessao ATUALMENTE logada (Task #124, isolamento de cache
     * por organizacao) -- gravado em todo RecordEntity/PendingSyncEntity
     * criado a partir de agora, pra AuthRepository.login() conseguir
     * decidir depois se a fila pendente pertence a mesma organizacao que
     * esta logando ou a uma anterior (e deve ser limpa). Null se, por
     * algum motivo, nao houver sessao cacheada ainda (nao deveria
     * acontecer em uso normal -- login() sempre grava a SessionEntity
     * antes de qualquer tela que chame create/update/deleteRecord). */
    private suspend fun currentOrgId(): String? = db.sessionDao().get()?.orgId

    /** Grava o "expectedVersion" (ultima edicao conhecida, ver
     * AuditInfoRepository/DomainListScreen.loadAuditInfo) de um registro
     * especifico -- Task #124 (deteccao de conflito de sync). Chamado
     * sempre que a tela de lista busca o rotulo "Editado por" de cada
     * card, pra manter esse valor atualizado localmente ANTES do usuario
     * eventualmente editar o registro (updateRecord le esse campo pra
     * mandar de volta ao backend em SyncRequest.expectedVersion). */
    suspend fun updateExpectedVersion(domainId: String, id: String, version: String) {
        db.recordDao().updateExpectedVersion(domainId, id, version)
    }

    /** "Copiar último lançamento" (Task #51/#77) -- mesmo mecanismo generico
     * do site (data-table.tsx: abre o formulario de criacao pre-preenchido
     * com os campos do registro mais recente do modulo). Funciona igual pra
     * QUALQUER um dos ~18 dominios, sem excecao por modulo. */
    suspend fun mostRecent(domainId: String): Map<String, String?>? =
        db.recordDao().mostRecent(domainId)?.let { jsonStringToMap(it.fieldsJson) }

    /** Busca os registros do modulo no servidor e substitui o cache local
     * (preservando os que ainda estao pendentes de sincronizar). Chamar ao
     * abrir cada modulo, se houver internet. */
    suspend fun refreshFromServer(domainId: String): Boolean {
        val tokens = tokenStore.current() ?: return false
        var accessToken = tokens.first
        return try {
            var response = NetworkModule.mobileApi.records(RecordsRequest(accessToken, tokens.second, domainId))
            // Ver comentario equivalente em DashboardRepository (Task #37).
            if (response.code() == 401) {
                val newAccess = TokenRefresher.refreshAccessToken(tokenStore, tokens.second)
                if (newAccess != null) {
                    accessToken = newAccess
                    response = NetworkModule.mobileApi.records(RecordsRequest(accessToken, tokens.second, domainId))
                }
            }
            val body = response.body()
            if (!response.isSuccessful || body?.ok != true) return false

            val orgId = currentOrgId()
            db.recordDao().clearSyncedForDomain(domainId)
            db.recordDao().upsertAll(
                body.records.map { obj ->
                    RecordEntity(
                        domainId = domainId,
                        id = (obj["id"] as? JsonPrimitive)?.content ?: UUID.randomUUID().toString(),
                        fieldsJson = obj.toString(),
                        criadoEmMillis = System.currentTimeMillis(),
                        orgId = orgId,
                    )
                }
            )
            true
        } catch (e: Exception) {
            AppLog.e("RecordRepository", "Falha ao atualizar cache local de registros do módulo domainId=$domainId a partir do servidor", e)
            false
        }
    }

    /** Salva um novo lancamento -- grava local IMEDIATAMENTE (o usuario nunca
     * espera rede pra ver o proprio lancamento na lista) e entra na fila de
     * sincronizacao. Se houver conexao, tenta sincronizar na hora (e some da
     * fila); se nao, fica pendente ate o SyncWorker conseguir. */
    suspend fun createRecord(domainId: String, fields: Map<String, String>): SaveResult {
        val localId = "local-${UUID.randomUUID()}"
        val orgId = currentOrgId()
        db.recordDao().upsert(
            RecordEntity(domainId = domainId, id = localId, fieldsJson = mapToJson(fields), criadoEmMillis = System.currentTimeMillis(), pendingCreate = true, orgId = orgId)
        )
        val pendingId = db.pendingSyncDao().insert(
            PendingSyncEntity(domainId = domainId, kind = "create", localRecordId = localId, serverRecordId = null, fieldsJson = mapToJson(fields), criadoEmMillis = System.currentTimeMillis(), orgId = orgId)
        )
        return trySyncOne(pendingId) ?: SaveResult.SavedOffline
    }

    suspend fun updateRecord(domainId: String, recordId: String, fields: Map<String, String>): SaveResult {
        val orgId = currentOrgId()
        // Task #124 (deteccao de conflito) -- preserva o "expectedVersion" ja
        // conhecido deste registro (gravado por updateExpectedVersion() na
        // ultima vez que a tela buscou "Editado por") ANTES de sobrescrever a
        // linha com os campos novos; e esse valor que vai no
        // SyncRequest.expectedVersion mais abaixo (via PendingSyncEntity),
        // pro backend saber se algum outro aparelho editou o registro
        // depois da ultima vez que este aparelho conferiu.
        val expectedVersion = db.recordDao().byId(domainId, recordId)?.expectedVersion
        db.recordDao().upsert(
            RecordEntity(domainId = domainId, id = recordId, fieldsJson = mapToJson(fields), criadoEmMillis = System.currentTimeMillis(), orgId = orgId, expectedVersion = expectedVersion)
        )
        val pendingId = db.pendingSyncDao().insert(
            PendingSyncEntity(domainId = domainId, kind = "update", localRecordId = recordId, serverRecordId = recordId, fieldsJson = mapToJson(fields), criadoEmMillis = System.currentTimeMillis(), orgId = orgId, expectedVersion = expectedVersion)
        )
        return trySyncOne(pendingId) ?: SaveResult.SavedOffline
    }

    /** Ícone Excluir por lançamento (pedido do usuário -- "crie
     * individualmente em cada bloco ícone ver, editar e excluir"). Some da
     * tela IMEDIATAMENTE (mesmo princípio de createRecord/updateRecord) e
     * entra na fila de sincronização; trySyncOne já sabe lidar com
     * kind="delete" (rota /api/offline-sync no site, ver route.ts) sem
     * nenhuma mudança extra nela. Sem "fields" -- não há nenhum campo pra
     * excluir, só o id. */
    suspend fun deleteRecord(domainId: String, recordId: String): SaveResult {
        db.recordDao().deleteById(domainId, recordId)
        val pendingId = db.pendingSyncDao().insert(
            PendingSyncEntity(domainId = domainId, kind = "delete", localRecordId = recordId, serverRecordId = recordId, fieldsJson = mapToJson(emptyMap()), criadoEmMillis = System.currentTimeMillis(), orgId = currentOrgId())
        )
        return trySyncOne(pendingId) ?: SaveResult.SavedOffline
    }

    /** Processa TODA a fila pendente, em ordem -- chamado pelo SyncWorker
     * (WorkManager) quando a conexao volta, e tambem manualmente (botao
     * "Sincronizar agora"). */
    suspend fun syncAll(): Int {
        var sincronizados = 0
        // Task #124 (conflito de sync) -- itens ja marcados com
        // conflictMessage NAO entram aqui: ja sabemos que vao bater 409 de
        // novo (nada mudou do lado do servidor so porque o tempo passou),
        // entao tentar de novo automaticamente so bateria rede sem motivo
        // ate o usuario abrir o lancamento e decidir o que fazer. Continuam
        // visiveis na fila (observeAll/observePending) pro banner do
        // Início avisar sobre eles.
        val pendentes = db.pendingSyncDao().allOnce().filter { it.conflictMessage == null }
        for (item in pendentes) {
            val result = trySyncOne(item.id)
            if (result is SaveResult.SavedOnline) sincronizados++
        }
        return sincronizados
    }

    /** true se ainda sobrou algo na fila depois de um syncAll() -- usado
     * pelo SyncWorker pra saber se deve pedir retry ao WorkManager (antes
     * ele sempre retornava sucesso, mesmo com pendencia sobrando, entao
     * nunca reagendava uma nova tentativa por conta propria).
     *
     * Task #124 -- itens em conflito (conflictMessage != null) NAO contam
     * aqui de proposito: eles so se resolvem com uma acao do usuario (abrir
     * o lancamento, editar de novo ou descartar), nunca so tentando de novo
     * -- se contassem, o WorkManager ficaria reagendando retry pra sempre
     * (com backoff crescente) por causa de um item que nenhum retry
     * automatico resolve. */
    suspend fun hasPending(): Boolean = db.pendingSyncDao().allOnce().any { it.conflictMessage == null }

    /** Tenta sincronizar UM item da fila. Retorna null se nao ha conexao/
     * token (quem chamou entao assume que ficou pendente mesmo, sem erro). */
    private suspend fun trySyncOne(pendingId: Long): SaveResult? {
        val pending = db.pendingSyncDao().allOnce().find { it.id == pendingId } ?: return null
        val tokens = tokenStore.current() ?: return null

        return try {
            val fieldsMap = jsonStringToMap(pending.fieldsJson).mapValues { it.value ?: "" }
            var accessToken = tokens.first
            var response = NetworkModule.mobileApi.sync(
                SyncRequest(accessToken, tokens.second, pending.domainId, pending.kind, pending.serverRecordId, fieldsMap, pending.expectedVersion)
            )

            // Access token de vida curta (~1h) pode ja ter expirado se o
            // aparelho ficou muito tempo sem tentar sincronizar -- so tenta
            // renovar (via refresh_token, que dura bem mais) UMA vez antes
            // de desistir; o /api/offline-sync tambem religa a sessao pelo
            // refresh_token sozinho, entao isso e so uma segunda tentativa
            // de garantir o access_token novo pra proxima chamada. Ver
            // TokenRefresher (Fase 2, Task #37) -- mesma logica reaproveitada
            // pelas telas de leitura (Dashboard/DRE/Analises).
            if (response.code() == 401) {
                val newAccess = TokenRefresher.refreshAccessToken(tokenStore, tokens.second)
                if (newAccess != null) {
                    accessToken = newAccess
                    response = NetworkModule.mobileApi.sync(
                        SyncRequest(accessToken, tokens.second, pending.domainId, pending.kind, pending.serverRecordId, fieldsMap, pending.expectedVersion)
                    )
                }
            }

            val body = response.body()
            if (!response.isSuccessful || body?.ok != true) {
                // Task #124 (deteccao de conflito) -- 409 com code="CONFLICT"
                // e um caso ESPECIAL: nao e um erro generico (rede/validacao)
                // que vale a pena tentar de novo sozinho, e um aviso de que
                // outro aparelho editou o MESMO registro depois da ultima
                // vez que este aparelho conferiu (ver expectedVersion acima).
                // Como o corpo da resposta so chega em response.body() quando
                // o HTTP e 2xx, aqui (response nao-2xx) o corpo real esta em
                // errorBody() -- parseSyncErrorBody() le e interpreta isso.
                val (code, message) = if (!response.isSuccessful) {
                    parseSyncErrorBody(response)
                } else {
                    body?.code to body?.error
                }
                if (code == "CONFLICT") {
                    val conflictMsg = message
                        ?: "Este lançamento foi alterado por outro dispositivo desde a última sincronização."
                    db.pendingSyncDao().marcarConflito(pending.id, conflictMsg)
                    return SaveResult.Failure(conflictMsg)
                }
                val erro = message ?: body?.error ?: "Erro ao sincronizar (codigo ${response.code()})."
                db.pendingSyncDao().marcarErro(pending.id, erro)
                return SaveResult.Failure(erro)
            }

            db.pendingSyncDao().delete(pending.id)
            // O servidor calcula campos derivados (rateio, vencimento,
            // numeracao de O.S. etc.) que este app NAO reproduz localmente
            // de proposito (ver README) -- por isso o registro local
            // temporario e removido apos sincronizar, e refreshFromServer()
            // (chamado logo em seguida pela tela) busca a versao definitiva.
            if (pending.kind == "create") {
                db.recordDao().upsert(
                    RecordEntity(domainId = pending.domainId, id = pending.localRecordId, fieldsJson = pending.fieldsJson, criadoEmMillis = System.currentTimeMillis(), pendingCreate = false, orgId = pending.orgId)
                )
            }
            refreshFromServer(pending.domainId)
            SaveResult.SavedOnline
        } catch (e: Exception) {
            // Loga sempre (mesmo sendo o caso normal de "sem conexão") --
            // sem isso, e impossivel distinguir depois um timeout/sem-rede
            // real de um erro de verdade (ex.: parsing, bug) que
            // silenciosamente deixa um item pendente pra sempre na fila.
            AppLog.e("RecordRepository", "Falha ao sincronizar item pendente da fila (pendingId=$pendingId, kind=${pending.kind}, domainId=${pending.domainId})", e)
            null // sem conexao -- fica pendente, sem marcar como erro real
        }
    }

    /** Le e interpreta o corpo de erro de uma resposta HTTP nao-2xx do
     * /api/offline-sync (Task #124, deteccao de conflito) -- Retrofit so
     * converte `response.body()` pro tipo esperado (SyncResponse) quando o
     * HTTP e 2xx; quando nao e (ex.: 409 CONFLICT), o corpo bruto fica em
     * `response.errorBody()`, sem conversao automatica. Le uma unica vez
     * (errorBody() so pode ser consumido uma vez) e tenta extrair "code"/
     * "message" do JSON -- devolve (null, null) se o corpo estiver vazio,
     * nao for JSON valido, ou nao tiver esses campos (fail-open: quem
     * chamou trata como erro generico nesse caso, igual a antes desta
     * task). */
    private fun parseSyncErrorBody(response: Response<SyncResponse>): Pair<String?, String?> {
        return try {
            val raw = response.errorBody()?.string() ?: return null to null
            val obj = kotlinx.serialization.json.Json.parseToJsonElement(raw) as? JsonObject ?: return null to null
            val code = (obj["code"] as? JsonPrimitive)?.content
            val message = (obj["message"] as? JsonPrimitive)?.content
            code to message
        } catch (e: Exception) {
            AppLog.e("RecordRepository", "Falha ao interpretar corpo de erro da resposta de /api/offline-sync", e)
            null to null
        }
    }

    private fun mapToJson(fields: Map<String, String>): String =
        buildJsonObject { fields.forEach { (k, v) -> put(k, JsonPrimitive(v)) } }.toString()

    private fun jsonStringToMap(jsonString: String): Map<String, String?> {
        return try {
            val obj = kotlinx.serialization.json.Json.parseToJsonElement(jsonString) as? JsonObject ?: return emptyMap()
            obj.mapValues { (_, v) ->
                when (v) {
                    is kotlinx.serialization.json.JsonNull -> null
                    is JsonPrimitive -> v.content
                    else -> v.toString() // objeto/array aninhado (nao esperado nos campos de um registro) -- guarda o JSON bruto em vez de quebrar
                }
            }
        } catch (e: Exception) {
            AppLog.e("RecordRepository", "Falha ao decodificar JSON de campos de um registro (fieldsJson corrompido?)", e)
            emptyMap()
        }
    }
}
