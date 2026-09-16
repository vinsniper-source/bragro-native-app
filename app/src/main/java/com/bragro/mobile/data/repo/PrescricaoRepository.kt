package com.bragro.mobile.data.repo

import android.content.Context
import com.bragro.mobile.data.AppLog
import com.bragro.mobile.data.TokenStore
import com.bragro.mobile.data.model.PrescricaoRequest
import com.bragro.mobile.data.model.PrescricaoResponse
import com.bragro.mobile.data.remote.NetworkModule

/** Prescrição / Taxa Variável -- VISUALIZADOR simples (Task #604/#608):
 * busca em /api/mobile/prescricao, que reaproveita 100% os registros já
 * salvos pelo site (savePrescricaoAction já resolveu taxaMedia/Min/Max e
 * gravou o geojson) -- nenhuma geração de SHP/ISO-XML nem geoprocessamento
 * novo em Kotlin. DE PROPÓSITO SEM CACHE no Room (mesmo critério de
 * ChartsRepository/ReconciliacaoEstoqueRepository). */
class PrescricaoRepository(context: Context) {
    private val tokenStore = TokenStore(context)

    suspend fun fetch(): PrescricaoResponse? {
        val tokens = tokenStore.current() ?: return null
        var (accessToken, refreshToken) = tokens
        return try {
            var response = NetworkModule.mobileApi.prescricao(PrescricaoRequest(accessToken, refreshToken))
            if (response.code() == 401) {
                val newAccess = TokenRefresher.refreshAccessToken(tokenStore, refreshToken)
                if (newAccess != null) {
                    accessToken = newAccess
                    response = NetworkModule.mobileApi.prescricao(PrescricaoRequest(accessToken, refreshToken))
                }
            }
            val body = response.body()
            if (!response.isSuccessful || body?.ok != true) null else body
        } catch (e: Exception) {
            AppLog.e("PrescricaoRepository", "Falha ao buscar prescrições de taxa variável", e)
            null
        }
    }
}
