package com.bragro.mobile.data.repo

import android.content.Context
import com.bragro.mobile.BuildConfig
import com.bragro.mobile.data.AppLog
import com.bragro.mobile.data.TokenStore
import com.bragro.mobile.data.local.AppDatabase
import com.bragro.mobile.data.remote.NetworkModule
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

private const val FOTO_BUCKET = "lookup-fotos"

/** Sobe a foto anexada a um valor de lista suspensa (categoria "visual":
 * itens_estoque, frotas, marcas, racas_pecuaria, oficinas, locais -- ver
 * BaseDeDadosScreen.kt) pro Supabase Storage. MESMO padrão de bucket/caminho
 * de PragaUploadRepository.kt/LogoUploadRepository.kt, bucket dedicado
 * "lookup-fotos" (mesmo nome usado do lado do site, upload de foto no
 * cadastro de LookupOption) -- não reaproveita "pragas" nem "branding" pra
 * manter os arquivos de cada categoria separados por bucket, igual o resto
 * do app já faz. */
class LookupOptionUploadRepository(private val context: Context) {
    private val db = AppDatabase.get(context)
    private val tokenStore = TokenStore(context)

    /** Retorna a URL pública da foto, ou null se offline/sem sessão/erro --
     * quem chamou segue o cadastro do valor sem foto (upload é aditivo,
     * nunca bloqueia o "+Criar"/"+" do dropdown). */
    suspend fun uploadFoto(bytes: ByteArray): String? {
        val tokens = tokenStore.current() ?: return null
        val session = db.sessionDao().get() ?: return null
        val orgId = session.orgId
        return try {
            val path = "$orgId/lookup_${System.currentTimeMillis()}.jpg"
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
            AppLog.e("LookupOptionUploadRepository", "Falha ao enviar foto do valor de lista suspensa pro Storage", e)
            null
        }
    }
}
