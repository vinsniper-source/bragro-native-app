package com.bragro.mobile.data.repo

import android.content.Context
import com.bragro.mobile.data.AppLog
import com.bragro.mobile.data.TokenStore
import com.bragro.mobile.data.model.DossieRequest
import com.bragro.mobile.data.model.DossieResponse
import com.bragro.mobile.data.remote.NetworkModule

/** Dossiê Bancário (Task #599/#615) -- busca em /api/mobile/dossie, que
 * reaproveita 100% a MESMA função de serviço (getDossieData) que a página
 * do site chama, nenhum cálculo duplicado em Kotlin. DE PROPÓSITO SEM
 * CACHE no Room (mesmo critério de ReconciliacaoEstoqueRepository/
 * PrescricaoRepository): é um relatório ponto-no-tempo pra anexar num
 * pedido de crédito -- um retrato desatualizado seria mais confuso que
 * útil, então sem conexão a tela mostra "sem conexão" em vez de um
 * resultado antigo. */
class DossieRepository(context: Context) {
    private val tokenStore = TokenStore(context)

    suspend fun fetch(ano: Int? = null): DossieResponse? {
        val tokens = tokenStore.current() ?: return null
        var (accessToken, refreshToken) = tokens
        return try {
            var response = NetworkModule.mobileApi.dossie(DossieRequest(accessToken, refreshToken, ano))
            if (response.code() == 401) {
                val newAccess = TokenRefresher.refreshAccessToken(tokenStore, refreshToken)
                if (newAccess != null) {
                    accessToken = newAccess
                    response = NetworkModule.mobileApi.dossie(DossieRequest(accessToken, refreshToken, ano))
                }
            }
            val body = response.body()
            if (!response.isSuccessful || body?.ok != true) null else body
        } catch (e: Exception) {
            AppLog.e("DossieRepository", "Falha ao buscar dossie bancario (ano=$ano)", e)
            null
        }
    }
}
