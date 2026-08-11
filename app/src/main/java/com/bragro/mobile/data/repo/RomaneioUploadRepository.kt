package com.bragro.mobile.data.repo

import android.content.Context
import com.bragro.mobile.BuildConfig
import com.bragro.mobile.data.AppLog
import com.bragro.mobile.data.TokenStore
import com.bragro.mobile.data.local.AppDatabase
import com.bragro.mobile.data.remote.NetworkModule
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

private const val FOTO_BUCKET = "romaneios"

/** Sobe a foto do ticket da balanca pro Supabase Storage (Fase 2, Task #42)
 * -- MESMO bucket "romaneios" e MESMA convencao de caminho
 * ({orgId}/ticket_{timestamp}.jpg) que o site usa em
 * quick-romaneio-button.tsx, pra cair sob as mesmas politicas de RLS de
 * Storage ja configuradas (escrita restrita a organizacao do usuario,
 * leitura publica) -- nada de infraestrutura nova no backend. */
class RomaneioUploadRepository(private val context: Context) {
    private val db = AppDatabase.get(context)
    private val tokenStore = TokenStore(context)

    /** Retorna a URL publica da foto, ou null se offline/sem sessao/erro
     * (quem chamou deixa o usuario seguir sem foto -- nunca bloqueia o
     * lancamento por causa disso, mesmo criterio do site). */
    suspend fun uploadTicketPhoto(bytes: ByteArray): String? {
        val tokens = tokenStore.current() ?: return null
        val session = db.sessionDao().get() ?: return null
        val orgId = session.orgId
        return try {
            val path = "$orgId/ticket_${System.currentTimeMillis()}.jpg"
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
            AppLog.e("RomaneioUploadRepository", "Falha ao enviar foto do ticket da balança pro Storage", e)
            null
        }
    }
}
