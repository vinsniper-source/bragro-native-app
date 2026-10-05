package com.bragro.mobile.data

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bragro.mobile.data.local.SessionEntity
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

private val Context.vaultStore by preferencesDataStore(name = "bragro_offline_vault")

/** "Cofre" de login offline -- pedido do usuario (plataforma totalmente
 * offline): depois de UM login com internet neste aparelho, da pra entrar
 * de novo SEM internet, mesmo depois de sair da conta.
 *
 * Seguranca: a senha NUNCA e guardada -- so um verificador PBKDF2
 * (HmacSHA256, salt aleatorio, 120 mil iteracoes), que so serve pra
 * conferir se a senha digitada offline e a mesma do ultimo login online.
 * Guarda tambem um retrato da sessao (quem e, organizacao, permissoes) pra
 * reabrir o app. Isso NAO concede acesso a nada no servidor: gravacoes
 * continuam so valendo quando sincronizadas online com token real (a fila
 * de sincronizacao so envia depois de um login online). So existe um cofre
 * por aparelho (o do ultimo usuario que logou online). */
class OfflineVault(private val context: Context) {
    private val keyEmail = stringPreferencesKey("email")
    private val keySalt = stringPreferencesKey("salt")
    private val keyHash = stringPreferencesKey("hash")
    private val keySession = stringPreferencesKey("session_json")

    /** Chamado apos login online bem-sucedido (bootstrap ok). */
    suspend fun save(email: String, password: String, session: SessionEntity) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = derive(password, salt)
        context.vaultStore.edit {
            it[keyEmail] = email.trim().lowercase()
            it[keySalt] = Base64.encodeToString(salt, Base64.NO_WRAP)
            it[keyHash] = Base64.encodeToString(hash, Base64.NO_WRAP)
            it[keySession] = sessionToJson(session)
        }
    }

    /** Devolve a sessao guardada se e-mail+senha conferem; senao null. */
    suspend fun verify(email: String, password: String): SessionEntity? {
        val prefs = context.vaultStore.data.first()
        val savedEmail = prefs[keyEmail] ?: return null
        val saltB64 = prefs[keySalt] ?: return null
        val hashB64 = prefs[keyHash] ?: return null
        val sessionJson = prefs[keySession] ?: return null
        if (savedEmail != email.trim().lowercase()) return null
        val expected = Base64.decode(hashB64, Base64.NO_WRAP)
        val actual = derive(password, Base64.decode(saltB64, Base64.NO_WRAP))
        // Comparacao em tempo constante.
        if (!MessageDigest.isEqual(expected, actual)) return null
        return jsonToSession(sessionJson)
    }

    suspend fun hasVault(): Boolean = context.vaultStore.data.first()[keyHash] != null

    private fun derive(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_BITS)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    private fun sessionToJson(s: SessionEntity): String = JSONObject().apply {
        put("userId", s.userId)
        put("email", s.email)
        put("orgId", s.orgId)
        put("orgName", s.orgName)
        put("orgLogoUrl", s.orgLogoUrl ?: JSONObject.NULL)
        put("avatarUrl", s.avatarUrl ?: JSONObject.NULL)
        put("role", s.role)
        put("allowedModulesCsv", s.allowedModulesCsv)
        put("planTier", s.planTier)
        put("accessToken", s.accessToken)
        put("refreshToken", s.refreshToken)
    }.toString()

    private fun jsonToSession(json: String): SessionEntity? = try {
        val o = JSONObject(json)
        SessionEntity(
            userId = o.getString("userId"),
            email = o.getString("email"),
            orgId = o.getString("orgId"),
            orgName = o.getString("orgName"),
            orgLogoUrl = if (o.isNull("orgLogoUrl")) null else o.getString("orgLogoUrl"),
            avatarUrl = if (o.isNull("avatarUrl")) null else o.getString("avatarUrl"),
            role = o.getString("role"),
            allowedModulesCsv = o.getString("allowedModulesCsv"),
            planTier = o.getString("planTier"),
            accessToken = o.getString("accessToken"),
            refreshToken = o.getString("refreshToken"),
            atualizadoEm = System.currentTimeMillis(),
        )
    } catch (e: Exception) {
        AppLog.e("OfflineVault", "Falha ao ler sessao guardada no cofre offline", e)
        null
    }

    private companion object {
        const val ITERATIONS = 120_000
        const val KEY_BITS = 256
    }
}
