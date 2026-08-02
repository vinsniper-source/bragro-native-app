package com.bragro.mobile.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "bragro_tokens")

/** Guarda o access/refresh token da sessao Supabase no dispositivo -- e o
 * que permite o app funcionar offline (as chamadas a /api/mobile/* e
 * /api/offline-sync usam esses tokens, nao um cookie de navegador, ver
 * data/repo/AuthRepository.kt). DataStore (nao SharedPreferences cru) por
 * ser a API atual recomendada pelo Android, baseada em coroutines/Flow. */
class TokenStore(private val context: Context) {
    private val keyAccess = stringPreferencesKey("access_token")
    private val keyRefresh = stringPreferencesKey("refresh_token")
    private val keyEmail = stringPreferencesKey("email")

    val accessTokenFlow: Flow<String?> = context.dataStore.data.map { it[keyAccess] }
    val emailFlow: Flow<String?> = context.dataStore.data.map { it[keyEmail] }

    suspend fun save(accessToken: String, refreshToken: String, email: String) {
        context.dataStore.edit {
            it[keyAccess] = accessToken
            it[keyRefresh] = refreshToken
            it[keyEmail] = email
        }
    }

    suspend fun updateAccessToken(accessToken: String) {
        context.dataStore.edit { it[keyAccess] = accessToken }
    }

    suspend fun current(): Pair<String, String>? {
        val prefs = context.dataStore.data.first()
        val access = prefs[keyAccess] ?: return null
        val refresh = prefs[keyRefresh] ?: return null
        return access to refresh
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }
}
