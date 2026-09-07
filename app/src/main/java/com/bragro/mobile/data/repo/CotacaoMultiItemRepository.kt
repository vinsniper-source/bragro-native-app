package com.bragro.mobile.data.repo

import android.content.Context
import com.bragro.mobile.data.AppLog
import com.bragro.mobile.data.TokenStore
import com.bragro.mobile.data.model.CotacaoComparacaoPropostaData
import com.bragro.mobile.data.model.CotacaoComparacaoRequest
import com.bragro.mobile.data.model.CotacaoComparacaoResponse
import com.bragro.mobile.data.model.CotacaoMultiItemItemData
import com.bragro.mobile.data.model.CotacaoMultiItemRequest
import com.bragro.mobile.data.model.CotacaoMultiItemResponse
import com.bragro.mobile.data.model.CotacaoPrecoMedioRequest
import com.bragro.mobile.data.model.CotacaoPrecoMedioResponse
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

    /** Inverso de criar() acima -- pedido do usuário ("Cotações Fornecedores:
     * múltiplos fornecedores por operação", task #404): 1 item, N propostas
     * de fornecedores diferentes na mesma submissão. Chama
     * /api/mobile/cotacao-comparacao, que chama DIRETO
     * createCotacaoComparacaoAction() no servidor. */
    suspend fun criarComparacao(
        data: String,
        categoria: String,
        item: String,
        quantidade: Double?,
        unidade: String?,
        observacoes: String?,
        propostas: List<CotacaoComparacaoPropostaData>,
    ): CotacaoComparacaoResponse? {
        val tokens = tokenStore.current() ?: return null
        var (accessToken, refreshToken) = tokens
        return try {
            fun buildRequest() = CotacaoComparacaoRequest(
                accessToken = accessToken,
                refreshToken = refreshToken,
                data = data,
                categoria = categoria,
                item = item,
                quantidade = quantidade,
                unidade = unidade,
                observacoes = observacoes,
                propostas = propostas,
            )
            var response = NetworkModule.mobileApi.cotacaoComparacao(buildRequest())
            if (response.code() == 401) {
                val newAccess = TokenRefresher.refreshAccessToken(tokenStore, refreshToken)
                if (newAccess != null) {
                    accessToken = newAccess
                    response = NetworkModule.mobileApi.cotacaoComparacao(buildRequest())
                }
            }
            val body = response.body()
            if (!response.isSuccessful || body == null) {
                return body ?: CotacaoComparacaoResponse(ok = false, error = "Falha ao lançar as propostas.")
            }
            body
        } catch (e: Exception) {
            AppLog.e("CotacaoMultiItemRepository", "Falha ao lançar propostas de comparação", e)
            CotacaoComparacaoResponse(ok = false, error = "Sem conexão. Tente novamente.")
        }
    }

    /** Preço médio histórico de um item (task #472) -- ver comentário
     * completo em Models.kt (CotacaoPrecoMedioRequest/Response) e no site
     * (cotacao-multi-item-button.tsx). Retorna null em caso de falha de rede
     * (o chamador trata isso como "sem histórico disponível agora", não
     * como erro bloqueante -- o formulário continua funcionando sem essa
     * informação). */
    suspend fun precoMedioHistorico(categoria: String, item: String): CotacaoPrecoMedioResponse? {
        val tokens = tokenStore.current() ?: return null
        var (accessToken, refreshToken) = tokens
        return try {
            fun buildRequest() = CotacaoPrecoMedioRequest(
                accessToken = accessToken,
                refreshToken = refreshToken,
                categoria = categoria,
                item = item,
            )
            var response = NetworkModule.mobileApi.cotacaoPrecoMedio(buildRequest())
            if (response.code() == 401) {
                val newAccess = TokenRefresher.refreshAccessToken(tokenStore, refreshToken)
                if (newAccess != null) {
                    accessToken = newAccess
                    response = NetworkModule.mobileApi.cotacaoPrecoMedio(buildRequest())
                }
            }
            response.body()
        } catch (e: Exception) {
            AppLog.e("CotacaoMultiItemRepository", "Falha ao buscar preco medio historico", e)
            null
        }
    }
}
