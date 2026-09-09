package com.bragro.mobile.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Agriculture
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Warehouse
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bragro.mobile.ui.home.domainIcon

// Preto/branco (onSurface) em vez de verde -- pedido do usuário ("vc não
// alterou a cor dos botoes da barra inferiro para branco/preto"), mesmo
// critério já aplicado aos blocos individuais (MODULE_ICON_FG em
// ModuleIconRow.kt) e à lista suspensa desta mesma barra. Reverte o esquema
// anterior (ícone/rótulo = primary/verde). A pílula de fundo do item
// selecionado também deixa de ser verde (onSurface bem translúcido) -- só a
// opacidade menor no item não-selecionado continua distinguindo do
// selecionado.
private val BottomNavColors: androidx.compose.material3.NavigationBarItemColors
    @Composable
    get() = NavigationBarItemDefaults.colors(
        selectedIconColor = MaterialTheme.colorScheme.onSurface,
        selectedTextColor = MaterialTheme.colorScheme.onSurface,
        indicatorColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
        unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
        unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
    )

// Pedido do usuário: "os botões inferiores devem aparecer uma lista
// suspensa quando apertar de todas as operações e atividades de cada setor
// correspondente" -- cada aba deixa de navegar direto pra UM domínio e passa
// a abrir um menu com TODAS as atividades daquele setor (mesmo agrupamento
// de DomainVisuals.kt/modules.ts), inclusive as que antes só apareciam nos
// atalhos da extinta tela "Dashboard" (DRE/Análises/NF-e/Romaneio Rápido,
// ver SectorTarget.Special abaixo) -- o ícone de Dashboard não existe mais.
// "category" agrupa os itens DENTRO do dropdown de um setor -- pedido do
// usuário ("distribua esses módulos por categoria", sobre o dropdown de 11
// itens da aba Safra, que até então era uma lista só, sem nenhuma divisão).
// null (padrão) = sem cabeçalho, mesmo comportamento de antes -- só a aba
// Safra recebe categoria por enquanto (as demais abas com dropdown,
// Financeiro/RH, continuam de fora até um pedido explícito de estendê-las).
private sealed class SectorTarget {
    data class Domain(val domainId: String, val label: String, val category: String? = null) : SectorTarget()

    /** Telas que não são um domínio genérico (DRE/Análises/NF-e/Romaneio
     * Rápido) -- viviam na extinta tela "Dashboard" (atalhos), redistribuídas
     * aqui pro setor a que pertencem (mesmo critério de agrupamento de
     * lib/modules.ts no site: DRE/Análises/NF-e são "financeiro"; Romaneio
     * Rápido é "campo", junto de Romaneios). */
    data class Special(val routeKey: String, val label: String, val category: String? = null) : SectorTarget()
}

// "directDomainId" != null => aba de acesso direto (toque único já navega
// pro domínio, sem dropdown) -- pedido do usuário ("botão frota acesso
// direto, retire as listas suspensas" / "botão estoque... deixe botão
// direto"). Nesses casos "items" fica vazio e não é usado.
private data class BottomTab(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val items: List<SectorTarget> = emptyList(),
    val directDomainId: String? = null,
)

// Antigas abas "Safra"/"Financeiro"/"RH" (cada uma com dropdown agrupado por
// "category") foram ACHATADAS em abas de nível superior -- pedido do
// usuário, mockup por mockup: "não são 5 botões, são 4: lançamentos,
// relatórios, compras, faturamento" / "são 4 botões produção, sanidade,
// monitoramento e painéis" / "são 2 botões RH e Controle Interno". Cada
// antiga CATEGORIA (ou item solto, no caso de RH) virou sua própria
// BottomTab -- por isso nenhum SectorTarget abaixo usa mais "category"
// (fica null/default): o dropdown de cada aba agora é sempre uma lista
// plana (ver renderTabs/RenderTab mais abaixo, que decide entre dropdown e
// acesso direto com base em quantos itens sobram após o filtro de
// permissão).
private val BOTTOM_TABS = listOf(
    // Ex-categoria "Produção" (dentro da antiga aba Safra).
    BottomTab(
        "producao", "Produção", Icons.Filled.Agriculture,
        items = listOf(
            SectorTarget.Domain("safra", "Safra"),
            SectorTarget.Domain("planejamentosafra", "Planejamento de Safra"),
            SectorTarget.Domain("colheita", "Colheita"),
            // "Romaneio rápido" virou um 2º FAB dentro do próprio módulo
            // Romaneios (ver DomainListScreen.kt), pedido do usuário
            // ("coloque romaneio rápido como um botão dentro de romaneio,
            // unifique").
            SectorTarget.Domain("romaneios", "Romaneios"),
        ),
    ),
    // Ex-categoria "Sanidade": pragas/doenças e o receituário que as trata.
    BottomTab(
        "sanidade", "Sanidade", Icons.Filled.BugReport,
        items = listOf(
            SectorTarget.Domain("pragas", "Pragas"),
            SectorTarget.Domain("receituarios", "Receituários"),
        ),
    ),
    // Ex-categoria "Monitoramento": dados de campo captados por fonte
    // externa (clima, imagens de drone, mapa/talhões).
    BottomTab(
        "monitoramento", "Monitoramento", Icons.Filled.WbSunny,
        items = listOf(
            SectorTarget.Domain("clima", "Clima"),
            // Réplica completa do site (Task #106/#107) -- não são domínio
            // genérico (DomainConfig), rotas próprias.
            SectorTarget.Special("drone", "Drone"),
            SectorTarget.Special("fieldview", "FieldView"),
        ),
    ),
    // Ex-categoria "Painéis": visões cruzadas/agregadas, não um lançamento
    // específico. Quando só um dos dois itens abaixo estiver liberado pra
    // conta (ex.: só "controledeinsumos", sem "safra"), esta aba vira
    // acesso direto com o ícone/rótulo do próprio item -- ver
    // RenderTab.Direct mais abaixo (pedido do usuário: mockup do setor
    // Estoque mostrando "Estoque" + "Controle de Insumos" lado a lado, sem
    // um botão "Painéis" no meio).
    BottomTab(
        "paineis", "Painéis", Icons.Filled.Assignment,
        items = listOf(
            // Gap encontrado na auditoria módulo-a-módulo contra o site --
            // no site fica na seção "estoque" (lib/modules.ts), mas aqui
            // entra em Painéis por ser uma visão cruzada (Safra/Frota/ADM),
            // ao lado de Operações.
            SectorTarget.Special("controleinsumos", "Controle de Insumos"),
            // Visão "Operação" agrupada -- no site é permissão "safra"
            // (lib/modules.ts), então entra aqui também.
            SectorTarget.Special("operacoes", "Operações"),
        ),
    ),
    // Acesso direto -- pedido do usuário ("botão frota acesso direto, retire
    // as listas suspensas").
    BottomTab("frota", "Frota", Icons.Filled.DirectionsCar, directDomainId = "frota"),
    // Ex-categoria "Lançamentos" (dentro da antiga aba Financeiro):
    // operações financeiras do dia a dia.
    BottomTab(
        "lancamentos", "Lançamentos", Icons.Filled.Receipt,
        items = listOf(
            SectorTarget.Domain("financeiro", "Lançamentos"),
            // Entrada PRÓPRIA -- pedido do usuário ("na barra inferior do
            // botão financeiro insira, na lista suspensa, um módulo chamado
            // gestão financeira"). Abre a mesma tela do Financeiro já na
            // visão "Contas a Pagar" (ver domainId == "gestaofinanceira" em
            // BRAgroNavHost.kt + startInGestao em FinanceiroScreen.kt).
            // "gestaofinanceira" não é um domínio de verdade separado.
            SectorTarget.Domain("gestaofinanceira", "Gestão Financeira"),
        ),
    ),
    // Ex-categoria "Relatórios": visões consolidadas/analíticas, não
    // lançamento.
    BottomTab(
        "relatorios", "Relatórios", Icons.Filled.BarChart,
        items = listOf(
            SectorTarget.Special("dre", "DRE"),
            SectorTarget.Special("analises", "Análises cruzadas"),
            // Livro Caixa do Produtor Rural (Task #58).
            SectorTarget.Special("livrocaixa", "Livro Caixa"),
        ),
    ),
    // Ex-categoria "Compras": aquisição de insumos/serviços de terceiros.
    BottomTab(
        "compras", "Compras", Icons.Filled.ShoppingCart,
        items = listOf(
            SectorTarget.Domain("pedidos", "Pedidos"),
            // Existia como domínio completo (colunas, cálculo automático de
            // Índice de Vantagem etc.) mas nunca tinha entrado numa lista
            // suspensa da barra inferior -- pedido do usuário ("ainda não
            // foi implementado o módulo cotações fornecedores").
            SectorTarget.Domain("cotacoesfornecedores", "Cotações de Fornecedores"),
            SectorTarget.Domain("contratos", "Contratos"),
            // Módulo de Orçamento (OCR + conciliação com nota mãe) -- ver
            // handoff-ocr-orcamento.md. Tela própria (não é um domínio
            // genérico, tabela nova Orcamento/OrcamentoItem), mesmo critério
            // de Romaneio Rápido/DRE/Análises (SectorTarget.Special).
            SectorTarget.Special("orcamentos", "Orçamentos"),
        ),
    ),
    // Ex-categoria "Faturamento": movimento de caixa/cobrança -- mesmo
    // termo já usado no site pro bloco externo de Cobranças (Task #218).
    BottomTab(
        "faturamento", "Faturamento", Icons.Filled.Payments,
        items = listOf(
            SectorTarget.Domain("caixainterno", "Caixa Interno"),
            // Cobranças e NFS-e unificados numa única entrada -- pedido do
            // usuário ("no módulo cobranças e nfse unifique e me um só
            // módulo").
            SectorTarget.Domain("cobrancas", "Cobranças / NFS-e"),
            // Inventário (ex-categoria "Patrimônio") -- pedido do usuário
            // foi só 4 botões em Financeiro ("lançamentos, relatóros,
            // compras, faturamento", sem "patrimônio"). Sem uma nova
            // categoria própria, Inventário entra aqui por afinidade
            // (ativo/patrimonial, mesmo espírito de Caixa Interno) -- ainda
            // não confirmado explicitamente com o usuário; fácil de mover
            // se ele pedir outro lugar.
            SectorTarget.Domain("inventario", "Inventário"),
        ),
    ),
    // Acesso direto -- pedido do usuário ("botão estoque retire a lista
    // suspensa e deixe botão direto estoque").
    BottomTab("estoque", "Estoque", Icons.Filled.Inventory2, directDomainId = "estoque"),
    // Ex-aba "RH" (2 itens soltos, sem category) -- virou 2 abas de acesso
    // direto de nível superior, pedido do usuário ("são 2 botões RH e
    // Controle Interno").
    BottomTab("rh", "RH", Icons.Filled.People, directDomainId = "rh"),
    BottomTab("controleinterno", "Controle Interno", Icons.Filled.Security, directDomainId = "controleinterno"),
)

// Barra do DONO/OWNER: 6 botões fixos (Safra, Financeiro, Frota, Estoque, RH,
// Módulos) -- pedido do usuário (mockup: "coloque os seguintes botões..." +
// depois "vc retirou as listas suspensas dos botões... crie um mockup
// mostrando as listas suspensas abertas"). Ao contrário de BOTTOM_TABS (que
// ACHATA cada categoria em aba própria, pensado pra contas de setor
// limitado), aqui Safra e Financeiro voltam a ser UM botão só com dropdown
// agrupado por categoria (mesma UI de categoria/acordeão de CATEGORY_ICONS,
// reaproveitada -- ela ficava "morta" desde o achatamento). Estoque e RH
// viram dropdown de 2 itens sem categoria (Estoque+Controle de Insumos /
// RH+Controle Interno) -- igual ao mockup, sem cabeçalho de categoria por
// terem só 2 itens. Frota continua acesso direto (não fazia parte do pedido
// de reverter as listas suspensas). "Controle de Insumos" aparece tanto aqui
// (dentro de Estoque) quanto dentro de Safra > Painéis -- é o mesmo item,
// dois pontos de entrada de propósito (pedido explícito do mockup).
private val OWNER_BOTTOM_TABS = listOf(
    BottomTab(
        "safra", "Safra", Icons.Filled.Agriculture,
        items = listOf(
            SectorTarget.Domain("safra", "Safra", category = "Produção"),
            SectorTarget.Domain("planejamentosafra", "Planejamento de Safra", category = "Produção"),
            SectorTarget.Domain("colheita", "Colheita", category = "Produção"),
            SectorTarget.Domain("romaneios", "Romaneios", category = "Produção"),
            SectorTarget.Domain("pragas", "Pragas", category = "Sanidade"),
            SectorTarget.Domain("receituarios", "Receituários", category = "Sanidade"),
            SectorTarget.Domain("clima", "Clima", category = "Monitoramento"),
            SectorTarget.Special("drone", "Drone", category = "Monitoramento"),
            SectorTarget.Special("fieldview", "FieldView", category = "Monitoramento"),
            SectorTarget.Special("controleinsumos", "Controle de Insumos", category = "Painéis"),
            SectorTarget.Special("operacoes", "Operações", category = "Painéis"),
        ),
    ),
    BottomTab(
        "financeiro", "Financeiro", Icons.Filled.Receipt,
        items = listOf(
            SectorTarget.Domain("financeiro", "Lançamentos", category = "Lançamentos"),
            SectorTarget.Domain("gestaofinanceira", "Gestão Financeira", category = "Lançamentos"),
            SectorTarget.Special("dre", "DRE", category = "Relatórios"),
            SectorTarget.Special("analises", "Análises cruzadas", category = "Relatórios"),
            SectorTarget.Special("livrocaixa", "Livro Caixa", category = "Relatórios"),
            SectorTarget.Domain("pedidos", "Pedidos", category = "Compras"),
            SectorTarget.Domain("cotacoesfornecedores", "Cotações de Fornecedores", category = "Compras"),
            SectorTarget.Domain("contratos", "Contratos", category = "Compras"),
            // Ver comentário completo em BOTTOM_TABS acima.
            SectorTarget.Special("orcamentos", "Orçamentos", category = "Compras"),
            SectorTarget.Domain("caixainterno", "Caixa Interno", category = "Faturamento"),
            SectorTarget.Domain("cobrancas", "Cobranças / NFS-e", category = "Faturamento"),
            SectorTarget.Domain("inventario", "Inventário", category = "Faturamento"),
        ),
    ),
    BottomTab("frota", "Frota", Icons.Filled.DirectionsCar, directDomainId = "frota"),
    BottomTab(
        "estoque", "Estoque", Icons.Filled.Inventory2,
        items = listOf(
            SectorTarget.Domain("estoque", "Estoque"),
            SectorTarget.Special("controleinsumos", "Controle de Insumos"),
        ),
    ),
    BottomTab(
        "rh", "RH", Icons.Filled.People,
        items = listOf(
            SectorTarget.Domain("rh", "RH"),
            SectorTarget.Domain("controleinterno", "Controle Interno"),
        ),
    ),
)

/** As 3 telas administrativas (Configurações/Base de Dados/Acessos) agora são
 * 100% nativas (Task #148) -- pedido explícito e repetido do usuário ("não
 * use nada para redirecionar, quero ele fixo nesse app"). Antes abriam via
 * BridgeRepository/Custom Tabs no navegador do aparelho; substituído por
 * navegação direta pras novas rotas Compose (ver SettingsScreen.kt/
 * BaseDeDadosScreen.kt/SegurancaScreen.kt e Routes em BRAgroNavHost.kt).
 * Cada item com seu próprio ícone (antes os 3 usavam o mesmo ícone de
 * engrenagem) -- pedido do usuário ("altere os ícones... coloque os ícones
 * correspondentes a cada setor"). */
// Ícone por categoria dentro do dropdown de setor -- pedido do usuário
// ("coloque as categorias com botões... com rótulos e ícones"): antes só
// tinha o chevron de expandir/recolher, sem nenhum ícone próprio por
// categoria. Reaproveita ícones JÁ usados em outro lugar do app (nunca um
// nome novo/não testado) -- mesmo critério de DomainVisuals.kt, pra não
// arriscar um "Unresolved reference" num nome de ícone que não existe na
// versão do material-icons-extended instalada (bug real já visto no
// projeto, ver CategoryBreakdownCard.kt). Categoria sem entrada aqui cai no
// ícone da própria aba (tabIcon), sem quebrar nada.
private val CATEGORY_ICONS: Map<String, ImageVector> = mapOf(
    // Safra
    "Produção" to Icons.Filled.Agriculture,
    "Sanidade" to Icons.Filled.BugReport,
    "Monitoramento" to Icons.Filled.WbSunny,
    "Painéis" to Icons.Filled.Assignment,
    // Financeiro
    "Lançamentos" to Icons.Filled.Receipt,
    "Relatórios" to Icons.Filled.BarChart,
    "Compras" to Icons.Filled.ShoppingCart,
    "Faturamento" to Icons.Filled.Payments,
    "Patrimônio" to Icons.Filled.Warehouse,
)

private data class SistemaLink(val path: String, val label: String, val icon: ImageVector)

private val SISTEMA_LINKS = listOf(
    SistemaLink("configuracoes", "Configurações", Icons.Filled.Settings),
    SistemaLink("base-de-dados", "Base de Dados", Icons.Filled.Storage),
    SistemaLink("seguranca", "Acessos", Icons.Filled.Security),
)

// Delegação de acesso por funcionário (pedido do usuário: "delegar funções
// pra funcionários, o que eles terão acesso ou não") -- o servidor já
// calcula e manda pro app, no bootstrap, a lista de módulos liberados pro
// usuário logado (SessionEntity.allowedModulesCsv, ver ConfigRepository.kt
// e allowedModuleIds() em lib/permissions.ts no site). "*" = OWNER/ADMIN,
// vê tudo. Essa função só TRADUZ o id usado aqui no app pro id de permissão
// equivalente no site -- a maioria é 1:1 (mesmo texto), só estes 4 casos
// divergem (o app nomeia a tela de um jeito, o site controla a permissão
// por outro id, ou a tela nem existe separada no site e usa a permissão de
// outra):
// - "controleinsumos" (rota do app) -> "controledeinsumos" (id no site)
// - "cobrancas" -> "pagamentos" (Cobranças e NFS-e são uma permissão só)
// - "gestaofinanceira" -> "financeiro" (mesma tela do Financeiro, view diferente)
// - "operacoes" -> "safra" (Operações usa a mesma permissão de Safra, sem dado próprio)
private fun permissionIdFor(nativeId: String): String = when (nativeId) {
    "controleinsumos" -> "controledeinsumos"
    "cobrancas" -> "pagamentos"
    "gestaofinanceira" -> "financeiro"
    "operacoes" -> "safra"
    "base-de-dados" -> "basededados"
    // Módulo de Orçamento usa a permissão de Financeiro (PERMISSION_ALIAS no
    // site) -- não ganhou toggle próprio em Acessos, ver
    // handoff-ocr-orcamento.md/actions.ts requireModule("orcamentos").
    "orcamentos" -> "financeiro"
    else -> nativeId
}

private fun isAllowed(allowedModules: Set<String>, nativeId: String): Boolean =
    allowedModules.contains("*") || allowedModules.contains(permissionIdFor(nativeId))

// Resolução de cada aba pra renderização -- pedido do usuário (mockup do
// setor Estoque): quando uma aba-grupo (Produção/Sanidade/Monitoramento/
// Painéis/Lançamentos/Relatórios/Compras/Faturamento) sobra com um único
// item visível depois do filtro de permissão (ex.: conta só com
// "controledeinsumos", sem "safra" -- a aba Painéis teria só "Controle de
// Insumos"), ela vira acesso direto com o ÍCONE/RÓTULO DO PRÓPRIO ITEM (não
// da categoria) -- sem dropdown de 1 linha só. Com 2+ itens, continua
// dropdown normal (ícone/rótulo da aba). "domainId" só é preenchido quando o
// item é um SectorTarget.Domain (usado pra destacar a aba selecionada);
// Special (DRE, Drone etc.) fica null, mesmo critério de antes.
private sealed class RenderTab {
    data class Direct(val id: String, val label: String, val icon: ImageVector, val domainId: String?, val onClick: () -> Unit) : RenderTab()
    data class Group(val tab: BottomTab) : RenderTab()
}

@Composable
fun BRAgroBottomBar(
    currentDomainId: String?,
    // Módulos liberados pro usuário logado -- ver comentário de
    // permissionIdFor() acima. Vazio = nenhum módulo liberado (não deveria
    // acontecer em uso normal -- todo papel tem pelo menos "dashboard"),
    // mostra só a Início e mais nada.
    allowedModules: Set<String>,
    // Dono da organização -- mesma checagem do site (ver layout.tsx:
    // "isOwner = ctx.role === OWNER"). Configurações/Base de Dados viraram
    // ícone no CABEÇALHO da Início pra quem NÃO é dono (ver
    // showConfiguracoesIcon/showBaseDeDadosIcon em HomeScreen.kt, réplica do
    // ConfiguracoesMenu/BaseDeDadosMenu do site) -- pedido do usuário ("no
    // cabeçalho... sempre no cabeçalho"). O dono continua acessando essas 2
    // telas por aqui, no menu "Módulos", pra não duplicar o ponto de acesso.
    isOwner: Boolean,
    onNavigateDomain: (String) -> Unit,
    onOpenDre: () -> Unit,
    onOpenAnalises: () -> Unit,
    onOpenLivroCaixa: () -> Unit,
    onOpenDrone: () -> Unit,
    onOpenFieldview: () -> Unit,
    onOpenControleInsumos: () -> Unit,
    onOpenOperacoes: () -> Unit,
    onOpenOrcamento: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenBaseDeDados: () -> Unit,
    onOpenSeguranca: () -> Unit,
) {
    var openTabId by remember { mutableStateOf<String?>(null) }
    // Categoria expandida dentro do dropdown de setor aberto -- pedido do
    // usuário ("na barra inferior coloque os títulos como botões e dentro
    // dos botões as listas suspensas correspondente a cada categoria de
    // títulos"): cada "category" (Produção/Sanidade/... em Safra,
    // Lançamentos/Relatórios/... em Financeiro) virou um botão clicável que
    // abre/fecha SÓ os itens dela (acordeão), em vez de aparecer tudo
    // sempre expandido com um texto de cabeçalho fixo. Só 1 categoria aberta
    // por vez (accordion simples) -- reseta pra fechada sempre que um setor
    // diferente é aberto (ver onClick do NavigationBarItem abaixo).
    var expandedCategory by remember { mutableStateOf<String?>(null) }
    // Cada aba só mostra os itens que o usuário tem acesso -- some a aba
    // inteira se sobrar zero itens (ou se o domínio de acesso direto,
    // Frota/Estoque, não estiver liberado). Recalculado a cada mudança de
    // allowedModules (ex.: dono reconfigurou o acesso e o app resincronizou).
    // Fonte da barra: dono usa OWNER_BOTTOM_TABS (6 botões fixos, Safra/
    // Financeiro com dropdown agrupado por categoria) -- demais contas
    // continuam com BOTTOM_TABS achatado (pensado pra setor limitado).
    val sourceTabs = if (isOwner) OWNER_BOTTOM_TABS else BOTTOM_TABS
    val visibleTabs = remember(allowedModules, isOwner) {
        sourceTabs.mapNotNull { tab ->
            if (tab.directDomainId != null) {
                if (isAllowed(allowedModules, tab.directDomainId)) tab else null
            } else {
                val visibleItems = tab.items.filter { item ->
                    val id = when (item) {
                        is SectorTarget.Domain -> item.domainId
                        is SectorTarget.Special -> item.routeKey
                    }
                    isAllowed(allowedModules, id)
                }
                if (visibleItems.isEmpty()) null else tab.copy(items = visibleItems)
            }
        }
    }
    val visibleSistemaLinks = remember(allowedModules) {
        SISTEMA_LINKS.filter { isAllowed(allowedModules, it.path) }
    }
    // Configurações/Base de Dados saem do menu "Módulos" pra quem NÃO é
    // dono -- essas contas já acessam as duas pelo ícone do CABEÇALHO da
    // Início (ver comentário no parâmetro isOwner acima). "Acessos"
    // (seguranca) não tem ícone equivalente -- continua aqui pra todo mundo
    // que tiver a permissão, dono ou não.
    val menuSistemaLinks = remember(visibleSistemaLinks, isOwner) {
        if (isOwner) visibleSistemaLinks else visibleSistemaLinks.filter { it.path == "seguranca" }
    }

    fun openSector(target: SectorTarget) {
        openTabId = null
        when (target) {
            is SectorTarget.Domain -> onNavigateDomain(target.domainId)
            is SectorTarget.Special -> when (target.routeKey) {
                "dre" -> onOpenDre()
                "analises" -> onOpenAnalises()
                "livrocaixa" -> onOpenLivroCaixa()
                "drone" -> onOpenDrone()
                "fieldview" -> onOpenFieldview()
                "controleinsumos" -> onOpenControleInsumos()
                "operacoes" -> onOpenOperacoes()
                "orcamentos" -> onOpenOrcamento()
            }
        }
    }

    // Ver comentário completo em RenderTab (topo do arquivo). Resolve cada
    // BottomTab visível em Direct (toque único navega) ou Group (dropdown).
    // Pra dono, os itens de Safra/Financeiro/Estoque/RH em OWNER_BOTTOM_TABS
    // nunca sobram com 1 item só (dono sempre tem "*"), então caem sempre em
    // Group (dropdown) -- exatamente o pedido do usuário de trazer de volta
    // as listas suspensas nesses 4 botões, sem precisar de nenhum caminho
    // especial aqui.
    val renderTabs = remember(visibleTabs) {
        visibleTabs.map { tab ->
            when {
                tab.directDomainId != null ->
                    RenderTab.Direct(tab.id, tab.label, tab.icon, tab.directDomainId) { onNavigateDomain(tab.directDomainId) }
                tab.items.size == 1 -> {
                    val item = tab.items[0]
                    val domainId = (item as? SectorTarget.Domain)?.domainId
                    RenderTab.Direct(tab.id, sectorItemLabel(item), sectorItemIcon(item, tab.icon), domainId) { openSector(item) }
                }
                else -> RenderTab.Group(tab)
            }
        }
    }
    // Com 1-2 botões sobrando (setor bem enxuto -- ex.: só Frota, ou só RH +
    // Controle Interno), ícone e rótulo ficam lado a lado (HorizontalNavChip)
    // em vez de empilhados -- pedido do usuário em 3 mockups seguidos ("como
    // tem mais espaço coloque ícone e rótulo na mesma linha"). Com 3+ (o
    // padrão de antes), continua ícone-em-cima-do-rótulo via NavigationBarItem
    // normal -- layout compacto de sempre, sem essa mudança.
    val totalButtons = renderTabs.size

    // containerColor = "background" (não mais "surface") -- pedido do
    // usuário ("a cor da barra inferior seja verde também"). "surface"
    // passou a ser a cor dos Cards (ver Theme.kt, comentário no
    // LightColors/DarkColors: precisou virar um tom DIFERENTE do fundo pra
    // os blocos se destacarem sem borda), então a barra inferior teria
    // herdado essa cor mais clara/neutra dos Cards por engano se
    // continuasse presa a "surface". "background" é o verde forte de
    // verdade (o mesmo da tela toda) -- tonalElevation em 0.dp continua
    // zerado, sem somar nenhuma camada extra por cima.
    NavigationBar(containerColor = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
        renderTabs.forEach { rt ->
            when (rt) {
                // Acesso direto (aba directDomainId de sempre -- Frota/
                // Estoque/RH/Controle Interno -- OU aba-grupo colapsada por
                // sobrar 1 item só, ver RenderTab acima).
                is RenderTab.Direct -> {
                    val selected = rt.domainId != null && rt.domainId == currentDomainId
                    if (totalButtons <= 2) {
                        // Ícone + rótulo na mesma linha, pedido do usuário em
                        // 3 mockups seguidos ("como tem mais espaço coloque
                        // ícone e rótulo na mesma linha"). 1 botão só (ex.:
                        // Frota sozinha) fica CENTRALIZADO em vez de esticado
                        // full-width feito o NavigationBarItem padrão faria;
                        // com 2 (ex.: RH + Controle Interno, ou Estoque +
                        // Controle de Insumos), cada um ocupa metade via
                        // weight(1f), igual às demais abas.
                        if (totalButtons == 1) {
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                HorizontalNavChip(icon = rt.icon, label = rt.label, selected = selected, onClick = rt.onClick)
                            }
                        } else {
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                HorizontalNavChip(icon = rt.icon, label = rt.label, selected = selected, onClick = rt.onClick)
                            }
                        }
                    } else {
                        // 3+ botões: layout compacto de sempre (ícone em
                        // cima do rótulo), sem essa mudança.
                        Box(modifier = Modifier.weight(1f)) {
                            this@NavigationBar.NavigationBarItem(
                                selected = selected,
                                onClick = rt.onClick,
                                icon = { Icon(rt.icon, contentDescription = rt.label, modifier = Modifier.size(22.dp)) },
                                label = { Text(rt.label, maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelSmall) },
                                colors = BottomNavColors,
                            )
                        }
                    }
                }
                // Aba-grupo com 2+ itens visíveis -- dropdown normal.
                is RenderTab.Group -> {
                    val tab = rt.tab
                    val selected = tab.items.any { it is SectorTarget.Domain && it.domainId == currentDomainId }
                    // NavigationBarItem só existe como extensão de RowScope (o
                    // escopo que NavigationBar { } dá pro seu conteúdo) -- o Box
                    // aqui dentro (âncora do DropdownMenu) cria um escopo novo
                    // (BoxScope) que esconde esse receiver implícito, por isso o
                    // "this@NavigationBar." explícito abaixo. E o próprio
                    // NavigationBarItem aplica ".weight(1f)" NELE MESMO, internamente
                    // -- como agora ele é neto do Row (não filho direto, por causa
                    // do Box no meio), esse weight interno é ignorado e a aba fica
                    // "encolhida" (só a 1ª aba parecia sobrar espaço pra aparecer
                    // inteira). Corrige pondo o weight(1f) direto no Box, que
                    // continua sendo filho direto do Row.
                    Box(modifier = Modifier.weight(1f)) {
                        this@NavigationBar.NavigationBarItem(
                            selected = selected,
                            onClick = {
                                // Fecha se já estava aberto (toque de novo no
                                // mesmo botão), senão abre este e reseta o
                                // acordeão de categoria (ver comentário acima).
                                openTabId = if (openTabId == tab.id) null else tab.id
                                expandedCategory = null
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label, modifier = Modifier.size(22.dp)) },
                            // maxLines/softWrap + labelSmall: rótulos como
                            // "Financeiro" quebravam em 2 linhas ou saíam cortados
                            // com o tamanho padrão -- pedido do usuário ("realoque
                            // os espaços pra escrever a palavra Financeiro completa
                            // em uma linha").
                            label = { Text(tab.label, maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelSmall) },
                            colors = BottomNavColors,
                        )
                        // Fundo do menu suspenso = MESMO verde forte da barra
                        // inferior/fundo do app -- pedido do usuário ("coloque
                        // nas listas suspensas... o mesmo verde do app"). O
                        // DropdownMenu do Material3 1.2.1 não expõe um parâmetro
                        // de cor direto (arriscado assumir uma API não conferida
                        // -- ver lição do bug ExposedDropdownMenu), então em vez
                        // disso sobrescrevemos "colorScheme.surface" (a cor que
                        // ele lê por dentro) só dentro deste escopo, via
                        // MaterialTheme(colorScheme = ...) -- API estável desde
                        // sempre no Material3, sem risco de incompatibilidade.
                        MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(surface = MaterialTheme.colorScheme.background)) {
                        DropdownMenu(expanded = openTabId == tab.id, onDismissRequest = { openTabId = null }) {
                            // Cada aba-grupo agora É a antiga categoria (ver
                            // comentário no topo de BOTTOM_TABS) -- os itens não
                            // carregam mais "category" nenhuma, então "groups"
                            // abaixo sempre produz um único grupo (category ==
                            // null) e cai direto na lista plana. O agrupamento
                            // por "category"/acordeão continua existindo pra não
                            // arriscar mexer no que já funciona, só que inerte
                            // por enquanto (nenhuma aba usa "category" hoje).
                            val groups = mutableListOf<Pair<String?, MutableList<SectorTarget>>>()
                            tab.items.forEach { item ->
                                val category = when (item) {
                                    is SectorTarget.Domain -> item.category
                                    is SectorTarget.Special -> item.category
                                }
                                val lastGroup = groups.lastOrNull()
                                if (lastGroup != null && lastGroup.first == category) lastGroup.second.add(item)
                                else groups.add(category to mutableListOf(item))
                            }
                            groups.forEachIndexed { index, (category, items) ->
                                if (category == null) {
                                    items.forEach { item -> SectorMenuItem(item, tab.icon, onClick = { openSector(item) }) }
                                } else {
                                    if (index > 0) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(vertical = 4.dp),
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                        )
                                    }
                                    val expanded = expandedCategory == category
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { expandedCategory = if (expanded) null else category }
                                            .background(
                                                MaterialTheme.colorScheme.onSurface.copy(alpha = if (expanded) 0.10f else 0.05f),
                                                RoundedCornerShape(8.dp),
                                            )
                                            .padding(horizontal = 16.dp, vertical = 10.dp),
                                    ) {
                                        Icon(
                                            CATEGORY_ICONS[category] ?: tab.icon,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp).padding(end = 8.dp),
                                            tint = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Text(
                                            category.uppercase(),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Icon(
                                            if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                        )
                                    }
                                    if (expanded) {
                                        items.forEach { item -> SectorMenuItem(item, tab.icon, onClick = { openSector(item) }) }
                                    }
                                }
                            }
                        }
                        }
                    }
                }
            }
        }
        // Aba "Módulos" (Acessos pra todo mundo; Configurações/Base de Dados
        // só pro dono, ver menuSistemaLinks acima) some por completo se
        // sobrar zero itens -- mesmo critério de filtragem das demais abas.
        if (menuSistemaLinks.isNotEmpty()) {
        Box(modifier = Modifier.weight(1f)) {
            this@NavigationBar.NavigationBarItem(
                selected = false,
                onClick = { openTabId = if (openTabId == "sistema") null else "sistema" },
                icon = { Icon(Icons.Filled.GridView, contentDescription = "Módulos", modifier = Modifier.size(22.dp)) },
                label = { Text("Módulos", maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelSmall) },
                colors = BottomNavColors,
            )
            // Mesmo critério do dropdown de setor acima: sobrescreve `surface`
            // com `background` pra pegar o verde forte, já que o container
            // padrão do DropdownMenu (Material3 1.2.1) lê `surface`, que
            // agora é o tom mais claro dos Cards (ver Theme.kt).
            MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(surface = MaterialTheme.colorScheme.background)) {
            DropdownMenu(expanded = openTabId == "sistema", onDismissRequest = { openTabId = null }) {
                menuSistemaLinks.forEach { link ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                link.label,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        leadingIcon = { Icon(link.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface) },
                        onClick = {
                            openTabId = null
                            when (link.path) {
                                "configuracoes" -> onOpenSettings()
                                "base-de-dados" -> onOpenBaseDeDados()
                                "seguranca" -> onOpenSeguranca()
                            }
                        },
                    )
                }
            }
            }
        }
        }
    }
}

// Botão de acesso direto com ícone e rótulo NA MESMA LINHA -- pedido do
// usuário em 3 mockups seguidos, sobre setores com pouca coisa na barra
// ("como tem mais espaço coloque ícone e rótulo na mesma linha"): usado só
// quando sobram 1-2 botões no total (ver totalButtons/RenderTab.Direct
// acima), nunca no caso normal de 3+ abas (que continua NavigationBarItem
// padrão, ícone em cima do rótulo). Reaproveita o mesmo esquema de cor
// preto/branco (onSurface) + pílula de fundo translúcida quando selecionado
// já usado no botão de categoria do dropdown (Row com .background(...,
// RoundedCornerShape) alguns parágrafos abaixo).
@Composable
private fun HorizontalNavChip(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clickable(onClick = onClick)
            .background(
                MaterialTheme.colorScheme.onSurface.copy(alpha = if (selected) 0.12f else 0f),
                RoundedCornerShape(10.dp),
            )
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(20.dp).padding(end = 8.dp))
        Text(label, color = color, style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false)
    }
}

// Rótulo/ícone de um item de setor -- extraído pra ser reaproveitado tanto
// pelo SectorMenuItem (dentro de um dropdown) quanto pela resolução de
// RenderTab.Direct (quando uma aba-grupo sobra com um único item visível
// após o filtro de permissão, ver comentário em RenderTab mais abaixo).
private fun sectorItemLabel(item: SectorTarget): String = when (item) {
    is SectorTarget.Domain -> item.label
    is SectorTarget.Special -> item.label
}

private fun sectorItemIcon(item: SectorTarget, fallback: ImageVector): ImageVector = when (item) {
    is SectorTarget.Domain -> domainIcon(item.domainId)
    // Antes cada "Special" caía no ícone da aba pai (fallback) -- DRE e
    // Análises cruzadas ficavam iguais entre si e iguais ao item
    // "Financeiro"/"Lançamentos" -- pedido do usuário ("troque icone dre
    // e icone analises cruzadas").
    is SectorTarget.Special -> when (item.routeKey) {
        "dre" -> Icons.Filled.Assessment
        "analises" -> Icons.Filled.Analytics
        "livrocaixa" -> Icons.AutoMirrored.Filled.MenuBook
        "drone" -> Icons.Filled.FlightTakeoff
        "fieldview" -> Icons.Filled.Map
        "controleinsumos" -> Icons.Filled.AccountTree
        "operacoes" -> Icons.Filled.Timeline
        // Câmera -- reforça a leitura automática (OCR) que abre a tela, ver
        // handoff-ocr-orcamento.md.
        "orcamentos" -> Icons.Filled.CameraAlt
        else -> fallback
    }
}

// Item de um dropdown de setor (rótulo + ícone) -- extraído do corpo de
// BRAgroBottomBar pra ser reaproveitado tanto por itens SEM categoria quanto
// pelos itens DENTRO de uma categoria expandida (ver comentário no botão de
// categoria acima). Mesmo cálculo de rótulo/ícone que já existia inline
// antes da reestruturação em acordeão.
@Composable
private fun SectorMenuItem(item: SectorTarget, tabIcon: ImageVector, onClick: () -> Unit) {
    val label = sectorItemLabel(item)
    val icon = sectorItemIcon(item, tabIcon)
    // Preto/branco (onSurface) em vez de verde -- pedido do usuário ("na
    // barra inferior dos botões, lista suspensa fontes módulos
    // preto/branco"), mesmo critério já aplicado aos blocos individuais
    // (MODULE_ICON_FG em ModuleIconRow.kt).
    DropdownMenuItem(
        // maxLines/ellipsis -- achado de auditoria: nomes de módulo mais
        // longos (ex. "Cotações de Fornecedores", "Planejamento de Safra")
        // sem proteção nenhuma contra quebra de linha dentro do menu
        // suspenso.
        text = {
            Text(
                label,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingIcon = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface) },
        onClick = onClick,
        // Indentação leve -- pedido do usuário ("dentro dos botões as
        // listas suspensas"): reforça visualmente que este item pertence à
        // categoria/botão logo acima, quando chamado de dentro de um bloco
        // expandido (itens sem categoria, ex. RH, ficam sem indent extra
        // porque o padding padrão do DropdownMenuItem já é o mesmo usado
        // antes da reestruturação).
        modifier = Modifier.padding(start = 8.dp),
    )
}
