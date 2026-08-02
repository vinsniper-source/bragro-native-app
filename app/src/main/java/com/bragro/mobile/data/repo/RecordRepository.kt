package com.bragro.mobile.data.repo

import android.content.Context
import com.bragro.mobile.data.TokenStore
import com.bragro.mobile.data.local.AppDatabase
import com.bragro.mobile.data.local.PendingSyncEntity
import com.bragro.mobile.data.local.RecordEntity
import com.bragro.mobile.data.model.RecordsRequest
import com.bragro.mobile.data.model.SyncRequest
import com.bragro.mobile.data.remote.NetworkModule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
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

    suspend fun getRecord(domainId: String, id: String): Map<String, String?>? =
        db.recordDao().byId(domainId, id)?.let { jsonStringToMap(it.fieldsJson) }

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

            db.recordDao().clearSyncedForDomain(domainId)
            db.recordDao().upsertAll(
                body.records.map { obj ->
                    RecordEntity(
                        domainId = domainId,
                        id = (obj["id"] as? JsonPrimitive)?.content ?: UUID.randomUUID().toString(),
                        fieldsJson = obj.toString(),
                        criadoEmMillis = System.currentTimeMillis(),
                    )
                }
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Salva um novo lancamento -- grava local IMEDIATAMENTE (o usuario nunca
     * espera rede pra ver o proprio lancamento na lista) e entra na fila de
     * sincronizacao. Se houver conexao, tenta sincronizar na hora (e some da
     * fila); se nao, fica pendente ate o SyncWorker conseguir. */
    suspend fun createRecord(domainId: String, fields: Map<String, String>): SaveResult {
        val localId = "local-${UUID.randomUUID()}"
        db.recordDao().upsert(
            RecordEntity(domainId = domainId, id = localId, fieldsJson = mapToJson(fields), criadoEmMillis = System.currentTimeMillis(), pendingCreate = true)
        )
        val pendingId = db.pendingSyncDao().insert(
            PendingSyncEntity(domainId = domainId, kind = "create", localRecordId = localId, serverRecordId = null, fieldsJson = mapToJson(fields), criadoEmMillis = System.currentTimeMillis())
        )
        return trySyncOne(pendingId) ?: SaveResult.SavedOffline
    }

    suspend fun updateRecord(domainId: String, recordId: String, fields: Map<String, String>): SaveResult {
        db.recordDao().upsert(RecordEntity(domainId = domainId, id = recordId, fieldsJson = mapToJson(fields), criadoEmMillis = System.currentTimeMillis()))
        val pendingId = db.pendingSyncDao().insert(
            PendingSyncEntity(domainId = domainId, kind = "update", localRecordId = recordId, serverRecordId = recordId, fieldsJson = mapToJson(fields), criadoEmMillis = System.currentTimeMillis())
        )
        return trySyncOne(pendingId) ?: SaveResult.SavedOffline
    }

    /** Processa TODA a fila pendente, em ordem -- chamado pelo SyncWorker
     * (WorkManager) quando a conexao volta, e tambem manualmente (botao
     * "Sincronizar agora"). */
    suspend fun syncAll(): Int {
        var sincronizados = 0
        val pendentes = db.pendingSyncDao().allOnce()
        for (item in pendentes) {
            val result = trySyncOne(item.id)
            if (result is SaveResult.SavedOnline) sincronizados++
        }
        return sincronizados
    }

    /** Tenta sincronizar UM item da fila. Retorna null se nao ha conexao/
     * token (quem chamou entao assume que ficou pendente mesmo, sem erro). */
    private suspend fun trySyncOne(pendingId: Long): SaveResult? {
        val pending = db.pendingSyncDao().allOnce().find { it.id == pendingId } ?: return null
        val tokens = tokenStore.current() ?: return null

        return try {
            val fieldsMap = jsonStringToMap(pending.fieldsJson).mapValues { it.value ?: "" }
            var accessToken = tokens.first
            var response = NetworkModule.mobileApi.sync(
                SyncRequest(accessToken, tokens.second, pending.domainId, pending.kind, pending.serverRecordId, fieldsMap)
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
                        SyncRequest(accessToken, tokens.second, pending.domainId, pending.kind, pending.serverRecordId, fieldsMap)
                    )
                }
            }

            val body = response.body()
            if (!response.isSuccessful || body?.ok != true) {
                db.pendingSyncDao().marcarErro(pending.id, body?.error ?: "Erro ao sincronizar (codigo ${response.code()}).")
                return SaveResult.Failure(body?.error ?: "Erro ao sincronizar.")
            }

            db.pendingSyncDao().delete(pending.id)
            // O servidor calcula campos derivados (rateio, vencimento,
            // numeracao de O.S. etc.) que este app NAO reproduz localmente
            // de proposito (ver README) -- por isso o registro local
            // temporario e removido apos sincronizar, e refreshFromServer()
            // (chamado logo em seguida pela tela) busca a versao definitiva.
            if (pending.kind == "create") {
                db.recordDao().upsert(
                    RecordEntity(domainId = pending.domainId, id = pending.localRecordId, fieldsJson = pending.fieldsJson, criadoEmMillis = System.currentTimeMillis(), pendingCreate = false)
                )
            }
            refreshFromServer(pending.domainId)
            SaveResult.SavedOnline
        } catch (e: Exception) {
            null // sem conexao -- fica pendente, sem marcar como erro real
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
            emptyMap()
        }
    }
}
