package com.bragro.mobile.data.repo

import android.content.Context
import com.bragro.mobile.data.AppLog
import com.bragro.mobile.data.TokenStore
import com.bragro.mobile.data.model.NfeCreateRequest
import com.bragro.mobile.data.model.NfeCreateResponse
import com.bragro.mobile.data.model.NfeDeleteRequest
import com.bragro.mobile.data.model.NfeDeleteResponse
import com.bragro.mobile.data.model.NfeEmitirRequest
import com.bragro.mobile.data.model.NfeEmitirResponse
import com.bragro.mobile.data.model.NfeListRequest
import com.bragro.mobile.data.model.NfeListResponse
import com.bragro.mobile.data.remote.NetworkModule

/** Módulo NF-e (Task #628, ausente por completo no app até aqui). Chama
 * /api/mobile/nfe (dispatcher por "action"), que chama DIRETO
 * listInvoices()/createManualInvoiceAction()/deleteInvoiceAction()/
 * emitirNotaFiscalAction() no servidor -- mesmo motor que o site usa,
 * nenhuma lógica de negócio duplicada aqui. Sem cache offline de propósito
 * (mesmo critério de OrcamentoRepository): lista de notas fiscais + emissão
 * junto à SEFAZ são sempre dado ao vivo, não uma tela offline-first. */
class NfeRepository(context: Context) {
    private val tokenStore = TokenStore(context)

    suspend fun listar(): NfeListResponse? {
        val tokens = tokenStore.current() ?: return null
        var (accessToken, refreshToken) = tokens
        return try {
            fun buildRequest() = NfeListRequest(accessToken = accessToken, refreshToken = refreshToken)
            var response = NetworkModule.mobileApi.nfeList(buildRequest())
            if (response.code() == 401) {
                val newAccess = TokenRefresher.refreshAccessToken(tokenStore, refreshToken)
                if (newAccess != null) {
                    accessToken = newAccess
                    response = NetworkModule.mobileApi.nfeList(buildRequest())
                }
            }
            response.body() ?: NfeListResponse(ok = false, error = "Falha ao listar notas fiscais.")
        } catch (e: Exception) {
            AppLog.e("NfeRepository", "Falha ao listar notas fiscais", e)
            NfeListResponse(ok = false, error = "Sem conexão. Tente novamente.")
        }
    }

    suspend fun criar(
        numero: String,
        serie: String?,
        tipo: String,
        emitenteNome: String,
        valorTotal: Double,
        dataEmissao: String?,
    ): NfeCreateResponse? {
        val tokens = tokenStore.current() ?: return null
        var (accessToken, refreshToken) = tokens
        return try {
            fun buildRequest() = NfeCreateRequest(
                accessToken = accessToken,
                refreshToken = refreshToken,
                numero = numero,
                serie = serie,
                tipo = tipo,
                emitenteNome = emitenteNome,
                valorTotal = valorTotal,
                dataEmissao = dataEmissao,
            )
            var response = NetworkModule.mobileApi.nfeCreate(buildRequest())
            if (response.code() == 401) {
                val newAccess = TokenRefresher.refreshAccessToken(tokenStore, refreshToken)
                if (newAccess != null) {
                    accessToken = newAccess
                    response = NetworkModule.mobileApi.nfeCreate(buildRequest())
                }
            }
            response.body() ?: NfeCreateResponse(ok = false, error = "Falha ao lançar a nota fiscal.")
        } catch (e: Exception) {
            AppLog.e("NfeRepository", "Falha ao lançar nota fiscal", e)
            NfeCreateResponse(ok = false, error = "Sem conexão. Tente novamente.")
        }
    }

    suspend fun excluir(invoiceId: String): NfeDeleteResponse? {
        val tokens = tokenStore.current() ?: return null
        var (accessToken, refreshToken) = tokens
        return try {
            fun buildRequest() = NfeDeleteRequest(accessToken = accessToken, refreshToken = refreshToken, invoiceId = invoiceId)
            var response = NetworkModule.mobileApi.nfeDelete(buildRequest())
            if (response.code() == 401) {
                val newAccess = TokenRefresher.refreshAccessToken(tokenStore, refreshToken)
                if (newAccess != null) {
                    accessToken = newAccess
                    response = NetworkModule.mobileApi.nfeDelete(buildRequest())
                }
            }
            response.body() ?: NfeDeleteResponse(ok = false, error = "Falha ao excluir a nota fiscal.")
        } catch (e: Exception) {
            AppLog.e("NfeRepository", "Falha ao excluir nota fiscal", e)
            NfeDeleteResponse(ok = false, error = "Sem conexão. Tente novamente.")
        }
    }

    suspend fun emitir(invoiceId: String): NfeEmitirResponse? {
        val tokens = tokenStore.current() ?: return null
        var (accessToken, refreshToken) = tokens
        return try {
            fun buildRequest() = NfeEmitirRequest(accessToken = accessToken, refreshToken = refreshToken, invoiceId = invoiceId)
            var response = NetworkModule.mobileApi.nfeEmitir(buildRequest())
            if (response.code() == 401) {
                val newAccess = TokenRefresher.refreshAccessToken(tokenStore, refreshToken)
                if (newAccess != null) {
                    accessToken = newAccess
                    response = NetworkModule.mobileApi.nfeEmitir(buildRequest())
                }
            }
            response.body() ?: NfeEmitirResponse(ok = false, error = "Falha ao emitir a nota fiscal.")
        } catch (e: Exception) {
            AppLog.e("NfeRepository", "Falha ao emitir nota fiscal", e)
            NfeEmitirResponse(ok = false, error = "Sem conexão. Tente novamente.")
        }
    }
}
