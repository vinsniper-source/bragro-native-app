package com.bragro.mobile.data.repo

import android.content.Context
import com.bragro.mobile.BuildConfig
import com.bragro.mobile.data.TokenStore
import com.bragro.mobile.data.local.AppDatabase
import com.bragro.mobile.data.remote.NetworkModule
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

private const val DRONE_BUCKET = "drone"

/** Sobe o arquivo de captura (foto/NDVI/ortomosaico/vídeo) pro Supabase
 * Storage -- MESMO bucket "drone" e MESMA convenção de caminho
 * ({orgId}/{talhao}/{timestamp}_{nomeOriginal}) que drone-client.tsx usa no
 * site, pra cair sob a mesma política de RLS já configurada. Mesmo
 * princípio de RomaneioUploadRepository.kt. */
class DroneUploadRepository(private val context: Context) {
    private val db = AppDatabase.get(context)
    private val tokenStore = TokenStore(context)

    data class UploadResult(val storagePath: String, val publicUrl: String, val fileSizeBytes: Long)

    suspend fun upload(bytes: ByteArray, fileName: String, mimeType: String, talhao: String?): UploadResult? {
        val tokens = tokenStore.current() ?: return null
        val session = db.sessionDao().get() ?: return null
        val orgId = session.orgId
        return try {
            val safeName = fileName.replace(Regex("[^a-zA-Z0-9.\\-_]"), "_")
            val talhaoSegment = talhao?.takeIf { it.isNotBlank() } ?: "sem-talhao"
            val path = "$orgId/$talhaoSegment/${System.currentTimeMillis()}_$safeName"
            val body = bytes.toRequestBody(mimeType.toMediaType())
            val response = NetworkModule.supabaseStorageApi.upload(
                bucket = DRONE_BUCKET,
                path = path,
                authorization = "Bearer ${tokens.first}",
                apiKey = BuildConfig.SUPABASE_ANON_KEY,
                contentType = mimeType,
                body = body,
            )
            if (!response.isSuccessful) return null
            val publicUrl = "${BuildConfig.SUPABASE_URL}/storage/v1/object/public/$DRONE_BUCKET/$path"
            UploadResult(path, publicUrl, bytes.size.toLong())
        } catch (e: Exception) {
            null
        }
    }
}
