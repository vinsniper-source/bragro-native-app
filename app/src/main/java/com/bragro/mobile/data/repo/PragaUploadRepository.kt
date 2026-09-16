package com.bragro.mobile.data.repo

import android.content.Context
import com.bragro.mobile.BuildConfig
import com.bragro.mobile.data.AppLog
import com.bragro.mobile.data.TokenStore
import com.bragro.mobile.data.local.AppDatabase
import com.bragro.mobile.data.remote.NetworkModule
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

private const val FOTO_BUCKET = "pragas"

/** Sobe a foto de planta/folha/inseto pro Supabase Storage -- paridade com
 * o site (Task #601, quick-praga-foto-button.tsx). MESMO padrão de
 * bucket/caminho de OrcamentoUploadRepository.kt/RomaneioUploadRepository.kt
 * (bucket "pragas" já provisionado no Storage, mesmo do site). */
class PragaUploadRepository(private val context: Context) {
    private val db = AppDatabase.get(context)
    private val tokenStore = TokenStore(context)

    /** Retorna a URL pública da foto, ou null se offline/sem sessão/erro
     * (quem chamou deixa o usuário lançar sem diagnóstico automático, só
     * preenchendo o Alvo manualmente). */
    suspend fun uploadFoto(bytes: ByteArray): String? {
        val tokens = tokenStore.current() ?: return null
        val session = db.sessionDao().get() ?: return null
        val orgId = session.orgId
        return try {
            val path = "$orgId/praga_${System.currentTimeMillis()}.jpg"
            val body = bytes.toRequestBody("image/jpeg".toMediaType())
            val response = NetworkModule.supabaseStorageApi.upload(
                bucket = FOTO_BUCKET,
                path = path,
                authorization = "Bearer ${tokens.first}",
                apiKey = BuildConfig.SUPABASE_ANON_KEY,
                contentType = "image/jpeg",
                body = body,
            )
            if (!response.isSuccessful) return null
            "${BuildConfig.SUPABASE_URL}/storage/v1/object/public/$FOTO_BUCKET/$path"
        } catch (e: Exception) {
            AppLog.e("PragaUploadRepository", "Falha ao enviar foto da praga pro Storage", e)
            null
        }
    }
}
