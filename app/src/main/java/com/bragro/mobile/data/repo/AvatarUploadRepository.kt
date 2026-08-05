package com.bragro.mobile.data.repo

import android.content.Context
import com.bragro.mobile.BuildConfig
import com.bragro.mobile.data.TokenStore
import com.bragro.mobile.data.local.AppDatabase
import com.bragro.mobile.data.model.UpdateAvatarRequest
import com.bragro.mobile.data.remote.NetworkModule
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

private const val AVATAR_BUCKET = "avatars"

/** Sobe a foto de perfil do próprio usuário direto pelo app -- pedido do
 * usuário ("coloque também no ícone usuário a opção de inserir foto"),
 * mesmo padrão de LogoUploadRepository.kt (upload direto no Storage com o
 * token do próprio usuário, depois persiste a URL numa rota /api/mobile/*).
 * Diferença: bucket "avatars", caminho por USUÁRIO (não por organização,
 * ver topbar.tsx: `${userId}/avatar_${timestamp}.${ext}`), e qualquer
 * usuário pode trocar a própria foto (sem exigir OWNER/ADMIN, ao contrário
 * da logo da organização). */
class AvatarUploadRepository(context: Context) {
    private val db = AppDatabase.get(context)
    private val tokenStore = TokenStore(context)

    suspend fun uploadAvatar(bytes: ByteArray, mimeType: String, ext: String): Result<String> {
        val tokens = tokenStore.current() ?: return Result.failure(IllegalStateException("Sessão expirada. Entre novamente."))
        var (accessToken, refreshToken) = tokens
        val session = db.sessionDao().get() ?: return Result.failure(IllegalStateException("Sessão expirada. Entre novamente."))
        val userId = session.userId
        return try {
            val path = "$userId/avatar_${System.currentTimeMillis()}.$ext"
            val body = bytes.toRequestBody(mimeType.toMediaType())
            var uploadResponse = NetworkModule.supabaseStorageApi.upload(
                bucket = AVATAR_BUCKET,
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
                        bucket = AVATAR_BUCKET,
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
            val publicUrl = "${BuildConfig.SUPABASE_URL}/storage/v1/object/public/$AVATAR_BUCKET/$path"

            var apiResponse = NetworkModule.mobileApi.updateAvatar(UpdateAvatarRequest(accessToken, refreshToken, publicUrl))
            if (apiResponse.code() == 401) {
                val newAccess = TokenRefresher.refreshAccessToken(tokenStore, refreshToken)
                if (newAccess != null) {
                    accessToken = newAccess
                    apiResponse = NetworkModule.mobileApi.updateAvatar(UpdateAvatarRequest(accessToken, refreshToken, publicUrl))
                }
            }
            val apiBody = apiResponse.body()
            if (!apiResponse.isSuccessful || apiBody?.ok != true) {
                return Result.failure(IllegalStateException(apiBody?.error ?: "Não foi possível salvar a foto."))
            }

            db.sessionDao().upsert(session.copy(avatarUrl = publicUrl))
            Result.success(publicUrl)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
