package com.bragro.mobile.data.repo

import android.content.Context
import com.bragro.mobile.data.AppLog
import com.bragro.mobile.data.TokenStore
import com.bragro.mobile.data.remote.NetworkModule
import kotlinx.serialization.Serializable

/** v64 -- listas que dependem do campo anterior (/api/mobile/sugestoes):
 * campo -> campo-pai -> valor do pai (sem acento, maiúsculo) -> opções. */
@Serializable
data class SugestoesRequest(val accessToken: String, val refreshToken: String, val domainId: String)

@Serializable
data class SugestoesResponse(
    val ok: Boolean,
    val sugestoes: Map<String, Map<String, Map<String, List<String>>>>? = null,
    val error: String? = null,
)

class SugestoesRepository(context: Context) {
    private val tokenStore = TokenStore(context)

    suspend fun carregar(domainId: String): Map<String, Map<String, Map<String, List<String>>>>? {
        val tokens = tokenStore.current() ?: return null
        var (accessToken, refreshToken) = tokens
        return try {
            var response = NetworkModule.mobileApi.sugestoes(SugestoesRequest(accessToken, refreshToken, domainId))
            if (response.code() == 401) {
                val novo = TokenRefresher.refreshAccessToken(tokenStore, refreshToken)
                if (novo != null) {
                    accessToken = novo
                    response = NetworkModule.mobileApi.sugestoes(SugestoesRequest(accessToken, refreshToken, domainId))
                }
            }
            val body = response.body()
            if (!response.isSuccessful || body?.ok != true) null else body.sugestoes
        } catch (e: Exception) {
            AppLog.e("SugestoesRepository", "Falha ao carregar sugestões", e)
            null
        }
    }
}
