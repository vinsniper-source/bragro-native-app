package com.bragro.mobile.data.repo

import android.content.Context
import com.bragro.mobile.data.AppLog
import com.bragro.mobile.data.TokenStore
import com.bragro.mobile.data.model.DisconnectProviderIntegrationRequest
import com.bragro.mobile.data.model.GetProviderIntegrationRequest
import com.bragro.mobile.data.model.ProviderIntegrationDto
import com.bragro.mobile.data.model.SaveProviderIntegrationRequest
import com.bragro.mobile.data.model.SyncProviderIntegrationRequest
import com.bragro.mobile.data.remote.NetworkModule

/** Módulo em que o card "Acesso automático via prestadora de serviço" está
 * sendo usado -- só decide qual par de rotas (/api/mobile/fieldview ou
 * /api/mobile/drone) o Retrofit chama, ver [ProviderIntegrationRepository]. */
enum class IntegrationModule { FIELDVIEW, DRONE }

/** Resultado de "Testar sincronização" -- [ok] reflete se a sincronização
 * em si funcionou (hoje sempre false, ver comentário de topo em
 * provider-integration.ts no site: depende de aprovação de parceiro do
 * fabricante), não sucesso HTTP. [mensagem] é sempre preenchida pelo
 * backend, pronta pra mostrar num Snackbar/Toast. */
data class IntegrationSyncResult(val ok: Boolean, val mensagem: String)

/** Réplica mobile do card "Acesso automático via prestadora de serviço"
 * (Task #341/#54, ver components/domain/provider-integration-card.tsx e
 * lib/services/provider-integration.ts no site) -- get/save/disconnect/
 * sync da credencial do provedor externo (John Deere, Climate FieldView,
 * DJI Terra etc.) por organização. Um repository só, parametrizado por
 * [module], evita duplicar a lógica de retry/token entre FieldView e
 * Drone (mesmo padrão de FieldviewRepository/DroneRepository, ver
 * TokenRefresher). */
class ProviderIntegrationRepository(context: Context, private val module: IntegrationModule) {
    private val tokenStore = TokenStore(context)

    suspend fun get(): ProviderIntegrationDto? {
        val tokens = tokenStore.current() ?: return null
        var (accessToken, refreshToken) = tokens
        return try {
            var response = callGet(accessToken, refreshToken)
            if (response.code() == 401) {
                val newAccess = TokenRefresher.refreshAccessToken(tokenStore, refreshToken)
                if (newAccess != null) {
                    accessToken = newAccess
                    response = callGet(accessToken, refreshToken)
                }
            }
            val body = response.body()
            if (!response.isSuccessful || body?.ok != true) null else body.integration
        } catch (e: Exception) {
            AppLog.e("ProviderIntegrationRepository", "Falha ao buscar integração de provedor ($module)", e)
            null
        }
    }

    suspend fun save(provedor: String, apiKey: String): Boolean {
        val tokens = tokenStore.current() ?: return false
        var (accessToken, refreshToken) = tokens
        return try {
            var response = callSave(accessToken, refreshToken, provedor, apiKey)
            if (response.code() == 401) {
                val newAccess = TokenRefresher.refreshAccessToken(tokenStore, refreshToken)
                if (newAccess != null) {
                    accessToken = newAccess
                    response = callSave(accessToken, refreshToken, provedor, apiKey)
                }
            }
            response.isSuccessful && response.body()?.ok == true
        } catch (e: Exception) {
            AppLog.e("ProviderIntegrationRepository", "Falha ao salvar integração de provedor ($module)", e)
            false
        }
    }

    suspend fun disconnect(): Boolean {
        val tokens = tokenStore.current() ?: return false
        var (accessToken, refreshToken) = tokens
        return try {
            var response = callDisconnect(accessToken, refreshToken)
            if (response.code() == 401) {
                val newAccess = TokenRefresher.refreshAccessToken(tokenStore, refreshToken)
                if (newAccess != null) {
                    accessToken = newAccess
                    response = callDisconnect(accessToken, refreshToken)
                }
            }
            response.isSuccessful && response.body()?.ok == true
        } catch (e: Exception) {
            AppLog.e("ProviderIntegrationRepository", "Falha ao desconectar integração de provedor ($module)", e)
            false
        }
    }

    suspend fun sync(): IntegrationSyncResult {
        val tokens = tokenStore.current()
            ?: return IntegrationSyncResult(false, "Sessão expirada -- faça login novamente.")
        var (accessToken, refreshToken) = tokens
        return try {
            var response = callSync(accessToken, refreshToken)
            if (response.code() == 401) {
                val newAccess = TokenRefresher.refreshAccessToken(tokenStore, refreshToken)
                if (newAccess != null) {
                    accessToken = newAccess
                    response = callSync(accessToken, refreshToken)
                }
            }
            val body = response.body()
            if (!response.isSuccessful || body == null) {
                IntegrationSyncResult(false, "Falha ao sincronizar -- confira a conexão e tente de novo.")
            } else {
                IntegrationSyncResult(body.ok, body.mensagem ?: "Sincronização concluída.")
            }
        } catch (e: Exception) {
            AppLog.e("ProviderIntegrationRepository", "Falha ao sincronizar integração de provedor ($module)", e)
            IntegrationSyncResult(false, "Falha ao sincronizar -- confira a conexão e tente de novo.")
        }
    }

    private suspend fun callGet(accessToken: String, refreshToken: String) =
        when (module) {
            IntegrationModule.FIELDVIEW -> NetworkModule.mobileApi.fieldviewGetIntegration(GetProviderIntegrationRequest(accessToken, refreshToken))
            IntegrationModule.DRONE -> NetworkModule.mobileApi.droneGetIntegration(GetProviderIntegrationRequest(accessToken, refreshToken))
        }

    private suspend fun callSave(accessToken: String, refreshToken: String, provedor: String, apiKey: String) =
        when (module) {
            IntegrationModule.FIELDVIEW -> NetworkModule.mobileApi.fieldviewSaveIntegration(SaveProviderIntegrationRequest(accessToken, refreshToken, provedor = provedor, apiKey = apiKey))
            IntegrationModule.DRONE -> NetworkModule.mobileApi.droneSaveIntegration(SaveProviderIntegrationRequest(accessToken, refreshToken, provedor = provedor, apiKey = apiKey))
        }

    private suspend fun callDisconnect(accessToken: String, refreshToken: String) =
        when (module) {
            IntegrationModule.FIELDVIEW -> NetworkModule.mobileApi.fieldviewDisconnectIntegration(DisconnectProviderIntegrationRequest(accessToken, refreshToken))
            IntegrationModule.DRONE -> NetworkModule.mobileApi.droneDisconnectIntegration(DisconnectProviderIntegrationRequest(accessToken, refreshToken))
        }

    private suspend fun callSync(accessToken: String, refreshToken: String) =
        when (module) {
            IntegrationModule.FIELDVIEW -> NetworkModule.mobileApi.fieldviewSyncIntegration(SyncProviderIntegrationRequest(accessToken, refreshToken))
            IntegrationModule.DRONE -> NetworkModule.mobileApi.droneSyncIntegration(SyncProviderIntegrationRequest(accessToken, refreshToken))
        }
}
