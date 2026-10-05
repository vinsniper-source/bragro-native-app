package com.bragro.mobile.data.repo

import android.content.Context
import com.bragro.mobile.data.AppLog
import com.bragro.mobile.data.NetworkStatus
import com.bragro.mobile.data.TokenStore
import com.bragro.mobile.data.model.NotaMultiItemItemData
import com.bragro.mobile.data.model.NotaMultiItemRequest
import com.bragro.mobile.data.model.NotaMultiItemResponse
import com.bragro.mobile.data.remote.NetworkModule

/** Financeiro: "Lançar nota com itens" (multi-item) -- gap encontrado na
 * auditoria módulo-a-módulo contra o site (pedido do usuário "implemente
 * tudo que falta ainda para o app native da plataforma"). Chama
 * /api/mobile/nota-multi-item, que por sua vez chama DIRETO
 * criarNotaComItensAction() -- mesmo motor que o site usa (1 Invoice + N
 * InvoiceItem + N EstoqueMovimento + parcelas em Financeiro). Sem cache
 * offline: ao contrário de antes, agora FUNCIONA sem internet -- o pedido
 * entra na fila (MultiItemOutbox) e é lançado pelo servidor (com todos os
 * efeitos em Estoque/Financeiro, uma única vez) quando a conexão volta. */
class NotaMultiItemRepository(context: Context) {
    private val appContext = context.applicationContext
    private val tokenStore = TokenStore(appContext)
    private val outbox = MultiItemOutbox(appContext)

    suspend fun criar(
        numero: String,
        serie: String?,
        emitenteNome: String,
        dataEmissao: String?,
        fazendaDestino: String,
        periodo: String?,
        safra: String?,
        cultura: String?,
        setor: String?,
        banco: String?,
        formaPgto: String?,
        bruto: Double,
        itens: List<NotaMultiItemItemData>,
    ): NotaMultiItemResponse? {
        val tokens = tokenStore.current() ?: return null
        var (accessToken, refreshToken) = tokens
        // Offline (pedido do usuario): fica na fila do aparelho e o servidor
        // lanca (Estoque + Financeiro) quando a internet voltar.
        val reqId = java.util.UUID.randomUUID().toString()
        suspend fun queue(): NotaMultiItemResponse {
            outbox.enqueueNota(
                NotaMultiItemRequest(
                    accessToken = "", refreshToken = "", numero = numero, serie = serie,
                    emitenteNome = emitenteNome, dataEmissao = dataEmissao, fazendaDestino = fazendaDestino,
                    periodo = periodo, safra = safra, cultura = cultura, setor = setor, banco = banco,
                    formaPgto = formaPgto, bruto = bruto, itens = itens, clientRequestId = reqId,
                )
            )
            return NotaMultiItemResponse(ok = true, itensCount = itens.size, valorTotal = bruto, savedOffline = true)
        }
        if (!NetworkStatus.isOnline(appContext)) return queue()
        return try {
            fun buildRequest() = NotaMultiItemRequest(
                accessToken = accessToken,
                refreshToken = refreshToken,
                numero = numero,
                serie = serie,
                emitenteNome = emitenteNome,
                dataEmissao = dataEmissao,
                fazendaDestino = fazendaDestino,
                periodo = periodo,
                safra = safra,
                cultura = cultura,
                setor = setor,
                banco = banco,
                formaPgto = formaPgto,
                bruto = bruto,
                itens = itens,
                clientRequestId = reqId,
            )
            var response = NetworkModule.mobileApi.notaMultiItem(buildRequest())
            if (response.code() == 401) {
                val newAccess = TokenRefresher.refreshAccessToken(tokenStore, refreshToken)
                if (newAccess != null) {
                    accessToken = newAccess
                    response = NetworkModule.mobileApi.notaMultiItem(buildRequest())
                }
            }
            val body = response.body()
            if (!response.isSuccessful || body == null) {
                return body ?: NotaMultiItemResponse(ok = false, error = "Falha ao lançar a nota.")
            }
            body
        } catch (e: Exception) {
            AppLog.e("NotaMultiItemRepository", "Falha ao lançar nota com itens", e)
            if (MultiItemOutbox.seguroEnfileirar(e)) queue()
            else NotaMultiItemResponse(ok = false, error = "Erro ao lançar a nota. Tente novamente.")
        }
    }
}
