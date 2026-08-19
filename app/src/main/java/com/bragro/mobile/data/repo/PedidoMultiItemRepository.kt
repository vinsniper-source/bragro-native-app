package com.bragro.mobile.data.repo

import android.content.Context
import com.bragro.mobile.data.AppLog
import com.bragro.mobile.data.TokenStore
import com.bragro.mobile.data.model.PedidoMultiItemItemData
import com.bragro.mobile.data.model.PedidoMultiItemRequest
import com.bragro.mobile.data.model.PedidoMultiItemResponse
import com.bragro.mobile.data.remote.NetworkModule

/** Pedidos: "novo modelo" de vários itens no mesmo lançamento -- pedido do
 * usuário ("insira o novo modelo dos modulos cotaçoes e pedidos no app
 * native"), réplica de pedido-multi-item-button.tsx no site (task #235).
 * Chama /api/mobile/pedido-multi-item, que chama DIRETO
 * createPedidoMultiItemAction() no servidor -- mesmo motor que o site usa
 * (N linhas de Pedido, Saldo/%/Status cumulativos por Nº Pedido+Item, baixa
 * automática em Estoque quando "Qtd Entregue" é preenchida). Sem cache
 * offline de propósito, mesmo critério do NotaMultiItemRepository: é um
 * lançamento com efeito colateral em Estoque, não um dado de leitura. */
class PedidoMultiItemRepository(context: Context) {
    private val tokenStore = TokenStore(context)

    suspend fun criar(
        noPedido: String,
        setor: String?,
        fornecedor: String?,
        safra: String?,
        cultura: String?,
        dataEntrega: String?,
        nf: String?,
        itens: List<PedidoMultiItemItemData>,
    ): PedidoMultiItemResponse? {
        val tokens = tokenStore.current() ?: return null
        var (accessToken, refreshToken) = tokens
        return try {
            fun buildRequest() = PedidoMultiItemRequest(
                accessToken = accessToken,
                refreshToken = refreshToken,
                noPedido = noPedido,
                setor = setor,
                fornecedor = fornecedor,
                safra = safra,
                cultura = cultura,
                dataEntrega = dataEntrega,
                nf = nf,
                itens = itens,
            )
            var response = NetworkModule.mobileApi.pedidoMultiItem(buildRequest())
            if (response.code() == 401) {
                val newAccess = TokenRefresher.refreshAccessToken(tokenStore, refreshToken)
                if (newAccess != null) {
                    accessToken = newAccess
                    response = NetworkModule.mobileApi.pedidoMultiItem(buildRequest())
                }
            }
            val body = response.body()
            if (!response.isSuccessful || body == null) {
                return body ?: PedidoMultiItemResponse(ok = false, error = "Falha ao lançar o pedido.")
            }
            body
        } catch (e: Exception) {
            AppLog.e("PedidoMultiItemRepository", "Falha ao lançar pedido com itens", e)
            PedidoMultiItemResponse(ok = false, error = "Sem conexão. Tente novamente.")
        }
    }
}
