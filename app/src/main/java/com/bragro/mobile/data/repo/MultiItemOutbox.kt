package com.bragro.mobile.data.repo

import android.content.Context
import com.bragro.mobile.data.AppLog
import com.bragro.mobile.data.TokenStore
import com.bragro.mobile.data.local.AppDatabase
import com.bragro.mobile.data.local.PendingSyncEntity
import com.bragro.mobile.data.model.CotacaoComparacaoRequest
import com.bragro.mobile.data.model.CotacaoMultiItemRequest
import com.bragro.mobile.data.model.NotaMultiItemRequest
import com.bragro.mobile.data.model.PedidoMultiItemRequest
import com.bragro.mobile.data.remote.NetworkModule
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.SocketTimeoutException
import java.util.UUID

/** Fila offline dos lancamentos "com varios itens" (Nota com itens, Pedido e
 * Cotacao multi-item) -- pedido do usuario ("nao funcionam offline no app, e
 * o lancamento mais comum de compra de insumo").
 *
 * Reaproveita a tabela pending_sync (sem migracao de Room): domainId =
 * "__multi__nota|pedido|cotacao", kind = "multi", fieldsJson = o corpo da
 * requisicao SEM tokens (tokens frescos entram so na hora de enviar). O envio
 * usa os MESMOS endpoints multi-item de antes (nota, pedido e cotacao), entao toda a
 * regra de negocio (baixa/entrada em Estoque, parcelas no Financeiro, Saldo,
 * Indice de Vantagem) continua rodando no servidor, uma unica vez, quando a
 * conexao volta. Quem drena a fila e o RecordRepository.syncAll (SyncWorker).
 *
 * Protecao contra duplicidade: cada lancamento carrega um clientRequestId
 * (UUID) igual em todas as tentativas; o servidor guarda o resultado dessa
 * chave (lib/idempotency.ts) e, se a resposta anterior se perdeu depois de
 * processar, devolve o resultado guardado em vez de lancar de novo. */
class MultiItemOutbox(context: Context) {
    private val appContext = context.applicationContext
    private val db = AppDatabase.get(appContext)
    private val tokenStore = TokenStore(appContext)

    sealed class Replay {
        data object Done : Replay()
        /** Sem rede / token / erro 5xx -- tenta de novo depois. */
        data object Retry : Replay()
        /** Servidor recusou (validacao etc.) -- retry automatico nao resolve. */
        data class Rejected(val message: String) : Replay()
    }

    suspend fun enqueue(kind: String, bodyJson: String) {
        db.pendingSyncDao().insert(
            PendingSyncEntity(
                domainId = PREFIX + kind,
                kind = "multi",
                localRecordId = "multi-${UUID.randomUUID()}",
                serverRecordId = null,
                fieldsJson = bodyJson,
                criadoEmMillis = System.currentTimeMillis(),
                orgId = db.sessionDao().get()?.orgId,
            )
        )
    }

    suspend fun enqueueNota(r: NotaMultiItemRequest) = enqueue(NOTA, json.encodeToString(r.copy(accessToken = "", refreshToken = "")))
    suspend fun enqueuePedido(r: PedidoMultiItemRequest) = enqueue(PEDIDO, json.encodeToString(r.copy(accessToken = "", refreshToken = "")))
    suspend fun enqueueCotacao(r: CotacaoMultiItemRequest) = enqueue(COTACAO, json.encodeToString(r.copy(accessToken = "", refreshToken = "")))
    suspend fun enqueueComparacao(r: CotacaoComparacaoRequest) = enqueue(COMPARACAO, json.encodeToString(r.copy(accessToken = "", refreshToken = "")))

    /** Envia UM item da fila. Nao mexe na tabela -- quem chama apaga/marca. */
    suspend fun replay(pending: PendingSyncEntity): Replay {
        val tokens = tokenStore.current() ?: return Replay.Retry
        return try {
            var access = tokens.first
            val refresh = tokens.second
            suspend fun <T> call(send: suspend (String) -> retrofit2.Response<T>): retrofit2.Response<T> {
                var resp = send(access)
                if (resp.code() == 401) {
                    val novo = TokenRefresher.refreshAccessToken(tokenStore, refresh)
                    if (novo != null) {
                        access = novo
                        resp = send(access)
                    }
                }
                return resp
            }
            val api = NetworkModule.mobileApi
            val (ok, erro, httpCode) = when (pending.domainId.removePrefix(PREFIX)) {
                NOTA -> {
                    val base = json.decodeFromString<NotaMultiItemRequest>(pending.fieldsJson)
                    val r = call { api.notaMultiItem(base.copy(accessToken = it, refreshToken = refresh)) }
                    Triple(r.body()?.ok == true, r.body()?.error, r.code())
                }
                PEDIDO -> {
                    val base = json.decodeFromString<PedidoMultiItemRequest>(pending.fieldsJson)
                    val r = call { api.pedidoMultiItem(base.copy(accessToken = it, refreshToken = refresh)) }
                    Triple(r.body()?.ok == true, r.body()?.error, r.code())
                }
                COTACAO -> {
                    val base = json.decodeFromString<CotacaoMultiItemRequest>(pending.fieldsJson)
                    val r = call { api.cotacaoMultiItem(base.copy(accessToken = it, refreshToken = refresh)) }
                    Triple(r.body()?.ok == true, r.body()?.error, r.code())
                }
                COMPARACAO -> {
                    val base = json.decodeFromString<CotacaoComparacaoRequest>(pending.fieldsJson)
                    val r = call { api.cotacaoComparacao(base.copy(accessToken = it, refreshToken = refresh)) }
                    Triple(r.body()?.ok == true, r.body()?.error, r.code())
                }
                else -> return Replay.Rejected("Tipo de lançamento desconhecido na fila.")
            }
            when {
                ok -> Replay.Done
                httpCode >= 500 || httpCode == 401 || httpCode == 429 -> Replay.Retry
                else -> Replay.Rejected(erro ?: "O servidor recusou o lançamento (código $httpCode).")
            }
        } catch (e: Exception) {
            AppLog.e("MultiItemOutbox", "Falha ao enviar lançamento multi-item da fila (${pending.domainId})", e)
            Replay.Retry
        }
    }

    companion object {
        const val PREFIX = "__multi__"
        const val NOTA = "nota"
        const val PEDIDO = "pedido"
        const val COTACAO = "cotacao"
        const val COMPARACAO = "comparacao"
        private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

        fun isMulti(domainId: String) = domainId.startsWith(PREFIX)

        /** Qualquer falha de rede (inclusive timeout) pode ir pra fila: o
         * reenvio leva o MESMO clientRequestId, e o servidor (lib/
         * idempotency.ts) devolve o resultado ja guardado se a tentativa
         * anterior chegou a ser processada -- sem duplicar. */
        fun seguroEnfileirar(e: Exception) = e is java.io.IOException
    }
}
