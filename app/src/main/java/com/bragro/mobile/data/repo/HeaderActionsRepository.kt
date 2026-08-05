package com.bragro.mobile.data.repo

import android.content.Context
import com.bragro.mobile.BuildConfig
import com.bragro.mobile.data.TokenStore
import com.bragro.mobile.data.model.BackupRequest
import com.bragro.mobile.data.model.BridgeCodeRequest
import com.bragro.mobile.data.model.NoticesRequest
import com.bragro.mobile.data.model.NotificationsRequest
import com.bragro.mobile.data.remote.NetworkModule
import kotlinx.serialization.json.JsonElement

/** Réplica mobile do sino de notificações da topbar (NotificationBell) --
 * busca/marca-como-lidas em /api/mobile/notifications. Mesmo critério de
 * SEM cache no Room dos demais blocos "ao vivo" do Início (Mural/Alertas/
 * Monitor): notificação desatualizada não ajuda ninguém. */
class NotificationsRepository(context: Context) {
    private val tokenStore = TokenStore(context)

    suspend fun run(action: String): JsonElement? {
        val tokens = tokenStore.current() ?: return null
        var (accessToken, refreshToken) = tokens
        return try {
            var response = NetworkModule.mobileApi.notifications(NotificationsRequest(accessToken, refreshToken, action))
            if (response.code() == 401) {
                val newAccess = TokenRefresher.refreshAccessToken(tokenStore, refreshToken)
                if (newAccess != null) {
                    accessToken = newAccess
                    response = NetworkModule.mobileApi.notifications(NotificationsRequest(accessToken, refreshToken, action))
                }
            }
            val body = response.body()
            if (!response.isSuccessful || body?.ok != true) null else body.result
        } catch (e: Exception) {
            null
        }
    }
}

/** Réplica mobile do botão "Backup" da topbar (só OWNER/ADMIN) -- busca o
 * JSON completo em /api/mobile/backup; quem chama decide o que fazer com
 * ele (salvar em cache + compartilhar via Intent, ver HomeScreen.kt). */
class BackupRepository(context: Context) {
    private val tokenStore = TokenStore(context)

    suspend fun fetch(): JsonElement? {
        val tokens = tokenStore.current() ?: return null
        var (accessToken, refreshToken) = tokens
        return try {
            var response = NetworkModule.mobileApi.backup(BackupRequest(accessToken, refreshToken))
            if (response.code() == 401) {
                val newAccess = TokenRefresher.refreshAccessToken(tokenStore, refreshToken)
                if (newAccess != null) {
                    accessToken = newAccess
                    response = NetworkModule.mobileApi.backup(BackupRequest(accessToken, refreshToken))
                }
            }
            val body = response.body()
            if (!response.isSuccessful || body?.ok != true) null else body.backup
        } catch (e: Exception) {
            null
        }
    }
}

/** Réplica mobile do Mural de Avisos com adição/remoção (só OWNER/ADMIN
 * pode criar/excluir; qualquer usuário pode listar) -- /api/mobile/notices.
 * Antes desse endpoint existir o app não tinha como adicionar aviso nenhum. */
class NoticesRepository(context: Context) {
    private val tokenStore = TokenStore(context)

    private suspend fun run(req: NoticesRequest): JsonElement? {
        val tokens = tokenStore.current() ?: return null
        var (accessToken, refreshToken) = tokens
        return try {
            var body = req.copy(accessToken = accessToken, refreshToken = refreshToken)
            var response = NetworkModule.mobileApi.notices(body)
            if (response.code() == 401) {
                val newAccess = TokenRefresher.refreshAccessToken(tokenStore, refreshToken)
                if (newAccess != null) {
                    accessToken = newAccess
                    body = body.copy(accessToken = accessToken)
                    response = NetworkModule.mobileApi.notices(body)
                }
            }
            val resp = response.body()
            if (!response.isSuccessful || resp?.ok != true) null else resp.result
        } catch (e: Exception) {
            null
        }
    }

    suspend fun list(): JsonElement? = run(NoticesRequest("", "", "list"))

    suspend fun create(titulo: String, mensagem: String, expiraEm: String?, fixado: Boolean): Boolean =
        run(NoticesRequest("", "", "create", titulo = titulo, mensagem = mensagem, expiraEm = expiraEm, fixado = fixado)) != null

    suspend fun delete(id: String): Boolean =
        run(NoticesRequest("", "", "delete", id = id)) != null
}

/** "Ponte" pro botão Módulos abrir Configurações/Base de Dados/Acessos no
 * navegador do aparelho JÁ autenticado -- pedido do usuário ("estão sendo
 * redirecionados para o app anterior... coloque as mesmas funções do
 * anterior neste atual"). Essas 3 telas administrativas (Stripe billing,
 * tokens de notificação Telegram/WhatsApp, permissões por usuário) são
 * grandes demais pra valer reescrever em Kotlin agora -- em vez disso, o
 * navegador abre logado de verdade (troca os tokens que o app já tem por um
 * código de curta duração em /api/mobile/bridge-code, que /api/mobile/bridge
 * consome pra gravar a sessão Supabase antes de redirecionar). Antes disso,
 * o navegador abria sem cookie nenhum (o app usa bearer token, não cookie) e
 * caía direto no /login -- daí o usuário ver "o app anterior". */
class BridgeRepository(context: Context) {
    private val tokenStore = TokenStore(context)

    /** Monta a URL final pro Intent.ACTION_VIEW -- se o código de ponte
     * falhar por qualquer motivo (rede, sessão expirada), cai pro link
     * simples de antes em vez de travar o botão. */
    suspend fun buildWebUrl(path: String): String {
        val fallback = "${BuildConfig.API_BASE_URL}/$path"
        val tokens = tokenStore.current() ?: return fallback
        var (accessToken, refreshToken) = tokens
        return try {
            var response = NetworkModule.mobileApi.bridgeCode(BridgeCodeRequest(accessToken, refreshToken))
            if (response.code() == 401) {
                val newAccess = TokenRefresher.refreshAccessToken(tokenStore, refreshToken)
                if (newAccess != null) {
                    accessToken = newAccess
                    response = NetworkModule.mobileApi.bridgeCode(BridgeCodeRequest(accessToken, refreshToken))
                }
            }
            val body = response.body()
            val code = if (response.isSuccessful && body?.ok == true) body.code else null
            if (code.isNullOrBlank()) fallback
            else "${BuildConfig.API_BASE_URL}/api/mobile/bridge?code=$code&next=/$path"
        } catch (e: Exception) {
            fallback
        }
    }
}
