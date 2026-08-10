package com.bragro.mobile.data.repo

import android.content.Context
import com.bragro.mobile.data.TokenStore
import com.bragro.mobile.data.model.FieldviewRequest
import com.bragro.mobile.data.model.FieldviewResponse
import com.bragro.mobile.data.remote.NetworkModule

/** Módulo FieldView (Task #107) -- versão SÓ DADOS (talhões + status de
 * safra/frota), sem mapa interativo nem importação de KML/KMZ (ver
 * comentário completo em /api/mobile/fieldview/route.ts e no resumo final
 * desta rodada). Busca em /api/mobile/fieldview. */
class FieldviewRepository(context: Context) {
    private val tokenStore = TokenStore(context)

    suspend fun fetch(): FieldviewResponse? {
        val tokens = tokenStore.current() ?: return null
        var (accessToken, refreshToken) = tokens
        return try {
            var response = NetworkModule.mobileApi.fieldview(FieldviewRequest(accessToken, refreshToken))
            if (response.code() == 401) {
                val newAccess = TokenRefresher.refreshAccessToken(tokenStore, refreshToken)
                if (newAccess != null) {
                    accessToken = newAccess
                    response = NetworkModule.mobileApi.fieldview(FieldviewRequest(accessToken, refreshToken))
                }
            }
            val body = response.body()
            if (!response.isSuccessful || body?.ok != true) null else body
        } catch (e: Exception) {
            null
        }
    }
}
