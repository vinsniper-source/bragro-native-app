package com.bragro.mobile.data.repo

import android.content.Context
import com.bragro.mobile.BuildConfig
import com.bragro.mobile.data.AppLog
import com.bragro.mobile.data.TokenStore
import com.bragro.mobile.data.local.AppDatabase
import com.bragro.mobile.data.model.UpdateLogoRequest
import com.bragro.mobile.data.remote.NetworkModule
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

private const val LOGO_BUCKET = "branding"

/** Sobe a logo da organizacao direto pelo app -- pedido do usuario ("quando
 * clicar em adicionar a logo, coloque quando clicar a adicionar a logo por
 * la memo"), substituindo o Toast que so apontava pro site. MESMO bucket
 * "branding" e MESMA convencao de caminho do site
 * (topbar.tsx/configuracoes/actions.ts): {orgId}/logo_{timestamp}.{ext} --
 * upload direto no Storage com o token do proprio usuario (RLS restringe
 * escrita a OWNER/ADMIN da propria organizacao, mesma politica que ja existe
 * pro site), depois persiste a URL via /api/mobile/update-logo (equivalente
 * mobile de updateLogoAction). MESMO padrao ja usado por
 * RomaneioUploadRepository.kt pra foto de ticket -- nada de infraestrutura
 * nova no backend, so uma rota pra gravar o campo apos o upload. */
class LogoUploadRepository(context: Context) {
    private val db = AppDatabase.get(context)
    private val tokenStore = TokenStore(context)

    /** Retorna a URL publica em caso de sucesso, ou uma mensagem de erro
     * pronta pra mostrar ao usuario (Result.failure). */
    suspend fun uploadLogo(bytes: ByteArray, mimeType: String, ext: String): Result<String> {
        val tokens = tokenStore.current() ?: return Result.failure(IllegalStateException("Sessão expirada. Entre novamente."))
        var (accessToken, refreshToken) = tokens
        val session = db.sessionDao().get() ?: return Result.failure(IllegalStateException("Sessão expirada. Entre novamente."))
        val orgId = session.orgId
        return try {
            val path = "$orgId/logo_${System.currentTimeMillis()}.$ext"
            val body = bytes.toRequestBody(mimeType.toMediaType())
            var uploadResponse = NetworkModule.supabaseStorageApi.upload(
                bucket = LOGO_BUCKET,
                path = path,
                authorization = "Bearer $accessToken",
                apiKey = BuildConfig.SUPABASE_ANON_KEY,
                contentType = mimeType,
                upsert = "true",
                body = body,
            )
            if (uploadResponse.code() == 401) {
                val newAccess = TokenRefresher.refreshAccessToken(tokenStore, refreshToken)
                if (newAccess != null) {
                    accessToken = newAccess
                    uploadResponse = NetworkModule.supabaseStorageApi.upload(
                        bucket = LOGO_BUCKET,
                        path = path,
                        authorization = "Bearer $accessToken",
                        apiKey = BuildConfig.SUPABASE_ANON_KEY,
                        contentType = mimeType,
                        upsert = "true",
                        body = body,
                    )
                }
            }
            if (!uploadResponse.isSuccessful) {
                return Result.failure(IllegalStateException("Falha ao enviar a imagem (código ${uploadResponse.code()})."))
            }
            val publicUrl = "${BuildConfig.SUPABASE_URL}/storage/v1/object/public/$LOGO_BUCKET/$path"

            var apiResponse = NetworkModule.mobileApi.updateLogo(UpdateLogoRequest(accessToken, refreshToken, publicUrl))
            if (apiResponse.code() == 401) {
                val newAccess = TokenRefresher.refreshAccessToken(tokenStore, refreshToken)
                if (newAccess != null) {
                    accessToken = newAccess
                    apiResponse = NetworkModule.mobileApi.updateLogo(UpdateLogoRequest(accessToken, refreshToken, publicUrl))
                }
            }
            val apiBody = apiResponse.body()
            if (!apiResponse.isSuccessful || apiBody?.ok != true) {
                return Result.failure(IllegalStateException(apiBody?.error ?: "Não foi possível salvar a logo."))
            }

            // Atualiza o cache local imediatamente -- a tela observa
            // sessionDao().observe(), então o ícone vira a logo nova sem
            // precisar de outro refresh manual.
            db.sessionDao().upsert(session.copy(orgLogoUrl = publicUrl))
            Result.success(publicUrl)
        } catch (e: Exception) {
            AppLog.e("LogoUploadRepository", "Falha ao enviar/atualizar logo da organização", e)
            Result.failure(e)
        }
    }
}
