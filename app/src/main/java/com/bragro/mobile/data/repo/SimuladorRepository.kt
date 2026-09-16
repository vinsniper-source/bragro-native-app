package com.bragro.mobile.data.repo

import android.content.Context
import com.bragro.mobile.data.AppLog
import com.bragro.mobile.data.TokenStore
import com.bragro.mobile.data.model.SimuladorRequest
import com.bragro.mobile.data.model.SimuladorBaseData
import com.bragro.mobile.data.remote.NetworkModule

/** Simulador de Cenários "E se?" (Task #600/#616) -- busca a base REAL uma
 * única vez em /api/mobile/simulador (reaproveita getSimuladorBase do
 * site). SEM CACHE no Room de propósito, mesmo critério de
 * ReconciliacaoEstoqueRepository/DossieRepository -- é a base pra uma
 * calculadora, uma base desatualizada geraria projeções erradas sem o
 * usuário perceber. Todo o recálculo "e se" (produtividade/preço/receita/
 * custo/margem projetados) acontece no ViewModel da tela, em Kotlin puro,
 * SEM round-trip nenhum -- ver SimuladorScreen.kt. */
class SimuladorRepository(context: Context) {
    private val tokenStore = TokenStore(context)

    suspend fun fetchBase(): SimuladorBaseData? {
        val tokens = tokenStore.current() ?: return null
        var (accessToken, refreshToken) = tokens
        return try {
            var response = NetworkModule.mobileApi.simulador(SimuladorRequest(accessToken, refreshToken))
            if (response.code() == 401) {
                val newAccess = TokenRefresher.refreshAccessToken(tokenStore, refreshToken)
                if (newAccess != null) {
                    accessToken = newAccess
                    response = NetworkModule.mobileApi.simulador(SimuladorRequest(accessToken, refreshToken))
                }
            }
            val body = response.body()
            if (!response.isSuccessful || body?.ok != true) null else body.base
        } catch (e: Exception) {
            AppLog.e("SimuladorRepository", "Falha ao buscar base do simulador", e)
            null
        }
    }
}
