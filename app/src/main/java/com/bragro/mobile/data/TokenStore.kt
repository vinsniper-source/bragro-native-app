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
 * que permite o app funcionar offline (as chamadas a /api/mobile (varias
 * rotas) e /api/offline-sync usam esses tokens, nao um cookie de navegador, ver
 * data/repo/AuthRepository.kt). DataStore (nao SharedPreferences cru) por
 * ser a API atual recomendada pelo Android, baseada em coroutines/Flow. */
class TokenStore(private val context: Context) {
    private val keyAccess = stringPreferencesKey("access_token")
    private val keyRefresh = stringPreferencesKey("refresh_token")
    private val keyEmail = stringPreferencesKey("email")
    // Task #124 (isolamento de cache por organizacao) -- guarda o orgId da
    // ULTIMA organizacao autenticada com sucesso neste aparelho, sobrevive a
    // logout() de proposito (logout() NAO chama clear() em nenhuma dessas
    // duas chaves -- ver AuthRepository.logout()): e o que permite
    // AuthRepository.login() comparar "org nova == org anterior" mesmo
    // depois de um logout/login normal na MESMA conta.
    private val keyLastOrgId = stringPreferencesKey("last_org_id")

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

    /** Ultima organizacao autenticada com sucesso neste aparelho -- null no
     * primeiríssimo login (nunca logou antes neste aparelho), caso em que
     * AuthRepository.login() NAO deve tratar como "troca de organizacao"
     * (nao ha fila pendente de organizacao nenhuma pra limpar ainda). */
    suspend fun getLastOrgId(): String? = context.dataStore.data.first()[keyLastOrgId]

    suspend fun setLastOrgId(orgId: String) {
        context.dataStore.edit { it[keyLastOrgId] = orgId }
    }

    /** Chamado no logout -- apaga access/refresh/email (a sessao em si),
     * mas PROPOSITALMENTE preserva "last_org_id" (Task #124): esse valor so
     * faz sentido sobrevivendo ao logout, senao AuthRepository.login()
     * nunca conseguiria distinguir "relogando na mesma organizacao depois
     * de um logout normal" (deve preservar a fila pendente) de "outra
     * organizacao logando por cima" (deve limpar) -- ver comentario em
     * getLastOrgId() acima. Antes usava it.clear() (limpeza total do
     * DataStore), o que apagava esse rastro junto. */
    suspend fun clear() {
        context.dataStore.edit {
            it.remove(keyAccess)
            it.remove(keyRefresh)
            it.remove(keyEmail)
        }
    }
}
