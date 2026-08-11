package com.bragro.mobile.data.repo

import com.bragro.mobile.BuildConfig
import com.bragro.mobile.data.AppLog
import com.bragro.mobile.data.TokenStore
import com.bragro.mobile.data.remote.NetworkModule

/** Renovacao do access token via refresh_token (Supabase Auth) -- extraido
 * do que ja existia (duplicado) dentro de RecordRepository.trySyncOne para
 * ser reaproveitado tambem por DashboardRepository/DreRepository/
 * AnalisesRepository (Fase 2, Task #37): antes, so a fila de sincronizacao
 * tentava renovar o token quando um envio dava 401 -- as telas de
 * leitura (Inicio/DRE/Analises) simplesmente falhavam silenciosamente
 * (caiam pro estado "offline") se o access token (vida curta, ~1h) tivesse
 * expirado, mesmo com o aparelho online e o refresh_token (vida bem mais
 * longa) ainda valido. */
object TokenRefresher {
    suspend fun refreshAccessToken(tokenStore: TokenStore, refreshToken: String): String? {
        return try {
            val response = NetworkModule.supabaseAuthApi.refresh(
                grantType = "refresh_token",
                apiKey = BuildConfig.SUPABASE_ANON_KEY,
                body = mapOf("refresh_token" to refreshToken),
            )
            val newAccess = response.body()?.accessToken
            if (response.isSuccessful && newAccess != null) {
                tokenStore.updateAccessToken(newAccess)
                newAccess
            } else {
                null
            }
        } catch (e: Exception) {
            AppLog.e("TokenRefresher", "Falha ao renovar access token via refresh_token (Supabase Auth)", e)
            null
        }
    }
}
