package com.bragro.mobile.data.repo

import android.content.Context
import com.bragro.mobile.data.TokenStore
import com.bragro.mobile.data.model.FieldviewImportRequest
import com.bragro.mobile.data.model.FieldviewRequest
import com.bragro.mobile.data.model.FieldviewResponse
import com.bragro.mobile.data.remote.NetworkModule
import kotlinx.serialization.json.JsonElement

/** Módulo FieldView (Task #107/#110) -- talhões, status de safra/frota,
 * mapa nativo (osmdroid) e importação nativa de KML/KMZ. Busca/lança em
 * /api/mobile/fieldview (distinguido pelo campo "action" no corpo -- ver
 * comentário em FieldviewImportRequest/Models.kt). */
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

    /** Importa um talhão (contorno + área) parseado de um KML/KMZ no
     * aparelho -- upsert por [orgId, talhao] no servidor (mesma convenção
     * talhao-por-nome de Safra/Frota). Retorna true em caso de sucesso. */
    suspend fun importBoundary(talhao: String, nome: String?, geojson: JsonElement, areaHaCalc: Double?): Boolean {
        val tokens = tokenStore.current() ?: return false
        var (accessToken, refreshToken) = tokens
        fun buildRequest(token: String) = FieldviewImportRequest(
            accessToken = token, refreshToken = refreshToken,
            talhao = talhao, nome = nome, geojson = geojson, areaHaCalc = areaHaCalc,
        )
        return try {
            var response = NetworkModule.mobileApi.importFieldBoundary(buildRequest(accessToken))
            if (response.code() == 401) {
                val newAccess = TokenRefresher.refreshAccessToken(tokenStore, refreshToken)
                if (newAccess != null) {
                    accessToken = newAccess
                    response = NetworkModule.mobileApi.importFieldBoundary(buildRequest(accessToken))
                }
            }
            val body = response.body()
            response.isSuccessful && body?.ok == true
        } catch (e: Exception) {
            false
        }
    }
}
