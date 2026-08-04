package com.bragro.mobile.data.repo

import android.content.Context
import com.bragro.mobile.data.TokenStore
import com.bragro.mobile.data.model.ModuleActionRequest
import com.bragro.mobile.data.model.ModuleChartsRequest
import com.bragro.mobile.data.model.ModuleChartsResponse
import com.bragro.mobile.data.remote.NetworkModule
import kotlinx.serialization.json.JsonObject

/** Bloco "Gráficos" (Task de réplica completa dos módulos) -- busca em
 * /api/mobile/module-charts, que reaproveita getModuleChartData()/
 * getExtraModuleChartsAction() do site. De propósito SEM cache no Room
 * (mesmo critério de WeatherRepository/HomeRepository): um gráfico
 * desatualizado seria mais confuso que útil, e esses dados só importam
 * enquanto a tela do módulo está aberta -- se não tiver conexão, o bloco
 * simplesmente fica vazio/oculto em vez de mostrar um retrato velho. */
class ChartsRepository(context: Context) {
    private val tokenStore = TokenStore(context)

    suspend fun fetch(domainId: String, safra: String? = null): ModuleChartsResponse? {
        val tokens = tokenStore.current() ?: return null
        var (accessToken, refreshToken) = tokens
        return try {
            var response = NetworkModule.mobileApi.moduleCharts(ModuleChartsRequest(accessToken, refreshToken, domainId, safra))
            if (response.code() == 401) {
                val newAccess = TokenRefresher.refreshAccessToken(tokenStore, refreshToken)
                if (newAccess != null) {
                    accessToken = newAccess
                    response = NetworkModule.mobileApi.moduleCharts(ModuleChartsRequest(accessToken, refreshToken, domainId, safra))
                }
            }
            val body = response.body()
            if (!response.isSuccessful || body?.ok != true) null else body
        } catch (e: Exception) {
            null
        }
    }
}

/** Botões sem formulário (Recalcular Vencimentos/Área, Eficiência de Frota)
 * -- busca/dispara em /api/mobile/module-actions. */
class ModuleActionsRepository(context: Context) {
    private val tokenStore = TokenStore(context)

    suspend fun run(action: String): JsonObject? {
        val tokens = tokenStore.current() ?: return null
        var (accessToken, refreshToken) = tokens
        return try {
            var response = NetworkModule.mobileApi.moduleActions(ModuleActionRequest(accessToken, refreshToken, action))
            if (response.code() == 401) {
                val newAccess = TokenRefresher.refreshAccessToken(tokenStore, refreshToken)
                if (newAccess != null) {
                    accessToken = newAccess
                    response = NetworkModule.mobileApi.moduleActions(ModuleActionRequest(accessToken, refreshToken, action))
                }
            }
            val body = response.body()
            if (!response.isSuccessful || body?.ok != true) null else body.result
        } catch (e: Exception) {
            null
        }
    }
}
