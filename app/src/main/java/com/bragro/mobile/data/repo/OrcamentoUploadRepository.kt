package com.bragro.mobile.data.repo

import android.content.Context
import com.bragro.mobile.BuildConfig
import com.bragro.mobile.data.AppLog
import com.bragro.mobile.data.TokenStore
import com.bragro.mobile.data.local.AppDatabase
import com.bragro.mobile.data.remote.NetworkModule
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

private const val FOTO_BUCKET = "orcamentos"

/** Sobe a foto da requisição/comprovante pro Supabase Storage -- ver
 * handoff-ocr-orcamento.md. MESMA convenção de caminho
 * ({orgId}/{prefixo}_{timestamp}.jpg) e MESMO padrão de bucket público com
 * escrita restrita por organização (RLS) já usados em
 * RomaneioUploadRepository.kt/DroneUploadRepository.kt -- nada de
 * infraestrutura nova no backend, bucket "orcamentos" já provisionado em
 * prisma/sql/05-storage-orcamentos.sql. */
class OrcamentoUploadRepository(private val context: Context) {
    private val db = AppDatabase.get(context)
    private val tokenStore = TokenStore(context)

    /** Retorna a URL pública da foto, ou null se offline/sem sessão/erro
     * (quem chamou deixa o usuário seguir sem foto -- OCR fica desabilitado
     * pra aquele bloco, mas o lançamento manual continua possível). */
    suspend fun uploadFoto(bytes: ByteArray, prefixo: String): String? {
        val tokens = tokenStore.current() ?: return null
        val session = db.sessionDao().get() ?: return null
        val orgId = session.orgId
        return try {
            val path = "$orgId/${prefixo}_${System.currentTimeMillis()}.jpg"
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
            AppLog.e("OrcamentoUploadRepository", "Falha ao enviar foto do orçamento pro Storage", e)
            null
        }
    }
}
