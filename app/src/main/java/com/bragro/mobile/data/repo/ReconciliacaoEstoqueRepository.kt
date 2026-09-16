package com.bragro.mobile.data.repo

import android.content.Context
import com.bragro.mobile.data.AppLog
import com.bragro.mobile.data.TokenStore
import com.bragro.mobile.data.model.ReconciliacaoEstoqueRequest
import com.bragro.mobile.data.model.ReconciliacaoEstoqueResponse
import com.bragro.mobile.data.remote.NetworkModule

/** Reconciliação Físico x Fiscal de Estoque (Task #603/#607) -- busca em
 * /api/mobile/reconciliacao-estoque, que reaproveita 100% a MESMA função de
 * serviço (getReconciliacaoEstoque) que a página do site chama, nenhum
 * cálculo duplicado em Kotlin. DE PROPÓSITO SEM CACHE no Room (mesmo
 * critério de ChartsRepository/HomeRepository): é um relatório ponto-no-
 * tempo -- um retrato desatualizado do saldo fiscal x físico seria mais
 * confuso que útil, então sem conexão a tela mostra "sem conexão" em vez de
 * um resultado antigo. */
class ReconciliacaoEstoqueRepository(context: Context) {
    private val tokenStore = TokenStore(context)

    suspend fun fetch(): ReconciliacaoEstoqueResponse? {
        val tokens = tokenStore.current() ?: return null
        var (accessToken, refreshToken) = tokens
        return try {
            var response = NetworkModule.mobileApi.reconciliacaoEstoque(ReconciliacaoEstoqueRequest(accessToken, refreshToken))
            if (response.code() == 401) {
                val newAccess = TokenRefresher.refreshAccessToken(tokenStore, refreshToken)
                if (newAccess != null) {
                    accessToken = newAccess
                    response = NetworkModule.mobileApi.reconciliacaoEstoque(ReconciliacaoEstoqueRequest(accessToken, refreshToken))
                }
            }
            val body = response.body()
            if (!response.isSuccessful || body?.ok != true) null else body
        } catch (e: Exception) {
            AppLog.e("ReconciliacaoEstoqueRepository", "Falha ao buscar reconciliação físico x fiscal de estoque", e)
            null
        }
    }
}
