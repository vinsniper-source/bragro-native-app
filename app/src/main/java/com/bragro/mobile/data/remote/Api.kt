package com.bragro.mobile.data.remote

import com.bragro.mobile.data.model.AnalisesRequest
import com.bragro.mobile.data.model.AnalisesResponse
import com.bragro.mobile.data.model.BootstrapRequest
import com.bragro.mobile.data.model.BootstrapResponse
import com.bragro.mobile.data.model.ConfigResponse
import com.bragro.mobile.data.model.DashboardRequest
import com.bragro.mobile.data.model.DashboardResponse
import com.bragro.mobile.data.model.DreRequest
import com.bragro.mobile.data.model.DreResponse
import com.bragro.mobile.data.model.NfeImportRequest
import com.bragro.mobile.data.model.NfeImportResponse
import com.bragro.mobile.data.model.NfePreviewRequest
import com.bragro.mobile.data.model.NfePreviewResponse
import com.bragro.mobile.data.model.RecordsRequest
import com.bragro.mobile.data.model.RecordsResponse
import com.bragro.mobile.data.model.SupabaseLoginRequest
import com.bragro.mobile.data.model.SupabaseLoginResponse
import com.bragro.mobile.data.model.SyncRequest
import com.bragro.mobile.data.model.SyncResponse
import com.bragro.mobile.data.model.WeatherResponse
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** Login direto no Supabase Auth (REST) -- o app NAO passa pelo site pra
 * autenticar, so pra tudo que e dado de negocio (bootstrap/registros/sync).
 * Mesmo mecanismo que qualquer app mobile oficial do Supabase usa. */
interface SupabaseAuthApi {
    @POST("auth/v1/token")
    suspend fun login(
        @Query("grant_type") grantType: String,
        @Header("apikey") apiKey: String,
        @Body body: SupabaseLoginRequest,
    ): Response<SupabaseLoginResponse>

    @POST("auth/v1/token")
    suspend fun refresh(
        @Query("grant_type") grantType: String,
        @Header("apikey") apiKey: String,
        @Body body: Map<String, String>,
    ): Response<SupabaseLoginResponse>
}

/** Rotas /api/mobile/* do site publicado (Next.js) -- ver
 * src/app/api/mobile/{config,bootstrap,records}/route.ts e
 * src/app/api/offline-sync/route.ts no repositorio do site. Reaproveitam
 * 100% da validacao/calculos/RLS ja existentes; este app nao duplica
 * nenhuma regra de negocio. */
interface MobileApi {
    @GET("api/mobile/config")
    suspend fun config(): Response<ConfigResponse>

    @POST("api/mobile/bootstrap")
    suspend fun bootstrap(@Body body: BootstrapRequest): Response<BootstrapResponse>

    @POST("api/mobile/dashboard")
    suspend fun dashboard(@Body body: DashboardRequest): Response<DashboardResponse>

    @POST("api/mobile/dre")
    suspend fun dre(@Body body: DreRequest): Response<DreResponse>

    @POST("api/mobile/analises")
    suspend fun analises(@Body body: AnalisesRequest): Response<AnalisesResponse>

    @POST("api/mobile/records")
    suspend fun records(@Body body: RecordsRequest): Response<RecordsResponse>

    @POST("api/mobile/nfe-preview")
    suspend fun nfePreview(@Body body: NfePreviewRequest): Response<NfePreviewResponse>

    @POST("api/mobile/nfe-import")
    suspend fun nfeImport(@Body body: NfeImportRequest): Response<NfeImportResponse>

    // Rota publica, sem token (ver comentario em WeatherResponse/route.ts).
    @GET("api/mobile/weather")
    suspend fun weather(): Response<WeatherResponse>

    @POST("api/offline-sync")
    suspend fun sync(@Body body: SyncRequest): Response<SyncResponse>
}

/** Supabase Storage REST direto (Fase 2, Task #42) -- mesmo bucket/politica
 * de RLS que o site ja usa pra foto de ticket de romaneio (ver
 * components/domain/quick-romaneio-button.tsx e
 * prisma/sql/04-storage-romaneios.sql: escrita restrita a organizacao do
 * usuario logado, leitura publica). O app nativo sobe a foto DIRETO pro
 * Storage (com o access_token da propria sessao, sem passar por nenhuma
 * rota /api/mobile) exatamente como o navegador faz via supabase-js --
 * nao ha logica de negocio aqui, so transporte de arquivo. */
interface SupabaseStorageApi {
    @POST("storage/v1/object/{bucket}/{path}")
    suspend fun upload(
        @Path("bucket") bucket: String,
        @Path(value = "path", encoded = true) path: String,
        @Header("Authorization") authorization: String,
        @Header("apikey") apiKey: String,
        @Header("Content-Type") contentType: String,
        @Header("x-upsert") upsert: String = "false",
        @Body body: RequestBody,
    ): Response<Unit>
}
