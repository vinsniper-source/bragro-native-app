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
    // Torna a lista de opcoes deste campo dependente do valor atual de outro
    // campo (irmao, mesmo dominio) -- ver comentario equivalente em
    // types.ts (ColumnConfig). `dependsOn` e a `key` do campo irmao;
    // `lookupCategoryByValue` mapeia o valor bruto desse campo irmao pra
    // qual lookupCategory usar neste campo (cai em `lookupCategory` normal
    // se o irmao nao tem valor ainda ou o valor nao esta no mapa).
    val dependsOn: String? = null,
    val lookupCategoryByValue: Map<String, String>? = null,
    val staticOptions: List<String>? = null,
    val hideInTable: Boolean = false,
    val hint: String? = null,
    // Espelha defaultChecked de types.ts -- pedido do usuário ("erro 400 no
    // offline-sync mobile"): campos checkbox com defaultChecked=true (ex.:
    // "incluirDeclaracao" do Financeiro/Livro Caixa) devem nascer marcados
    // num registro NOVO, igual ao formulário web (record-form.tsx). Sem
    // isso o app não tinha como saber e sempre nascia desmarcado (ver
    // DomainFormScreen.kt).
    val defaultChecked: Boolean = false,
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
// "id" default "" -- hoje /api/mobile/bootstrap ainda NÃO devolve o id real
// da Farm (só name/areaHa, ver bootstrap/route.ts), então esse campo chega
// vazio na prática; deixado aqui pronto pra quando o backend passar a
// mandar (ignoreUnknownKeys já cobre o caso de o campo não vir na resposta).
data class FarmItem(val name: String, val areaHa: Double, val id: String = "")

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
data class HomeRequest(
    val accessToken: String,
    val refreshToken: String,
    // Filtros do Canvas -- pedido do usuário ("implemente nessa sequência
    // no app nativo... substitua os ícones fazenda, safra e cultura por
    // esses filtros da plataforma"), mesmos parâmetros que o dashboard web
    // manda via searchParams (ver dashboard/page.tsx). janela default 60
    // (mesmo default do site). fazendaSelecionada é o NOME (não id) da
    // fazenda escolhida no filtro global (FarmSelection).
    val janela: Int = 60,
    val safra: String? = null,
    val cultura: String? = null,
    val fazendaSelecionada: String? = null,
)

@Serializable
data class CanvasBreakdownItemData(val categoria: String, val valor: Double)

@Serializable
data class CanvasFazendaCardData(
    val id: String,
    val nome: String,
    val areaHa: Double,
    // Área de fato coberta pelos lançamentos de Safra que batem no filtro
    // de safra/cultura selecionado -- espelho exato de areaFiltroHa em
    // lib/services/canvas.ts (site). null = sem filtro de safra/cultura
    // ativo (ou sem lançamento com hectare preenchido ainda), o círculo cai
    // de volta pra areaHa (área cadastral da fazenda inteira).
    val areaFiltroHa: Double? = null,
    val status: String, // "ok" | "alerta" | "risco" | "semdado"
    val variacaoMedia: Double? = null,
    val culturaAtual: String? = null,
    val custoHaMedio: Double? = null,
    val breakdown: List<CanvasBreakdownItemData> = emptyList(),
    val tendencia: String? = null, // "melhorando" | "piorando" | "estavel" | null
)

@Serializable
data class CanvasSectionData(
    val estagio: String, // "plantio" | "vegetativo" | "colheita" | "indefinido"
    val fazendas: List<CanvasFazendaCardData> = emptyList(),
)

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
    // Operação/Item/Status do evento -- ver ACTIVITY_OPERACAO_FIELD/
    // ACTIVITY_ITEM_FIELD/ACTIVITY_STATUS_FIELD em home/route.ts. Nem toda
    // tabela tem os 3 campos (ex.: Romaneios não tem operacao nem status),
    // por isso todos são opcionais -- pedido do usuário ("monitor coloque
    // nessa sequência: setor, operação, tipo, item, quantidade, status,
    // horas").
    val operacao: String? = null,
    val item: String? = null,
    val status: String? = null,
    // Quantidade representativa do evento (ex.: "12 SC", "450 kg"). Nem
    // toda tabela tem uma quantidade natural (ex.: Financeiro), por isso é
    // opcional.
    val qtde: String? = null,
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
    // Novos contadores do Início (KPI "Fazendas cadastradas") -- espelham
    // numeroFazendas/areaTotalHa retornados por /api/mobile/home e
    // /api/mobile/dashboard (ver getDashboardStats no backend). Default 0
    // evita quebrar a leitura do cache offline salvo antes desses campos
    // existirem.
    val numeroFazendas: Int = 0,
    val areaTotalHa: Double = 0.0,
    val alerts: List<AlertData> = emptyList(),
    val notices: List<NoticeData> = emptyList(),
    val recentActivity: List<ActivityEventData> = emptyList(),
    // Canvas da fazenda + listas dos seletores de Safra/Cultura -- pedido do
    // usuário ("implemente nessa sequência no app nativo... substitua os
    // ícones fazenda, safra e cultura por esses filtros da plataforma").
    // Default null/vazio não quebra a leitura do cache offline salvo antes
    // desses campos existirem (mesmo motivo do default 0 em numeroFazendas
    // acima).
    val canvas: CanvasSectionData? = null,
    val safrasDisponiveis: List<String> = emptyList(),
    val culturasDisponiveis: List<String> = emptyList(),
    // Blocos da Início liberados pra este membro -- pedido do usuário
    // ("crie em acessos uma categoria da aba início... site e app nativo
    // juntos"), ver allowedInicioWidgets no backend (lib/permissions.ts).
    // Default null (não lista vazia!) distingue "backend antigo que ainda
    // não manda esse campo" (cache offline salvo antes dele existir) de
    // "admin restringiu e não sobrou nenhum bloco" -- null = mostra tudo
    // (mesmo critério do backend), lista vazia de verdade = esconde tudo.
    val allowedWidgets: List<String>? = null,
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
data class ModuleActionRequest(
    val accessToken: String,
    val refreshToken: String,
    val action: String,
    // Campos extras só usados pelas actions "estoque-transferir"/
    // "estoque-devolver"/"estoque-saida"/"estoque-ajuste" (Controle de
    // Estoque por Fazenda) -- pedido do usuário ("como faço pra fazer esse
    // controle interligando com a tabela estoque"... "precisamos tambem
    // colocar saída/devolução e... ajuste manual"). Ficam null/omitidos nas
    // demais actions.
    val item: String? = null,
    val unidade: String? = null,
    val quantidade: Double? = null,
    val fazendaOrigemId: String? = null,
    val fazendaDestinoId: String? = null,
    val transferenciaEntradaId: String? = null,
    // "estoque-saida"/"estoque-ajuste" reaproveitam fazendaOrigemId como a
    // fazenda única desses dois (rótulo "Origem" no app). "tipo" só existe
    // no ajuste ("ENTRADA" | "SAIDA"); "motivo" é obrigatório no ajuste e
    // opcional na saída. Nulos por padrão -- não quebram nenhuma action
    // antiga que não os envia (compatibilidade com o backend já em
    // produção, que ignora campos extras que não usa).
    val motivo: String? = null,
    val tipo: String? = null,
    // Só usado por "emitir-nfse" (varredura de auditoria, pedido do usuário
    // "implemente tudo") -- id do registro Nfse a emitir. Réplica mobile do
    // botão "Emitir NFS-e" da tabela genérica do site (data-table.tsx,
    // domain.id === "nfse").
    val id: String? = null,
)

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

// "Ponte" pro botão Módulos (Configurações/Base de Dados/Acessos) abrir o
// navegador do aparelho já autenticado -- ver BridgeRepository.kt e
// /api/mobile/bridge-code + /api/mobile/bridge no backend.
@Serializable
data class BridgeCodeRequest(val accessToken: String, val refreshToken: String)

@Serializable
data class BridgeCodeResponse(val ok: Boolean, val code: String? = null, val error: String? = null)

// Persistir a URL da logo apos o upload direto pro Storage (ver
// LogoUploadRepository.kt) -- pedido do usuario ("quando clicar em
// adicionar a logo, coloque quando clicar a adicionar a logo por la
// memo"), equivalente mobile de updateLogoAction no site.
@Serializable
data class UpdateLogoRequest(val accessToken: String, val refreshToken: String, val logoUrl: String)

@Serializable
data class UpdateLogoResponse(val ok: Boolean, val error: String? = null)

// Mesma ideia da logo, só que pra foto de perfil do usuário -- pedido do
// usuário ("coloque também no ícone usuário a opção de inserir foto").
@Serializable
data class UpdateAvatarRequest(val accessToken: String, val refreshToken: String, val avatarUrl: String)

@Serializable
data class UpdateAvatarResponse(val ok: Boolean, val error: String? = null)

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

// As 3 telas administrativas nativas (Configurações/Base de Dados/Acessos,
// Task #148) -- pedido explícito e repetido do usuário ("não use nada para
// redirecionar, quero ele fixo nesse app"), substituindo o auth-bridge/
// Custom Tabs. Mesmo padrão de "action" genérico de NoticesRequest/
// NotificationsRequest: um request/response por tela, o `result` chega como
// JsonElement cru e cada ViewModel re-parseia como JsonObject/JsonArray
// (mesmo critério de AnalisesScreen.kt), evitando modelar ~15 DTOs
// tipados só pra telas de administração usadas raramente.
@Serializable
data class SettingsRequest(
    val accessToken: String,
    val refreshToken: String,
    val action: String,
    val name: String? = null,
    val toleranciaPct: Double? = null,
    val notifTelegramBotToken: String? = null,
    val notifTelegramChatId: String? = null,
    val notifWhatsappPhoneId: String? = null,
    val notifWhatsappToken: String? = null,
    val notifWhatsappTo: String? = null,
    val notifChannelPush: Boolean? = null,
    val notifChannelTelegram: Boolean? = null,
    val notifChannelWhatsapp: Boolean? = null,
    val notifFrotaManutencao: Boolean? = null,
    val notifRomaneioDiario: Boolean? = null,
    val notifEstoqueMinimo: Boolean? = null,
    val channel: String? = null,
    val plano: String? = null,
)

@Serializable
data class SettingsResponse(val ok: Boolean, val result: JsonElement? = null, val error: String? = null)

@Serializable
data class BaseDeDadosRequest(
    val accessToken: String,
    val refreshToken: String,
    val action: String,
    val category: String? = null,
    val value: String? = null,
    val id: String? = null,
    val ativo: Boolean? = null,
    val name: String? = null,
    val areaHa: Double? = null,
    // Área "safrinha" -- exceção de schema autorizada (ver MEMORY.md),
    // opcional. null = campo omitido do JSON (encodeDefaults=false), então
    // o backend não mexe no valor já salvo (ver route.ts "areaSafrinhaData").
    val areaSafrinhaHa: Double? = null,
    // Área safrinha POR CULTURA (5ª exceção de schema, ver MEMORY.md) --
    // mesmo padrão de areaSafrinhaHa acima (omitido = não mexe).
    val areaSafrinhaMilhoHa: Double? = null,
    val areaSafrinhaSorgoHa: Double? = null,
    // Localização real (6ª exceção de schema, ver MEMORY.md) -- mesmo
    // padrão partial-update: omitido = não mexe. Usada por
    // resolveFarmCoords() (lib/services/weather.ts, site) pra trocar o
    // fallback fixo de clima (Tupaciguara/MG) pela localização real.
    val latitude: Double? = null,
    val longitude: Double? = null,
)

@Serializable
data class BaseDeDadosResponse(val ok: Boolean, val result: JsonElement? = null, val error: String? = null)

@Serializable
data class SecurityRequest(
    val accessToken: String,
    val refreshToken: String,
    val action: String,
    val email: String? = null,
    val role: String? = null,
    val modulosPermitidos: List<String>? = null,
    val membershipId: String? = null,
    val ativo: Boolean? = null,
)

@Serializable
data class SecurityResponse(val ok: Boolean, val result: JsonElement? = null, val error: String? = null)

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

// Livro Caixa do Produtor Rural (Task #58) -- ver POST /api/mobile/livro-caixa
// no site (src/app/api/mobile/livro-caixa/route.ts), que so serializa o
// retorno de getLivroCaixaData() (lib/services/livro-caixa.ts) -- MESMO
// motor de classificacao (regime de caixa, entrada/saida, resumo por conta
// e por mes) que a pagina /livro-caixa do site usa, nada refeito em Kotlin.
@Serializable
data class LivroCaixaRequest(
    val accessToken: String,
    val refreshToken: String,
    val ano: Int? = null,
    val saldoInicial: Double? = null,
    val banco: String? = null,
    // imovel -- pedido do usuario ("implemente tudo que falta ainda para o
    // app native da plataforma"): filtro por imovel rural (COD_IMOVEL do
    // LCDPR), ja existia no site (livro-caixa-client.tsx) e no motor
    // (getLivroCaixaData) mas faltava na rota/modelo mobile.
    val imovel: String? = null,
)

@Serializable
data class LivroCaixaLancamentoData(
    val id: String,
    val data: String,
    val historico: String,
    val operacao: String,
    val tipoDocumento: String? = null,
    val banco: String? = null,
    // imovel -- COD_IMOVEL do LCDPR (nome da fazenda vinculada, com
    // fallback pro campo texto "local"). Faltava no modelo mobile (gap
    // real, ver relatorio de auditoria).
    val imovel: String = "",
    val entrada: Double,
    val saida: Double,
    val saldo: Double,
)

@Serializable
data class LivroCaixaMesData(
    val mes: String,
    val label: String,
    val entradas: Double,
    val saidas: Double,
    val saldoFinal: Double,
)

@Serializable
data class LivroCaixaContaResumoData(
    val banco: String,
    val quantidade: Int,
    val entradas: Double,
    val saidas: Double,
    val saldo: Double,
)

// Resumo por imovel -- mesmo criterio do resumo por conta, agrupado pelo
// imovel rural. Espelha LivroCaixaImovelResumo em livro-caixa.ts (site).
@Serializable
data class LivroCaixaImovelResumoData(
    val imovel: String,
    val quantidade: Int,
    val entradas: Double,
    val saidas: Double,
    val saldo: Double,
)

@Serializable
data class LivroCaixaData(
    val ano: Int,
    val saldoInicial: Double,
    val bancoFiltro: String? = null,
    val imovelFiltro: String? = null,
    val lancamentos: List<LivroCaixaLancamentoData> = emptyList(),
    val porMes: List<LivroCaixaMesData> = emptyList(),
    val contas: List<LivroCaixaContaResumoData> = emptyList(),
    val imoveis: List<LivroCaixaImovelResumoData> = emptyList(),
    val totalEntradas: Double,
    val totalSaidas: Double,
    val saldoFinal: Double,
)

@Serializable
data class LivroCaixaResponse(
    val ok: Boolean,
    val resultado: LivroCaixaData? = null,
    val error: String? = null,
)

// Config Produtor Rural / IRPF (Organization.cnpj/cpfProdutorRural/
// inscricaoEstadualProdutor/certificadoDigitalRef/contaIrpfPadrao) -- pedido
// do usuario ("implemente tudo que falta ainda para o app native da
// plataforma"): esse card ja existia no site (produtor-rural-card.tsx) e no
// schema (excecao ja autorizada, ver memoria "Livro Caixa schema
// exception"), mas nao tinha rota nem modelo mobile ainda -- gap real.
@Serializable
data class ProdutorRuralConfigData(
    val cnpj: String = "",
    val cpfProdutorRural: String = "",
    val inscricaoEstadualProdutor: String = "",
    val certificadoDigitalRef: String = "",
    val contaIrpfPadrao: String = "",
)

@Serializable
data class ProdutorRuralRequest(
    val accessToken: String,
    val refreshToken: String,
    val save: ProdutorRuralConfigData? = null,
)

@Serializable
data class ProdutorRuralResponse(
    val ok: Boolean,
    val config: ProdutorRuralConfigData? = null,
    val error: String? = null,
)

// Nota digitada manualmente com mais de um item no mesmo lançamento -- gap
// encontrado na auditoria módulo-a-módulo (Financeiro, botão "Lançar nota
// com itens", ver nota-multi-item-button.tsx no site). Chama DIRETO
// criarNotaComItensAction() via /api/mobile/nota-multi-item (mesmo motor de
// baixa em Estoque + geração de Financeiro que o XML já usa).
//
// "valorUnitario" por item SAIU -- pedido do usuário no site (sétima
// rodada, ver comentário em nota-multi-item-button.tsx): a nota inteira
// usa só 1 total ("bruto" em NotaMultiItemRequest abaixo), distribuído por
// item proporcional à quantidade só no servidor. /api/mobile/nota-multi-item
// ainda aceita o payload ANTIGO (item.valorUnitario) de APKs já instalados
// -- não quebra quem não atualizou -- mas esta tela (embutida no Novo
// Lançamento, ver FinanceiroItensInlineSection em DomainFormScreen.kt) já
// manda o payload NOVO.
@Serializable
data class NotaMultiItemItemData(
    val descricao: String = "",
    val quantidade: Double = 0.0,
    val unidade: String? = null,
)

@Serializable
data class NotaMultiItemRequest(
    val accessToken: String,
    val refreshToken: String,
    val numero: String,
    val serie: String? = null,
    val emitenteNome: String,
    val dataEmissao: String? = null,
    val fazendaDestino: String,
    // Campos "unificados" com o formulário genérico (mesmo critério do
    // site, ver "Quinta rodada" em nota-multi-item-button.tsx) -- lidos AO
    // VIVO do DomainFormScreen.kt em vez de duplicar campo próprio nesta
    // tela.
    val periodo: String? = null,
    val safra: String? = null,
    val cultura: String? = null,
    val setor: String? = null,
    val banco: String? = null,
    val formaPgto: String? = null,
    // Total da nota inteira (substituiu valorUnitario por item, ver
    // comentário acima de NotaMultiItemItemData).
    val bruto: Double,
    val itens: List<NotaMultiItemItemData>,
)

@Serializable
data class NotaMultiItemResponse(
    val ok: Boolean,
    val invoiceId: String? = null,
    val itensCount: Int? = null,
    val valorTotal: Double? = null,
    val error: String? = null,
)

// Pedidos/Cotações: "novo modelo" de vários itens no mesmo lançamento --
// pedido do usuário ("insira o novo modelo dos modulos cotaçoes e pedidos no
// app native"), réplica de pedido-multi-item-button.tsx/
// cotacao-multi-item-button.tsx no site (tasks #234/#235). Mesmo critério do
// NotaMultiItem* acima: chama DIRETO createPedidoMultiItemAction()/
// createCotacaoMultiItemAction() via /api/mobile/pedido-multi-item e
// /api/mobile/cotacao-multi-item -- nenhuma lógica de negócio duplicada em
// Kotlin (Saldo/%/Status cumulativos, baixa em Estoque, Índice de Vantagem
// continuam calculados só no servidor).
@Serializable
data class PedidoMultiItemItemData(
    val categoria: String? = null,
    val item: String = "",
    val unidade: String? = null,
    val qtdPedida: Double = 0.0,
    val qtdEntregue: Double? = null,
)

@Serializable
data class PedidoMultiItemRequest(
    val accessToken: String,
    val refreshToken: String,
    val noPedido: String,
    val setor: String? = null,
    val fornecedor: String? = null,
    val safra: String? = null,
    val cultura: String? = null,
    val dataEntrega: String? = null,
    val nf: String? = null,
    val itens: List<PedidoMultiItemItemData>,
)

@Serializable
data class PedidoMultiItemResponse(
    val ok: Boolean,
    val count: Int? = null,
    val error: String? = null,
)

@Serializable
data class CotacaoMultiItemItemData(
    val categoria: String = "",
    val item: String = "",
    val quantidade: Double? = null,
    val unidade: String? = null,
    val precoUnitario: Double = 0.0,
    val prazoEntregaDias: Double? = null,
)

@Serializable
data class CotacaoMultiItemRequest(
    val accessToken: String,
    val refreshToken: String,
    val data: String,
    val fornecedor: String,
    val condicaoPagamento: String? = null,
    val validadeProposta: String? = null,
    val observacoes: String? = null,
    val itens: List<CotacaoMultiItemItemData>,
)

@Serializable
data class CotacaoMultiItemResponse(
    val ok: Boolean,
    val count: Int? = null,
    val error: String? = null,
)

// Painel "Controle de Insumos" (gap encontrado na auditoria módulo-a-módulo
// contra o site, pedido do usuário "implemente tudo que falta ainda para o
// app native da plataforma") -- painel SOMENTE-LEITURA, ver
// /api/mobile/controle-insumos (route.ts), que so serializa o retorno das
// MESMAS funções que src/app/(app)/controle-de-insumos/page.tsx usa
// (lib/services/insumos-arvore.ts). Espelha InsumoSaldo/InsumoItemSituacao/
// InsumosSituacaoConsolidada/InsumosRamoItem.
@Serializable
data class InsumoSaldoData(
    val item: String,
    val saldo: Double,
    val minimo: Double,
    val status: String, // "OK" | "ATENCAO" | "CRITICO"
    val consumoMedioDiario: Double,
    val diasRestantes: Int? = null,
)

@Serializable
data class InsumoItemSituacaoData(
    val item: String,
    val categoria: String, // "SAFRA" | "FROTA" | "ADM"
    val unidade: String? = null,
    val qtdPedida: Double,
    val entregue: Double,
    val aReceber: Double,
    val emEstoque: Double,
    val aplicadoSafra: Double,
    val aplicadoFrota: Double,
    val aplicadoAdm: Double,
    val totalAplicado: Double,
    val percentualAplicado: Double? = null,
    val status: String, // "FALTA" | "EM_USO" | "EM_TRANSITO" | "OK"
)

@Serializable
data class InsumosSituacaoConsolidadaData(
    val itensUnicos: Int,
    val totalPedido: Double,
    val totalEmEstoque: Double,
    val totalAplicado: Double,
    val itensAtencao: Int,
    val porCategoria: Map<String, List<InsumoItemSituacaoData>> = emptyMap(),
)

// Recursivo (cada nó pode ter "filhos", ex.: Categoria -> item), mesmo
// padrão de DreRamoItemData.
@Serializable
data class InsumosRamoItemData(
    val label: String,
    val qtd: Double,
    val unidade: String? = null,
    val filhos: List<InsumosRamoItemData>? = null,
    val status: String? = null,
)

@Serializable
data class InsumoSaldoTopData(val item: String, val saldo: Double)

@Serializable
data class ControleInsumosRequest(
    val accessToken: String,
    val refreshToken: String,
    val safra: String? = null,
)

@Serializable
data class ControleInsumosResponse(
    val ok: Boolean,
    val situacaoConsolidada: InsumosSituacaoConsolidadaData? = null,
    val itensCriticos: List<InsumoSaldoData> = emptyList(),
    val arvoreControleInterno: List<InsumosRamoItemData> = emptyList(),
    val saldoTop10: List<InsumoSaldoTopData> = emptyList(),
    val error: String? = null,
)

// Visão "Operação" agrupada (gap encontrado na auditoria módulo-a-módulo
// contra o site, pedido do usuário "implemente tudo que falta ainda para o
// app native da plataforma") -- ver /api/mobile/operacoes (route.ts), que só
// serializa o retorno de getOperacoes() (lib/services/operacoes.ts). Espelha
// OperacaoTimelineItem/OperacaoEstoqueItem/OperacaoFinanceiroItem/
// OperacaoAgrupada.
@Serializable
data class OperacaoTimelineItemData(
    val id: String,
    val data: String,
    val operacao: String,
    val responsavel: String? = null,
    val os: String? = null,
)

@Serializable
data class OperacaoEstoqueItemData(
    val item: String,
    val qtd: Double,
    val unidade: String? = null,
)

@Serializable
data class OperacaoFinanceiroItemData(
    val id: String,
    val data: String,
    val categoria: String,
    val subcategoria: String? = null,
    val entidade: String? = null,
    val valor: Double,
)

@Serializable
data class OperacaoAgrupadaData(
    val chave: String,
    val safra: String,
    val cultura: String,
    val local: String,
    val hectare: Double? = null,
    // Área TOTAL da fazenda (Local) desta operação / % ocupado -- pedido do
    // usuário ("em safra modulo operações crie uma barra de progresso da
    // area total com a areas parcial"). Já vem calculado do servidor
    // (getOperacoes, services/operacoes.ts, mesmo lookup de Farm.areaHa por
    // normalizeName usado em computeSafraFields) -- nunca recalcular aqui.
    // null quando o "local" não bate com nenhuma fazenda cadastrada.
    val areaTotal: Double? = null,
    val areaPct: Int? = null,
    val estagio: String, // "plantio" | "vegetativo" | "colheita" | "indefinido"
    val dataInicio: String,
    val dataFim: String? = null,
    // Progresso OPERACIONAL (O.S. concluídas/totais), não mais tempo
    // decorrido -- varredura de auditoria, pedido do usuário ("a
    // porcentagem está em 650%... a barra continua contando o tempo
    // decorrido mesmo após a conclusão das tarefas"). Já vem calculado do
    // servidor (getOperacoes, services/operacoes.ts) -- nunca recalcular
    // aqui, réplica exata do mesmo raciocínio de "nenhuma lógica de
    // negócio duplicada em Kotlin" usado no resto do app.
    val osTotal: Int = 0,
    val osConcluidas: Int = 0,
    val progressoPct: Int = 0,
    val concluida: Boolean = false,
    val atrasada: Boolean = false,
    val realizadoTotal: Double,
    val planejadoTotal: Double,
    val variacaoMedia: Double? = null,
    val timeline: List<OperacaoTimelineItemData> = emptyList(),
    val financeiroTotal: Double,
    val financeiroDetalhe: List<OperacaoFinanceiroItemData> = emptyList(),
    val estoqueConsumido: List<OperacaoEstoqueItemData> = emptyList(),
)

@Serializable
data class OperacoesRequest(
    val accessToken: String,
    val refreshToken: String,
    val janela: Int? = null,
)

@Serializable
data class OperacoesResponse(
    val ok: Boolean,
    val operacoes: List<OperacaoAgrupadaData> = emptyList(),
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

// usdVariacaoPct/eurVariacaoPct -- pedido do usuário ("kpi câmbio, na linha
// dólar e na linha euro adicione ícone de variação e porcentagem"), mesmo
// campo que já existia só pras cotações agrícolas (variacaoPct em
// CommodityQuoteData abaixo). null quando a fonte não informa variação (ex.:
// fallback exchangerate-api.com, usado só se a AwesomeAPI cair de vez).
@Serializable
data class FxRatesData(
    val usdBrl: Double? = null,
    val eurBrl: Double? = null,
    val arsBrl: Double? = null,
    val usdVariacaoPct: Double? = null,
    val eurVariacaoPct: Double? = null,
)

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
data class AnalisesRequest(
    val accessToken: String,
    val refreshToken: String,
    val safra: String? = null,
    // Filtro de Cultura ao lado do de Safra -- pedido do usuário ("análises
    // coloque filtro cultura"), mesmo padrão já usado no DRE.
    val cultura: String? = null,
)

@Serializable
data class AnalisesResponse(
    val ok: Boolean,
    val analises: JsonObject? = null,
    val safrasDisponiveis: List<String> = emptyList(),
    val culturasDisponiveis: List<String> = emptyList(),
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

// Réplica mobile do módulo Drone (ver /api/mobile/drone/route.ts e
// src/app/(app)/drone/actions.ts no site) -- schema fixo (não passa pelo
// DomainConfig genérico porque Drone não é um domínio do registry.ts, é
// uma tabela própria com upload de arquivo).
@Serializable
data class DroneRecordDto(
    val id: String,
    val data: String,
    val talhao: String? = null,
    val tipoCaptura: String,
    val piloto: String? = null,
    val altitude: Double? = null,
    val areaCoberta: Double? = null,
    val observacoes: String? = null,
    val storagePath: String,
    val publicUrl: String? = null,
    val fileSizeBytes: Long? = null,
    val criadoEm: String,
)

@Serializable
data class DroneListRequest(val accessToken: String, val refreshToken: String, val action: String = "list")

@Serializable
data class DroneListResponse(val ok: Boolean, val records: List<DroneRecordDto> = emptyList(), val error: String? = null)

@Serializable
data class DroneCreateRequest(
    val accessToken: String,
    val refreshToken: String,
    val action: String = "create",
    val data: String,
    val talhao: String? = null,
    val tipoCaptura: String,
    val piloto: String? = null,
    val altitude: Double? = null,
    val areaCoberta: Double? = null,
    val observacoes: String? = null,
    val storagePath: String,
    val publicUrl: String,
    val fileSizeBytes: Long,
)

@Serializable
data class DroneCreateResponse(val ok: Boolean, val record: DroneRecordDto? = null, val error: String? = null)

// Réplica mobile do módulo FieldView -- talhões (com boundary/área
// calculada), status de safra/frota por talhão/máquina, e agora (Task
// #110) o mapa nativo (osmdroid) + importação nativa de KML/KMZ, sem
// depender mais do site pra nenhuma das duas coisas. "geojson" chega cru
// (JsonElement, mesmo critério de outros campos heterogêneos deste
// arquivo) porque é sempre um objeto GeoJSON Polygon padrão
// ({"type":"Polygon","coordinates":[[[lon,lat],...]]}) e a tela só precisa
// reconverter pra GeoPoint pro overlay do mapa, não faz sentido modelar
// uma data class Polygon/MultiPolygon aqui. Nullable com default null pra
// não quebrar a deserialização de respostas antigas (versão anterior do
// backend não mandava esse campo).
@Serializable
data class FieldBoundaryDto(
    val id: String,
    val talhao: String,
    val nome: String? = null,
    val areaHaCalc: Double? = null,
    val geojson: JsonElement? = null,
    // O backend (findMany sem select em /api/mobile/fieldview) já mandava
    // esse campo há tempos -- só nunca tinha sido declarado aqui (era
    // silenciosamente ignorado na deserialização). Precisa dele agora pra
    // montar os links "Abrir no Google Maps/Earth" POR FAZENDA (auditoria de
    // paridade, Task #226: o site já casa FieldBoundary.farmId com Farm.id
    // pra centralizar o mapa no talhão certo de cada fazenda).
    val farmId: String? = null,
)

@Serializable
data class FieldviewRequest(val accessToken: String, val refreshToken: String)

@Serializable
data class FieldviewResponse(
    val ok: Boolean,
    val boundaries: List<FieldBoundaryDto> = emptyList(),
    val talhaoStatus: List<JsonObject> = emptyList(),
    val maquinaStatus: List<JsonObject> = emptyList(),
    val error: String? = null,
)

// Importação nativa de KML/KMZ (Task #110) -- mesma rota
// /api/mobile/fieldview, só que com "action" = "import_boundary" (o campo
// "action" é omitido/ausente nas chamadas antigas de FieldviewRequest
// acima, que o backend continua tratando como o fluxo só-leitura de
// sempre). "talhao" é a chave única de negócio (mesma convenção
// talhao-por-nome usada em SafraRegistro/FrotaRegistro, ver FieldBoundary
// no schema.prisma -- upsert por [orgId, talhao]).
@Serializable
data class FieldviewImportRequest(
    val accessToken: String,
    val refreshToken: String,
    val action: String = "import_boundary",
    val talhao: String,
    val nome: String? = null,
    val geojson: JsonElement,
    val areaHaCalc: Double? = null,
    // Vínculo opcional com o cadastro de Fazendas (Farm) -- mesmo campo novo
    // aceito pelo backend em /api/mobile/fieldview (action=import_boundary),
    // que reaproveita o fluxo web equivalente (fieldview/actions.ts). Null/
    // ausente = não vincula a nenhuma fazenda (comportamento de sempre).
    val farmId: String? = null,
)

@Serializable
data class FieldviewImportResponse(
    val ok: Boolean,
    val boundary: FieldBoundaryDto? = null,
    val error: String? = null,
)

// Card "Acesso automático via prestadora de serviço" (Task #341/#54) --
// réplica mobile do ProviderIntegrationCard do site (ver
// components/domain/provider-integration-card.tsx e
// lib/services/provider-integration.ts). Mesmas 4 ações novas em
// /api/mobile/fieldview E /api/mobile/drone (o módulo é implícito na URL,
// não vai no corpo) -- por isso um único conjunto de request/response serve
// os dois, ver métodos *GetIntegration/*SaveIntegration/etc. em Api.kt e
// ProviderIntegrationRepository.kt.
@Serializable
data class ProviderIntegrationDto(
    val provedor: String? = null,
    val apiKeyConfigurado: Boolean = false,
    // "DESCONECTADO" | "CONECTADO" | "ERRO" -- mesmo enum de status do site.
    val status: String = "DESCONECTADO",
    val ultimaSincronizacaoEm: String? = null,
)

@Serializable
data class GetProviderIntegrationRequest(
    val accessToken: String, val refreshToken: String, val action: String = "get_integration",
)

@Serializable
data class GetProviderIntegrationResponse(
    val ok: Boolean, val integration: ProviderIntegrationDto? = null, val error: String? = null,
)

@Serializable
data class SaveProviderIntegrationRequest(
    val accessToken: String, val refreshToken: String, val action: String = "save_integration",
    val provedor: String, val apiKey: String,
)

@Serializable
data class SaveProviderIntegrationResponse(val ok: Boolean, val error: String? = null)

@Serializable
data class DisconnectProviderIntegrationRequest(
    val accessToken: String, val refreshToken: String, val action: String = "disconnect_integration",
)

@Serializable
data class DisconnectProviderIntegrationResponse(val ok: Boolean, val error: String? = null)

@Serializable
data class SyncProviderIntegrationRequest(
    val accessToken: String, val refreshToken: String, val action: String = "sync_integration",
)

// "ok" aqui reflete o resultado da sincronização em si (hoje sempre false --
// ver comentário de topo em provider-integration.ts: depende de aprovação
// de parceiro do fabricante), não sucesso HTTP -- diferente das outras
// respostas acima. "mensagem" é sempre preenchida pelo backend para exibir
// ao usuário (sucesso ou stub explicando o motivo).
@Serializable
data class SyncProviderIntegrationResponse(
    val ok: Boolean, val mensagem: String? = null, val error: String? = null,
)

// "Editado por" no card de cada lançamento (pedido do usuário: "editado
// por + data/hora dentro do card, via histórico de alterações") -- devolve
// só a última edição de cada recordId pedido, num único lote por tela de
// lista (ver /api/mobile/audit-info/route.ts).
@Serializable
data class AuditInfoRequest(
    val accessToken: String,
    val refreshToken: String,
    val domainId: String,
    val recordIds: List<String>,
)

@Serializable
data class AuditEntry(val userEmail: String, val createdAt: String, val action: String)

@Serializable
data class AuditInfoResponse(
    val ok: Boolean,
    val info: Map<String, AuditEntry> = emptyMap(),
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
    // Task #124 (deteccao de conflito de sync) -- timestamp ISO da ULTIMA
    // edicao conhecida deste registro (RecordEntity.expectedVersion, que
    // por sua vez vem de RecordLastEdit.updatedAt via /api/mobile/audit-info,
    // ver AuditInfoRepository). O backend (/api/offline-sync, ja ajustado)
    // compara com o updatedAt ATUAL de RecordLastEdit: se bater, aplica o
    // update normalmente; se NAO bater (outro aparelho editou o mesmo
    // registro nesse meio tempo), responde 409 com code="CONFLICT" em vez
    // de sobrescrever. Nullable/default null e de proposito fail-open --
    // um "update" sem esse campo (kind="create"/"delete", ou um registro
    // que nunca teve "editado por" carregado na tela) e aplicado sem
    // nenhuma verificacao extra, exatamente como antes desta task.
    val expectedVersion: String? = null,
)

@Serializable
data class SyncResponse(
    val ok: Boolean,
    val error: String? = null,
    // Preenchidos pelo backend SO na resposta 409 (ver comentario em
    // expectedVersion acima) -- na pratica, como o HTTP status nao e 2xx
    // nesse caso, o Retrofit entrega esse corpo em Response.errorBody(),
    // nao em Response.body() (ver RecordRepository.trySyncOne/
    // parseSyncErrorBody); esses 2 campos ficam aqui tambem so por
    // completude/documentacao do contrato, e como rede de seguranca caso
    // o backend um dia responda 200 com ok=false+code (fail-open, nao
    // quebra nada se isso nunca acontecer).
    val code: String? = null,
    val message: String? = null,
)

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
