package com.bragro.mobile.data.repo

import android.content.Context
import com.bragro.mobile.data.AppLog
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
 * offline de propósito: é um lançamento com efeito colateral em dois outros
 * módulos (Estoque/Financeiro), não um dado de leitura -- precisa de
 * conexão, igual ao Importar XML (NfeImportRepository). */
class NotaMultiItemRepository(context: Context) {
    private val tokenStore = TokenStore(context)

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
            NotaMultiItemResponse(ok = false, error = "Sem conexão. Tente novamente.")
        }
    }
}
