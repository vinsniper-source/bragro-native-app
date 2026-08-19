package com.bragro.mobile.data.repo

import android.content.Context
import com.bragro.mobile.data.AppLog
import com.bragro.mobile.data.TokenStore
import com.bragro.mobile.data.model.CotacaoMultiItemItemData
import com.bragro.mobile.data.model.CotacaoMultiItemRequest
import com.bragro.mobile.data.model.CotacaoMultiItemResponse
import com.bragro.mobile.data.remote.NetworkModule

/** Cotações de Fornecedores: "novo modelo" de vários itens do MESMO
 * fornecedor numa única submissão -- pedido do usuário ("insira o novo
 * modelo dos modulos cotaçoes e pedidos no app native"), réplica de
 * cotacao-multi-item-button.tsx no site (task #234). Chama
 * /api/mobile/cotacao-multi-item, que chama DIRETO
 * createCotacaoMultiItemAction() no servidor -- mesmo motor que o site usa
 * (cada item vira sua própria linha de Cotação, Índice de Vantagem/Avaliação
 * recalculados automaticamente contra o grupo Categoria+Item). Sem cache
 * offline de propósito, mesmo critério do NotaMultiItemRepository. */
class CotacaoMultiItemRepository(context: Context) {
    private val tokenStore = TokenStore(context)

    suspend fun criar(
        data: String,
        fornecedor: String,
        condicaoPagamento: String?,
        validadeProposta: String?,
        observacoes: String?,
        itens: List<CotacaoMultiItemItemData>,
    ): CotacaoMultiItemResponse? {
        val tokens = tokenStore.current() ?: return null
        var (accessToken, refreshToken) = tokens
        return try {
            fun buildRequest() = CotacaoMultiItemRequest(
                accessToken = accessToken,
                refreshToken = refreshToken,
                data = data,
                fornecedor = fornecedor,
                condicaoPagamento = condicaoPagamento,
                validadeProposta = validadeProposta,
                observacoes = observacoes,
                itens = itens,
            )
            var response = NetworkModule.mobileApi.cotacaoMultiItem(buildRequest())
            if (response.code() == 401) {
                val newAccess = TokenRefresher.refreshAccessToken(tokenStore, refreshToken)
                if (newAccess != null) {
                    accessToken = newAccess
                    response = NetworkModule.mobileApi.cotacaoMultiItem(buildRequest())
                }
            }
            val body = response.body()
            if (!response.isSuccessful || body == null) {
                return body ?: CotacaoMultiItemResponse(ok = false, error = "Falha ao lançar a cotação.")
            }
            body
        } catch (e: Exception) {
            AppLog.e("CotacaoMultiItemRepository", "Falha ao lançar cotação com itens", e)
            CotacaoMultiItemResponse(ok = false, error = "Sem conexão. Tente novamente.")
        }
    }
}
