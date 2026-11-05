package com.bragro.mobile.data.remote

import com.bragro.mobile.BuildConfig
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/** Monta os clientes Retrofit -- um pro backend do site (api/mobile,
 * api/offline-sync) e outro pro Supabase Auth (login direto). Singletons
 * simples (sem framework de DI) pra manter o projeto enxuto na Fase 1. */
object NetworkModule {
    // v1.6.12 -- encodeDefaults: sem isso, campos com valor padrão (ex.: action = "list")
    // não iam no corpo da requisição, e o servidor respondia "Campos obrigatórios
    // ausentes" (Pivôs vazio no app). explicitNulls = false continua omitindo nulos.
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }

    private fun httpClient(segundos: Long = 20): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
        }
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(segundos, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    val mobileApi: MobileApi by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL + "/")
            .client(httpClient())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(MobileApi::class.java)
    }

    /** v80 -- mesma API, com espera de até 70 s (NDVI próprio: o servidor
     * lê imagens de satélite na primeira vez de cada talhão). */
    val mobileApiLongo: MobileApi by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL + "/")
            .client(httpClient(70))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(MobileApi::class.java)
    }

    val supabaseAuthApi: SupabaseAuthApi by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.SUPABASE_URL + "/")
            .client(httpClient())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(SupabaseAuthApi::class.java)
    }

    // Sem converter de JSON aqui de proposito -- upload de Storage manda
    // bytes crus (RequestBody), nao um corpo JSON serializado.
    val supabaseStorageApi: SupabaseStorageApi by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.SUPABASE_URL + "/")
            .client(httpClient())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(SupabaseStorageApi::class.java)
    }
}
