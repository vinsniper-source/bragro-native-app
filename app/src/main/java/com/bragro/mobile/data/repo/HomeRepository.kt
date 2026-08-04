package com.bragro.mobile.data.repo

import android.content.Context
import com.bragro.mobile.data.TokenStore
import com.bragro.mobile.data.model.HomeData
import com.bragro.mobile.data.model.HomeRequest
import com.bragro.mobile.data.remote.NetworkModule

/** Réplica mobile do Início (Mural de Avisos + Central de Alertas + Monitor
 * de atividade recente + KPIs) -- busca em /api/mobile/home. De propósito
 * SEM cache no Room, mesmo critério do WeatherRepository: avisos/alertas/
 * atividade desatualizados seriam mais confusos que úteis, então a tela só
 * mostra esse bloco quando consegue buscar ao vivo (os KPIs "de verdade" já
 * têm cache offline separado via DashboardRepository/DashboardEntity, usados
 * na tela de Dashboard). */
class HomeRepository(context: Context) {
    private val tokenStore = TokenStore(context)

    suspend fun fetch(): HomeData? {
        val tokens = tokenStore.current() ?: return null
        var (accessToken, refreshToken) = tokens
        return try {
            var response = NetworkModule.mobileApi.home(HomeRequest(accessToken, refreshToken))
            if (response.code() == 401) {
                val newAccess = TokenRefresher.refreshAccessToken(tokenStore, refreshToken)
                if (newAccess != null) {
                    accessToken = newAccess
                    response = NetworkModule.mobileApi.home(HomeRequest(accessToken, refreshToken))
                }
            }
            val body = response.body()
            if (!response.isSuccessful || body?.ok != true) null else body.home
        } catch (e: Exception) {
            null
        }
    }
}
