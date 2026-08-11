package com.bragro.mobile.data.repo

import com.bragro.mobile.data.AppLog
import com.bragro.mobile.data.model.WeatherResponse
import com.bragro.mobile.data.remote.NetworkModule

/** Clima/Cambio/Cotacoes ao vivo (Fase 2, Task #35) -- busca em
 * /api/mobile/weather (reaproveita getWeather()/getFxRates()/
 * getCommodityQuotes() do site). Rota publica, sem token. De proposito SEM
 * cache no Room -- mesmo criterio do CachedDashboard no site: "Dolar agora"
 * ou "clima agora" desatualizado seria mais confuso que util, entao esta
 * tela so mostra o dado quando consegue buscar ao vivo. */
class WeatherRepository {
    suspend fun fetch(): WeatherResponse? {
        return try {
            val response = NetworkModule.mobileApi.weather()
            val body = response.body()
            if (!response.isSuccessful || body?.ok != true) null else body
        } catch (e: Exception) {
            AppLog.e("WeatherRepository", "Falha ao buscar clima/câmbio/cotações ao vivo", e)
            null
        }
    }
}
