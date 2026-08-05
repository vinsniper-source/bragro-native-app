package com.bragro.mobile.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

// Espelho em Kotlin de src/lib/domains/types.ts (site Next.js) -- os campos
// batem 1:1 com o JSON que /api/mobile/config devolve, que por sua vez e
// so a serializacao do MESMO registry.ts que o site usa. Nao reescrevemos a
// LISTA de modulos/campos aqui (isso ficaria desatualizado sozinho) -- so o
// formato pra conseguir ler o que a rota manda.

@Serializable
data class ColumnConfig(
    val key: String,
    val label: String,
    val type: String, // "text" | "number" | "date" | "select" | "checkbox" | "textarea"
    val required: Boolean = false,
    val computed: Boolean = false,
    val money: Boolean = false,
    val lookupCategory: String? = null,
    val staticOptions: List<String>? = null,
    val hideInTable: Boolean = false,
    val hint: String? = null,
)

@Serializable
data class DomainConfig(
    val id: String,
    val label: String,
    val columns: List<ColumnConfig>,
    val notice: String? = null,
)

@Serializable
data class ConfigResponse(
    val ok: Boolean,
    val domains: List<DomainConfig> = emptyList(),
    val error: String? = null,
)

@Serializable
data class SessionInfo(
    val userId: String,
    val email: String,
    val orgId: String,
    val orgName: String,
    val orgLogoUrl: String? = null,
    val avatarUrl: String? = null,
    val role: String,
    val allowedModules: List<String>,
    val planTier: String,
)

@Serializable
data class LookupItem(val category: String, val value: String, val label: String, val order: Int = 0)

@Serializable
data class FarmItem(val name: String, val areaHa: Double)

@Serializable
data class BootstrapResponse(
    val ok: Boolean,
    val session: SessionInfo? = null,
    val lookups: List<LookupItem> = emptyList(),
    val farms: List<FarmItem> = emptyList(),
    val error: String? = null,
)

@Serializable
data class BootstrapRequest(val accessToken: String, val refreshToken: String)

// Fase 2 (Task #31): KPIs do Início -- ver GET/POST /api/mobile/dashboard no
// site (src/app/api/mobile/dashboard/route.ts), que reaproveita a MESMA
// funcao getDashboardStats() (lib/services/dashboard.ts) usada pela pagina
// /dashboard do site. Mesmo shape de CachedDashboard (session-cache.ts) --
// o "retrato" de KPIs que o app Capacitor/WebView ja usa no modo offline
// dele, pra manter um unico contrato entre os dois esforcos de app mobile.
@Serializable
data class DashboardRequest(val accessToken: String, val refreshToken: String)

@Serializable
data class DashboardData(
    val orgName: String,
    val saldoFinanceiroAberto: Double,
    val itensEstoque: Int,
    val safrasAtivas: Int,
    val colaboradoresAtivos: Int,
    val culturaLider: String? = null,
    val pedidosAtrasados: Int,
    val alertsCount: Int,
)

@Serializable
data class DashboardResponse(
    val ok: Boolean,
    val dashboard: DashboardData? = null,
    val error: String? = null,
)

// Réplica mobile da tela "Início" do site (src/app/(app)/dashboard/page.tsx),
// pedida pelo usuario ("mesmo padrao da plataforma, réplica completa") -- ver
// POST /api/mobile/home no site (src/app/api/mobile/home/route.ts), que
// reaproveita getDashboardStats() (KPIs + Central de Alertas) e
// listNoticesForOrg() (Mural de Avisos) -- as MESMAS funcoes que a pagina web
// usa. "recentActivity" é a única peça sem equivalente 1:1: no site é um
// websocket do Supabase Realtime (ver realtime-monitor.tsx); aqui é um
// retrato das ultimas alteracoes nas mesmas 6 tabelas, atualizado a cada
// refresh -- ver comentario completo no route.ts.
@Serializable
data class HomeRequest(val accessToken: String, val refreshToken: String)

@Serializable
data class AlertData(
    val id: String,
    val tipo: String,
    val severidade: String, // "alta" | "media"
    val titulo: String,
    val descricao: String,
    val href: String,
)

@Serializable
data class NoticeData(
    val id: String,
    val titulo: String,
    val mensagem: String,
    val fixado: Boolean,
    val expiraEm: String? = null,
)

@Serializable
data class ActivityEventData(
    val id: String,
    val table: String,
    val tableLabel: String,
    // Descrição da operação do evento (operacao/item/compradorDestino
    // conforme a tabela -- ver ACTIVITY_DETAIL_FIELD em home/route.ts),
    // pedido do usuário ("tem que especificar qual é a operação assim como
    // a plataforma"). Pode faltar em registros antigos sem esse campo.
    val detail: String? = null,
    val type: String, // "criado" | "atualizado"
    val at: String,
)

@Serializable
data class HomeData(
    val orgName: String,
    val saldoFinanceiroAberto: Double,
    val itensEstoque: Int,
    val safrasAtivas: Int,
    val colaboradoresAtivos: Int,
    val culturaLider: String? = null,
    val pedidosAtrasados: Int,
    val alerts: List<AlertData> = emptyList(),
    val notices: List<NoticeData> = emptyList(),
    val recentActivity: List<ActivityEventData> = emptyList(),
)

@Serializable
data class HomeResponse(
    val ok: Boolean,
    val home: HomeData? = null,
    val error: String? = null,
)

// Réplica mobile do bloco "Gráficos" (ver ModuleChartsPanel/
// module-extra-charts.ts) -- POST /api/mobile/module-charts no site. "extras"
// fica como JSON bruto (kind "bar" | "table", formatos diferentes por
// domínio) e é interpretado em ChartModels.kt, mesmo motivo de
// AnalisesResponse.analises (JsonObject) acima: um domínio novo com um
// formato de gráfico novo não quebra a serialização aqui.
@Serializable
data class ModuleChartsRequest(val accessToken: String, val refreshToken: String, val domainId: String, val safra: String? = null)

@Serializable
data class GenericChartPoint(val name: String, val value: Double)

@Serializable
data class GenericChartData(
    val title: String,
    val isMoney: Boolean,
    val data: List<GenericChartPoint> = emptyList(),
    val safrasDisponiveis: List<String> = emptyList(),
)

@Serializable
data class ModuleChartsResponse(
    val ok: Boolean,
    val generic: GenericChartData? = null,
    val extras: List<JsonObject> = emptyList(),
    val error: String? = null,
)

// Réplica mobile dos botões "Recalcular Vencimentos" (Financeiro),
// "Recalcular Área" (Safra/Frota) e do gráfico "Eficiência de Frota" -- POST
// /api/mobile/module-actions no site. "result" varia por action (recalcular
// devolve um resumo de quantos registros mudaram; fleet-efficiency devolve
// {mediaGeral, maquinas[]}) -- fica como JsonObject bruto, interpretado só
// onde faz sentido (ver FleetEfficiencyCard.kt).
@Serializable
data class ModuleActionRequest(val accessToken: String, val refreshToken: String, val action: String)

@Serializable
data class ModuleActionResponse(
    val ok: Boolean,
    val result: JsonObject? = null,
    val error: String? = null,
)

// Réplica mobile do sino de notificações (topbar), do botão "Backup" e do
// mural de avisos com adição/remoção -- POST /api/mobile/notifications,
// /api/mobile/backup e /api/mobile/notices no site. "result"/"backup" ficam
// como JsonElement bruto (às vezes é um objeto, às vezes uma lista, conforme
// a action) igual ao ModuleActionResponse acima, interpretado só na tela.
@Serializable
data class NotificationItemData(
    val id: String,
    val tipo: String,
    val titulo: String,
    val mensagem: String,
    val link: String? = null,
    val lida: Boolean,
    val criadaEm: String,
)

@Serializable
data class NotificationsRequest(val accessToken: String, val refreshToken: String, val action: String)

@Serializable
data class NotificationsResponse(val ok: Boolean, val result: JsonElement? = null, val error: String? = null)

@Serializable
data class BackupRequest(val accessToken: String, val refreshToken: String)

@Serializable
data class BackupResponse(val ok: Boolean, val backup: JsonElement? = null, val error: String? = null)

@Serializable
data class NoticesRequest(
    val accessToken: String,
    val refreshToken: String,
    val action: String,
    val id: String? = null,
    val titulo: String? = null,
    val mensagem: String? = null,
    val expiraEm: String? = null,
    val fixado: Boolean? = null,
)

@Serializable
data class NoticesResponse(val ok: Boolean, val result: JsonElement? = null, val error: String? = null)

// Fase 2 (Task #32/#33): DRE consolidado + arvore de custos -- ver POST
// /api/mobile/dre no site (src/app/api/mobile/dre/route.ts), que so
// serializa o retorno de getDreConsolidado() + getDreArvoresPorFazendas()
// (lib/services/dre.ts) -- MESMO calculo de rateio/custo por ha/sc/arvore
// que a pagina /dre do site usa, nada refeito em Kotlin. Nao inclui a
// composicao por categoria (grafico de pizza da pagina web).
@Serializable
data class DreRequest(
    val accessToken: String,
    val refreshToken: String,
    val safra: String? = null,
    val cultura: String? = null,
)

@Serializable
data class DreFazendaData(
    val farmId: String,
    val farmName: String,
    val areaHa: Double,
    val custoTotal: Double,
    val custoPorHa: Double,
    val receitaTotal: Double,
    val margem: Double,
    val margemPorHa: Double,
    val totalSacas: Double? = null,
    val custoPorSc: Double? = null,
)

@Serializable
data class DreNaoAlocado(val custoTotal: Double, val receitaTotal: Double)

@Serializable
data class DreTotais(
    val areaHa: Double,
    val custoTotal: Double,
    val custoPorHa: Double,
    val receitaTotal: Double,
    val margem: Double,
    val totalSacas: Double,
    val custoPorSc: Double? = null,
)

// Espelho de DreRamoItem (lib/services/dre.ts) -- recursivo (cada no pode
// ter "filhos", ex.: Safra -> talhao -> item de insumo). "status" so vem
// preenchido nos nos de talhao (dentro do ramo "Safra"): "ACIMA" (estourou
// orcamento) ou "DENTRO".
@Serializable
data class DreRamoItemData(
    val label: String,
    val valor: Double,
    val filhos: List<DreRamoItemData>? = null,
    val status: String? = null,
)

/** Espelho do retorno de getDreComposicaoPorCategoria (dre.ts) -- custo
 * total (todas as fazendas juntas) por categoria de lancamento, ja
 * ordenado do maior pro menor valor. */
@Serializable
data class DreCategoriaData(val categoria: String, val valor: Double)

@Serializable
data class DreData(
    val safrasDisponiveis: List<String> = emptyList(),
    val culturasDisponiveis: List<String> = emptyList(),
    val porFazenda: List<DreFazendaData> = emptyList(),
    val naoAlocado: DreNaoAlocado,
    val totais: DreTotais,
    /** Chave = farmId (ver DreFazendaData.farmId) -- arvore de custos
     * (Financeiro por categoria, Frota por maquina, Safra por talhao/item)
     * de cada fazenda, exibida ao expandir o card dela na tela. */
    val arvores: Map<String, List<DreRamoItemData>> = emptyMap(),
    val composicaoPorCategoria: List<DreCategoriaData> = emptyList(),
)

@Serializable
data class DreResponse(
    val ok: Boolean,
    val dre: DreData? = null,
    val error: String? = null,
)

// Fase 2 (Task #35): Clima/Cambio/Cotacoes ao vivo -- ver GET
// /api/mobile/weather no site (src/app/api/mobile/weather/route.ts), que
// so serializa getWeather()/getFxRates()/getCommodityQuotes()
// (lib/services/weather.ts, quotes.ts). Rota publica, sem token (dado nao
// e especifico de organizacao) -- unica das rotas /api/mobile/* sem
// accessToken/refreshToken no corpo.
@Serializable
data class WeatherForecastDay(val date: String, val maxC: Double, val minC: Double, val icon: String, val precipMm: Double)

@Serializable
data class WeatherData(
    val currentTempC: Double,
    val currentIcon: String,
    val todayMaxC: Double,
    val todayMinC: Double,
    val todayPrecipMm: Double,
    val forecast: List<WeatherForecastDay> = emptyList(),
)

@Serializable
data class FxRatesData(val usdBrl: Double? = null, val eurBrl: Double? = null, val arsBrl: Double? = null)

@Serializable
data class CommodityQuoteData(
    val nome: String,
    val unidade: String,
    val praca: String,
    val valor: Double,
    val variacaoPct: Double,
    val atualizadoEm: String? = null,
)

@Serializable
data class CommodityQuotesData(
    val soja: CommodityQuoteData? = null,
    val milho: CommodityQuoteData? = null,
    val sorgo: CommodityQuoteData? = null,
)

@Serializable
data class WeatherResponse(
    val ok: Boolean,
    val weather: WeatherData? = null,
    val fx: FxRatesData? = null,
    val commodities: CommodityQuotesData? = null,
    val error: String? = null,
)

// Fase 2 (Task #36): Analises cruzadas entre modulos -- ver POST
// /api/mobile/analises no site (src/app/api/mobile/analises/route.ts), que
// so serializa getAnalisesCruzadas() (lib/services/analises.ts, 15
// cruzamentos: Planejado x Realizado x Pago, Custo/ha por fonte, Pedido x
// Recebimento, Clima x Produtividade etc.). "analises" fica como JsonObject
// bruto (mesmo motivo de RecordsResponse.records abaixo: 15 formatos
// diferentes de linha, um por analise) -- AnalisesScreen.kt renderiza cada
// chave do objeto de forma generica (lista de cards com os campos brutos)
// em vez de modelar 15 data classes que uma analise nova deixaria
// desatualizadas.
@Serializable
data class AnalisesRequest(val accessToken: String, val refreshToken: String, val safra: String? = null)

@Serializable
data class AnalisesResponse(
    val ok: Boolean,
    val analises: JsonObject? = null,
    val safrasDisponiveis: List<String> = emptyList(),
    val error: String? = null,
)

// Fase 2 (Task #40): Importacao de XML de NF-e -- ver POST
// /api/mobile/nfe-preview e /api/mobile/nfe-import no site
// (src/app/api/mobile/nfe-{preview,import}/route.ts), que chamam DIRETO
// previewXmlAction()/confirmXmlImportAction() (src/app/(app)/nfe/actions.ts)
// -- as MESMAS Server Actions que a tela web usa (parser fast-xml-parser,
// classificacao automatica de categoria de Estoque, geracao de
// Estoque/Financeiro com rateio). O app so le o arquivo XML escolhido pelo
// usuario (Storage Access Framework) como texto puro e manda pra ca --
// nenhum parsing de NF-e nem calculo de rateio refeito em Kotlin.
@Serializable
data class NfeItemData(
    val descricao: String,
    val quantidade: Double,
    val unidade: String,
    val valorUnitario: Double,
    val valorTotal: Double,
    val categoriaSugerida: String? = null,
)

@Serializable
data class NfePreviewData(
    val chaveAcesso: String? = null,
    val numero: String,
    val serie: String,
    val dataEmissao: String? = null,
    val emitenteNome: String,
    val emitenteDocumento: String,
    val destinatarioNome: String,
    val valorTotal: Double,
    val items: List<NfeItemData> = emptyList(),
)

@Serializable
data class NfePreviewRequest(val accessToken: String, val refreshToken: String, val xmlRaw: String)

@Serializable
data class NfePreviewResponse(val ok: Boolean, val preview: NfePreviewData? = null, val error: String? = null)

@Serializable
data class NfeImportRequest(val accessToken: String, val refreshToken: String, val xmlRaw: String, val fazendaDestino: String)

/** So os campos que a tela de confirmacao precisa mostrar -- a rota devolve
 * a Invoice inteira (com itens aninhados etc.), mas ignoreUnknownKeys=true
 * (ver NetworkModule) descarta o resto sem erro. */
@Serializable
data class NfeImportedInvoiceData(
    val id: String,
    val numero: String,
    val serie: String? = null,
    val emitenteNome: String? = null,
    val valorTotal: Double? = null,
)

@Serializable
data class NfeImportResponse(val ok: Boolean, val invoice: NfeImportedInvoiceData? = null, val error: String? = null)

@Serializable
data class RecordsRequest(val accessToken: String, val refreshToken: String, val domainId: String)

// Cada registro que a rota devolve tem colunas diferentes conforme o modulo
// (ver ColumnConfig acima) e tipos mistos (string/numero/booleano/data) --
// JsonObject guarda o JSON bruto de cada registro sem precisar de um schema
// fixo aqui; RecordRepository.kt converte campo a campo pro tipo certo na
// hora de gravar no Room (fieldsJson) usando o ColumnConfig do modulo pra
// saber como formatar cada um (ver toFieldsJson()).
@Serializable
data class RecordsResponse(
    val ok: Boolean,
    val records: List<JsonObject> = emptyList(),
    val error: String? = null,
)

@Serializable
data class SyncRequest(
    val accessToken: String,
    val refreshToken: String,
    val domainId: String,
    val kind: String, // "create" | "update"
    val recordId: String? = null,
    val fields: Map<String, String>,
)

@Serializable
data class SyncResponse(val ok: Boolean, val error: String? = null)

// -- Supabase Auth REST (login direto, sem passar pelo site) --

@Serializable
data class SupabaseLoginRequest(val email: String, val password: String)

@Serializable
data class SupabaseLoginResponse(
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("error_description") val errorDescription: String? = null,
    val msg: String? = null,
)

// -- Extrato bancário (aba "Extrato" dentro de Financeiro, ver
// bank-import-panel.tsx) -- o parsing do CSV roda no aparelho
// (BankImportParser.kt); o servidor só cobre dedup + gravação. --

@Serializable
data class BankImportRowDto(val data: String, val descricao: String, val valor: Double)

@Serializable
data class BankImportSignaturesRequest(val accessToken: String, val refreshToken: String, val action: String = "signatures", val banco: String)

@Serializable
data class BankImportSignaturesResponse(val ok: Boolean, val signatures: List<String> = emptyList(), val error: String? = null)

@Serializable
data class BankImportConfirmRequest(
    val accessToken: String,
    val refreshToken: String,
    val action: String = "confirm",
    val banco: String,
    val rows: List<BankImportRowDto>,
)

@Serializable
data class BankImportConfirmResponse(val ok: Boolean, val imported: Int = 0, val error: String? = null)
