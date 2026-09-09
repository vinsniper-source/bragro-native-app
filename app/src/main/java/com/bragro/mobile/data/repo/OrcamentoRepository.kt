package com.bragro.mobile.data.repo

import android.content.Context
import com.bragro.mobile.data.AppLog
import com.bragro.mobile.data.TokenStore
import com.bragro.mobile.data.model.OrcamentoCreateRequest
import com.bragro.mobile.data.model.OrcamentoCreateResponse
import com.bragro.mobile.data.model.OrcamentoItemData
import com.bragro.mobile.data.model.OrcamentoListRequest
import com.bragro.mobile.data.model.OrcamentoListResponse
import com.bragro.mobile.data.model.OrcamentoOcrItensRequest
import com.bragro.mobile.data.model.OrcamentoOcrItensResponse
import com.bragro.mobile.data.model.OrcamentoOcrRequisicaoRequest
import com.bragro.mobile.data.model.OrcamentoOcrRequisicaoResponse
import com.bragro.mobile.data.remote.NetworkModule

/** Módulo de Orçamento (OCR + conciliação) -- ver handoff-ocr-orcamento.md.
 * Chama /api/mobile/orcamento (dispatcher por "action"), que chama DIRETO
 * createOrcamentoAction()/extrairRequisicaoOcrAction()/
 * extrairItensOrcamentoOcrAction() no servidor -- mesmo motor que o site
 * usaria. Sem cache offline de propósito (mesmo critério de
 * PedidoMultiItemRepository/NotaMultiItemRepository): é um lançamento com
 * OCR ao vivo, não um dado de leitura pra tela offline. */
class OrcamentoRepository(context: Context) {
    private val tokenStore = TokenStore(context)

    suspend fun listar(status: String? = null): OrcamentoListResponse? {
        val tokens = tokenStore.current() ?: return null
        var (accessToken, refreshToken) = tokens
        return try {
            fun buildRequest() = OrcamentoListRequest(accessToken = accessToken, refreshToken = refreshToken, status = status)
            var response = NetworkModule.mobileApi.orcamentoList(buildRequest())
            if (response.code() == 401) {
                val newAccess = TokenRefresher.refreshAccessToken(tokenStore, refreshToken)
                if (newAccess != null) {
                    accessToken = newAccess
                    response = NetworkModule.mobileApi.orcamentoList(buildRequest())
                }
            }
            response.body() ?: OrcamentoListResponse(ok = false, error = "Falha ao listar orçamentos.")
        } catch (e: Exception) {
            AppLog.e("OrcamentoRepository", "Falha ao listar orçamentos", e)
            OrcamentoListResponse(ok = false, error = "Sem conexão. Tente novamente.")
        }
    }

    suspend fun criar(
        data: String,
        compradorId: String?,
        fornecedorId: String?,
        numeroOrcamento: String?,
        requisicao: String?,
        autorizadoPorId: String?,
        autorizadoPorNome: String?,
        fotoRequisicaoUrl: String?,
        fotoComprovanteUrl: String?,
        observacoes: String?,
        origemId: String?,
        itens: List<OrcamentoItemData>,
    ): OrcamentoCreateResponse? {
        val tokens = tokenStore.current() ?: return null
        var (accessToken, refreshToken) = tokens
        return try {
            fun buildRequest() = OrcamentoCreateRequest(
                accessToken = accessToken,
                refreshToken = refreshToken,
                data = data,
                compradorId = compradorId,
                fornecedorId = fornecedorId,
                numeroOrcamento = numeroOrcamento,
                requisicao = requisicao,
                autorizadoPorId = autorizadoPorId,
                autorizadoPorNome = autorizadoPorNome,
                fotoRequisicaoUrl = fotoRequisicaoUrl,
                fotoComprovanteUrl = fotoComprovanteUrl,
                observacoes = observacoes,
                origemId = origemId,
                itens = itens,
            )
            var response = NetworkModule.mobileApi.orcamentoCreate(buildRequest())
            if (response.code() == 401) {
                val newAccess = TokenRefresher.refreshAccessToken(tokenStore, refreshToken)
                if (newAccess != null) {
                    accessToken = newAccess
                    response = NetworkModule.mobileApi.orcamentoCreate(buildRequest())
                }
            }
            val body = response.body()
            if (!response.isSuccessful || body == null) {
                return body ?: OrcamentoCreateResponse(ok = false, error = "Falha ao lançar o orçamento.")
            }
            body
        } catch (e: Exception) {
            AppLog.e("OrcamentoRepository", "Falha ao lançar orçamento", e)
            OrcamentoCreateResponse(ok = false, error = "Sem conexão. Tente novamente.")
        }
    }

    /** Foto da REQUISIÇÃO -- lê só número da requisição + quem autorizou. */
    suspend fun lerRequisicao(fotoUrl: String): OrcamentoOcrRequisicaoResponse? {
        val tokens = tokenStore.current() ?: return null
        var (accessToken, refreshToken) = tokens
        return try {
            fun buildRequest() = OrcamentoOcrRequisicaoRequest(accessToken = accessToken, refreshToken = refreshToken, fotoUrl = fotoUrl)
            var response = NetworkModule.mobileApi.orcamentoOcrRequisicao(buildRequest())
            if (response.code() == 401) {
                val newAccess = TokenRefresher.refreshAccessToken(tokenStore, refreshToken)
                if (newAccess != null) {
                    accessToken = newAccess
                    response = NetworkModule.mobileApi.orcamentoOcrRequisicao(buildRequest())
                }
            }
            response.body() ?: OrcamentoOcrRequisicaoResponse(ok = false, error = "Falha na leitura automática.")
        } catch (e: Exception) {
            AppLog.e("OrcamentoRepository", "Falha ao ler requisição via OCR", e)
            OrcamentoOcrRequisicaoResponse(ok = false, error = "Sem conexão. Tente novamente.")
        }
    }

    /** Foto do COMPROVANTE/ORÇAMENTO -- lê só a lista de itens. */
    suspend fun lerItens(fotoUrl: String): OrcamentoOcrItensResponse? {
        val tokens = tokenStore.current() ?: return null
        var (accessToken, refreshToken) = tokens
        return try {
            fun buildRequest() = OrcamentoOcrItensRequest(accessToken = accessToken, refreshToken = refreshToken, fotoUrl = fotoUrl)
            var response = NetworkModule.mobileApi.orcamentoOcrItens(buildRequest())
            if (response.code() == 401) {
                val newAccess = TokenRefresher.refreshAccessToken(tokenStore, refreshToken)
                if (newAccess != null) {
                    accessToken = newAccess
                    response = NetworkModule.mobileApi.orcamentoOcrItens(buildRequest())
                }
            }
            response.body() ?: OrcamentoOcrItensResponse(ok = false, error = "Falha na leitura automática.")
        } catch (e: Exception) {
            AppLog.e("OrcamentoRepository", "Falha ao ler itens via OCR", e)
            OrcamentoOcrItensResponse(ok = false, error = "Sem conexão. Tente novamente.")
        }
    }
}
