package com.bragro.mobile.data.remote

import com.bragro.mobile.data.model.AnalisesRequest
import com.bragro.mobile.data.model.AnalisesResponse
import com.bragro.mobile.data.model.AuditInfoRequest
import com.bragro.mobile.data.model.AuditInfoResponse
import com.bragro.mobile.data.model.BackupRequest
import com.bragro.mobile.data.model.BackupResponse
import com.bragro.mobile.data.model.BankImportConfirmRequest
import com.bragro.mobile.data.model.BankImportConfirmResponse
import com.bragro.mobile.data.model.BankImportSignaturesRequest
import com.bragro.mobile.data.model.BankImportSignaturesResponse
import com.bragro.mobile.data.model.BaseDeDadosRequest
import com.bragro.mobile.data.model.BaseDeDadosResponse
import com.bragro.mobile.data.model.BootstrapRequest
import com.bragro.mobile.data.model.BridgeCodeRequest
import com.bragro.mobile.data.model.BridgeCodeResponse
import com.bragro.mobile.data.model.BootstrapResponse
import com.bragro.mobile.data.model.ConfigResponse
import com.bragro.mobile.data.model.ControleInsumosRequest
import com.bragro.mobile.data.model.ControleInsumosResponse
import com.bragro.mobile.data.model.DashboardRequest
import com.bragro.mobile.data.model.DashboardResponse
import com.bragro.mobile.data.model.DreRequest
import com.bragro.mobile.data.model.DreResponse
import com.bragro.mobile.data.model.DroneCreateRequest
import com.bragro.mobile.data.model.DroneCreateResponse
import com.bragro.mobile.data.model.DroneListRequest
import com.bragro.mobile.data.model.DroneListResponse
import com.bragro.mobile.data.model.FieldviewImportRequest
import com.bragro.mobile.data.model.FieldviewImportResponse
import com.bragro.mobile.data.model.FieldviewRequest
import com.bragro.mobile.data.model.FieldviewResponse
import com.bragro.mobile.data.model.HomeRequest
import com.bragro.mobile.data.model.HomeResponse
import com.bragro.mobile.data.model.LivroCaixaRequest
import com.bragro.mobile.data.model.LivroCaixaResponse
import com.bragro.mobile.data.model.ProdutorRuralRequest
import com.bragro.mobile.data.model.ProdutorRuralResponse
import com.bragro.mobile.data.model.ModuleActionRequest
import com.bragro.mobile.data.model.ModuleActionResponse
import com.bragro.mobile.data.model.ModuleChartsRequest
import com.bragro.mobile.data.model.ModuleChartsResponse
import com.bragro.mobile.data.model.NfeImportRequest
import com.bragro.mobile.data.model.NfeImportResponse
import com.bragro.mobile.data.model.NfePreviewRequest
import com.bragro.mobile.data.model.NfePreviewResponse
import com.bragro.mobile.data.model.NoticesRequest
import com.bragro.mobile.data.model.NoticesResponse
import com.bragro.mobile.data.model.NotaMultiItemRequest
import com.bragro.mobile.data.model.NotaMultiItemResponse
import com.bragro.mobile.data.model.PedidoMultiItemRequest
import com.bragro.mobile.data.model.PedidoMultiItemResponse
import com.bragro.mobile.data.model.CotacaoMultiItemRequest
import com.bragro.mobile.data.model.CotacaoMultiItemResponse
import com.bragro.mobile.data.model.OperacoesRequest
import com.bragro.mobile.data.model.OperacoesResponse
import com.bragro.mobile.data.model.NotificationsRequest
import com.bragro.mobile.data.model.NotificationsResponse
import com.bragro.mobile.data.model.GetProviderIntegrationRequest
import com.bragro.mobile.data.model.GetProviderIntegrationResponse
import com.bragro.mobile.data.model.SaveProviderIntegrationRequest
import com.bragro.mobile.data.model.SaveProviderIntegrationResponse
import com.bragro.mobile.data.model.DisconnectProviderIntegrationRequest
import com.bragro.mobile.data.model.DisconnectProviderIntegrationResponse
import com.bragro.mobile.data.model.SyncProviderIntegrationRequest
import com.bragro.mobile.data.model.SyncProviderIntegrationResponse
import com.bragro.mobile.data.model.GetModuleIntegrationRequest
import com.bragro.mobile.data.model.SaveModuleIntegrationRequest
import com.bragro.mobile.data.model.DisconnectModuleIntegrationRequest
import com.bragro.mobile.data.model.SyncModuleIntegrationRequest
import com.bragro.mobile.data.model.RecordsRequest
import com.bragro.mobile.data.model.RecordsResponse
import com.bragro.mobile.data.model.SecurityRequest
import com.bragro.mobile.data.model.SecurityResponse
import com.bragro.mobile.data.model.SettingsRequest
import com.bragro.mobile.data.model.SettingsResponse
import com.bragro.mobile.data.model.SupabaseLoginRequest
import com.bragro.mobile.data.model.SupabaseLoginResponse
import com.bragro.mobile.data.model.SyncRequest
import com.bragro.mobile.data.model.SyncResponse
import com.bragro.mobile.data.model.UpdateAvatarRequest
import com.bragro.mobile.data.model.UpdateAvatarResponse
import com.bragro.mobile.data.model.UpdateLogoRequest
import com.bragro.mobile.data.model.UpdateLogoResponse
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

/** Rotas /api/mobile (varias) do site publicado (Next.js) -- ver
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

    @POST("api/mobile/home")
    suspend fun home(@Body body: HomeRequest): Response<HomeResponse>

    @POST("api/mobile/module-charts")
    suspend fun moduleCharts(@Body body: ModuleChartsRequest): Response<ModuleChartsResponse>

    @POST("api/mobile/module-actions")
    suspend fun moduleActions(@Body body: ModuleActionRequest): Response<ModuleActionResponse>

    @POST("api/mobile/bank-import")
    suspend fun bankImportSignatures(@Body body: BankImportSignaturesRequest): Response<BankImportSignaturesResponse>

    @POST("api/mobile/bank-import")
    suspend fun bankImportConfirm(@Body body: BankImportConfirmRequest): Response<BankImportConfirmResponse>

    @POST("api/mobile/dre")
    suspend fun dre(@Body body: DreRequest): Response<DreResponse>

    // Painel "Controle de Insumos" (gap encontrado na auditoria módulo-a-
    // módulo, pedido do usuario "implemente tudo que falta ainda para o app
    // native da plataforma").
    @POST("api/mobile/controle-insumos")
    suspend fun controleInsumos(@Body body: ControleInsumosRequest): Response<ControleInsumosResponse>

    @POST("api/mobile/livro-caixa")
    suspend fun livroCaixa(@Body body: LivroCaixaRequest): Response<LivroCaixaResponse>

    // Produtor Rural / IRPF (config da Organization) -- pedido do usuario
    // ("implemente tudo que falta ainda para o app native da plataforma").
    @POST("api/mobile/produtor-rural")
    suspend fun produtorRural(@Body body: ProdutorRuralRequest): Response<ProdutorRuralResponse>

    // Financeiro: "Lançar nota com itens" (multi-item) -- pedido do usuario
    // ("implemente tudo que falta ainda para o app native da plataforma").
    @POST("api/mobile/nota-multi-item")
    suspend fun notaMultiItem(@Body body: NotaMultiItemRequest): Response<NotaMultiItemResponse>

    // Pedidos/Cotações: "novo modelo" de vários itens (pedido do usuario
    // "insira o novo modelo dos modulos cotaçoes e pedidos no app native"),
    // mesmo padrao do notaMultiItem acima.
    @POST("api/mobile/pedido-multi-item")
    suspend fun pedidoMultiItem(@Body body: PedidoMultiItemRequest): Response<PedidoMultiItemResponse>

    @POST("api/mobile/cotacao-multi-item")
    suspend fun cotacaoMultiItem(@Body body: CotacaoMultiItemRequest): Response<CotacaoMultiItemResponse>

    // Visão "Operação" agrupada (gap encontrado na auditoria módulo-a-módulo,
    // pedido do usuario "implemente tudo que falta ainda para o app native
    // da plataforma").
    @POST("api/mobile/operacoes")
    suspend fun operacoes(@Body body: OperacoesRequest): Response<OperacoesResponse>

    @POST("api/mobile/analises")
    suspend fun analises(@Body body: AnalisesRequest): Response<AnalisesResponse>

    @POST("api/mobile/records")
    suspend fun records(@Body body: RecordsRequest): Response<RecordsResponse>

    @POST("api/mobile/audit-info")
    suspend fun auditInfo(@Body body: AuditInfoRequest): Response<AuditInfoResponse>

    @POST("api/mobile/drone")
    suspend fun droneList(@Body body: DroneListRequest): Response<DroneListResponse>

    @POST("api/mobile/drone")
    suspend fun droneCreate(@Body body: DroneCreateRequest): Response<DroneCreateResponse>

    @POST("api/mobile/fieldview")
    suspend fun fieldview(@Body body: FieldviewRequest): Response<FieldviewResponse>

    // Mesma URL de fieldview() acima, corpo/resposta diferentes (o
    // Retrofit resolve pelo tipo do parametro no ponto de chamada, nao pela
    // URL -- o backend distingue os dois fluxos pelo campo "action" dentro
    // do JSON, ver comentario em FieldviewImportRequest/Models.kt).
    @POST("api/mobile/fieldview")
    suspend fun importFieldBoundary(@Body body: FieldviewImportRequest): Response<FieldviewImportResponse>

    // Card "Acesso automático via prestadora de serviço" (Task #341/#54) --
    // 4 ações novas na mesma rota /api/mobile/fieldview, distinguidas pelo
    // "action" dentro do corpo (mesma convenção de importFieldBoundary()
    // acima). Ver ProviderIntegrationRepository.kt.
    @POST("api/mobile/fieldview")
    suspend fun fieldviewGetIntegration(@Body body: GetProviderIntegrationRequest): Response<GetProviderIntegrationResponse>

    @POST("api/mobile/fieldview")
    suspend fun fieldviewSaveIntegration(@Body body: SaveProviderIntegrationRequest): Response<SaveProviderIntegrationResponse>

    @POST("api/mobile/fieldview")
    suspend fun fieldviewDisconnectIntegration(@Body body: DisconnectProviderIntegrationRequest): Response<DisconnectProviderIntegrationResponse>

    @POST("api/mobile/fieldview")
    suspend fun fieldviewSyncIntegration(@Body body: SyncProviderIntegrationRequest): Response<SyncProviderIntegrationResponse>

    // Mesmas 4 ações, agora na rota /api/mobile/drone -- mesmo corpo/
    // resposta (o módulo é implícito na URL, ver comentário em Models.kt).
    @POST("api/mobile/drone")
    suspend fun droneGetIntegration(@Body body: GetProviderIntegrationRequest): Response<GetProviderIntegrationResponse>

    @POST("api/mobile/drone")
    suspend fun droneSaveIntegration(@Body body: SaveProviderIntegrationRequest): Response<SaveProviderIntegrationResponse>

    @POST("api/mobile/drone")
    suspend fun droneDisconnectIntegration(@Body body: DisconnectProviderIntegrationRequest): Response<DisconnectProviderIntegrationResponse>

    @POST("api/mobile/drone")
    suspend fun droneSyncIntegration(@Body body: SyncProviderIntegrationRequest): Response<SyncProviderIntegrationResponse>

    // Mesmo card, agora pra bomba de combustível (Frota) e balança
    // (Romaneios) -- diferente de FieldView/Drone (endpoint dedicado por
    // módulo), esses dois já são módulos genéricos, então usam UMA rota só
    // (/api/mobile/module-integration) com "modulo" no corpo (ver
    // GetModuleIntegrationRequest/etc. em Models.kt e MODULE_TO_DOMAIN em
    // module-integration/route.ts no site).
    @POST("api/mobile/module-integration")
    suspend fun moduleGetIntegration(@Body body: GetModuleIntegrationRequest): Response<GetProviderIntegrationResponse>

    @POST("api/mobile/module-integration")
    suspend fun moduleSaveIntegration(@Body body: SaveModuleIntegrationRequest): Response<SaveProviderIntegrationResponse>

    @POST("api/mobile/module-integration")
    suspend fun moduleDisconnectIntegration(@Body body: DisconnectModuleIntegrationRequest): Response<DisconnectProviderIntegrationResponse>

    @POST("api/mobile/module-integration")
    suspend fun moduleSyncIntegration(@Body body: SyncModuleIntegrationRequest): Response<SyncProviderIntegrationResponse>

    @POST("api/mobile/nfe-preview")
    suspend fun nfePreview(@Body body: NfePreviewRequest): Response<NfePreviewResponse>

    @POST("api/mobile/nfe-import")
    suspend fun nfeImport(@Body body: NfeImportRequest): Response<NfeImportResponse>

    @POST("api/mobile/notifications")
    suspend fun notifications(@Body body: NotificationsRequest): Response<NotificationsResponse>

    @POST("api/mobile/backup")
    suspend fun backup(@Body body: BackupRequest): Response<BackupResponse>

    @POST("api/mobile/notices")
    suspend fun notices(@Body body: NoticesRequest): Response<NoticesResponse>

    // 3 telas administrativas nativas (Task #148) -- ver comentário em
    // Models.kt (SettingsRequest) pra contexto completo.
    @POST("api/mobile/settings")
    suspend fun settings(@Body body: SettingsRequest): Response<SettingsResponse>

    @POST("api/mobile/base-de-dados")
    suspend fun baseDeDados(@Body body: BaseDeDadosRequest): Response<BaseDeDadosResponse>

    @POST("api/mobile/security")
    suspend fun security(@Body body: SecurityRequest): Response<SecurityResponse>

    @POST("api/mobile/bridge-code")
    suspend fun bridgeCode(@Body body: BridgeCodeRequest): Response<BridgeCodeResponse>

    // Persiste a URL da logo da organizacao apos o upload direto pro Storage
    // (ver LogoUploadRepository.kt) -- equivalente mobile de updateLogoAction
    // em configuracoes/actions.ts (mesma exigencia de OWNER/ADMIN no servidor).
    @POST("api/mobile/update-logo")
    suspend fun updateLogo(@Body body: UpdateLogoRequest): Response<UpdateLogoResponse>

    // Persiste a URL da foto de perfil apos o upload direto pro Storage
    // (ver AvatarUploadRepository.kt) -- equivalente mobile de
    // updateAvatarAction em configuracoes/actions.ts.
    @POST("api/mobile/update-avatar")
    suspend fun updateAvatar(@Body body: UpdateAvatarRequest): Response<UpdateAvatarResponse>

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
