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
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Timeline
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

private val BOTTOM_TABS = listOf(
    BottomTab(
        "safra", "Safra", Icons.Filled.Eco,
        items = listOf(
            // "Produção": o ciclo principal safra -> planejamento -> colheita
            // -> transporte.
            SectorTarget.Domain("safra", "Safra", category = "Produção"),
            SectorTarget.Domain("planejamentosafra", "Planejamento de Safra", category = "Produção"),
            SectorTarget.Domain("colheita", "Colheita", category = "Produção"),
            // "Romaneio rápido" saiu daqui -- virou um 2º FAB dentro do
            // próprio módulo Romaneios (ver DomainListScreen.kt), pedido do
            // usuário ("coloque romaneio rápido como um botão dentro de
            // romaneio, unifique").
            SectorTarget.Domain("romaneios", "Romaneios", category = "Produção"),
            // "Sanidade": pragas/doenças e o receituário que as trata.
            SectorTarget.Domain("pragas", "Pragas", category = "Sanidade"),
            SectorTarget.Domain("receituarios", "Receituários", category = "Sanidade"),
            // "Monitoramento": dados de campo captados por fonte externa
            // (clima, imagens de drone, mapa/talhões).
            SectorTarget.Domain("clima", "Clima", category = "Monitoramento"),
            // Réplica completa do site (Task #106/#107) -- ficam aqui no
            // setor Safra por serem dados de campo/talhão, mesmo critério
            // de agrupamento de Pragas/Clima acima. Igual DRE/Análises,
            // não são um domínio genérico (DomainConfig) -- rotas próprias.
            SectorTarget.Special("drone", "Drone", category = "Monitoramento"),
            SectorTarget.Special("fieldview", "FieldView", category = "Monitoramento"),
            // "Painéis": visões cruzadas/agregadas, não um lançamento
            // específico.
            // Painel "Controle de Insumos" (gap encontrado na auditoria
            // módulo-a-módulo contra o site, pedido do usuário "implemente
            // tudo que falta ainda para o app native da plataforma") -- no
            // site fica na seção "estoque" (lib/modules.ts), mas o app não
            // tem dropdown na aba Estoque (é acesso direto, pedido explícito
            // do usuário: "botão estoque retire a lista suspensa"). Entra
            // aqui em Safra por ser um painel cruzando consumo de
            // Safra/Frota/ADM, ao lado de outros painéis "especiais"
            // (Drone/FieldView).
            SectorTarget.Special("controleinsumos", "Controle de Insumos", category = "Painéis"),
            // Visão "Operação" agrupada (mesmo gap/critério de agrupamento
            // do item acima) -- no site é "campo"/permissão "safra"
            // (lib/modules.ts), então entra aqui também.
            SectorTarget.Special("operacoes", "Operações", category = "Painéis"),
        ),
    ),
    // Acesso direto -- pedido do usuário ("botão frota acesso direto, retire
    // as listas suspensas").
    BottomTab("frota", "Frota", Icons.Filled.DirectionsCar, directDomainId = "frota"),
    BottomTab(
        "financeiro", "Financeiro", Icons.Filled.AccountBalanceWallet,
        items = listOf(
            // Categorias adicionadas -- pedido do usuário ("na imagem 2 faça
            // a mesma coisa criando as categorias com botões"): antes essa
            // lista de 10 itens não tinha nenhuma divisão (só a aba Safra
            // tinha "category", ver comentário no topo do arquivo). Critério
            // de agrupamento abaixo é novo (não existe um equivalente 1:1 no
            // site, que não agrupa esses itens na navegação) -- espelha a
            // natureza funcional de cada um.
            //
            // "Lançamentos": operações financeiras do dia a dia.
            SectorTarget.Domain("financeiro", "Lançamentos", category = "Lançamentos"),
            // Entrada PRÓPRIA -- pedido do usuário ("na barra inferior do
            // botão financeiro insira, na lista suspensa, um módulo chamado
            // gestão financeira... agora o módulo lançamento será só ele").
            // Antes "Gestão Financeira" só existia como um dropdown DENTRO
            // da tela de Lançamentos (ver FinanceiroGestaoDropdownButton em
            // FinanceiroScreen.kt); agora também é uma entrada direta aqui,
            // que abre a mesma tela já na visão "Contas a Pagar" (ver
            // domainId == "gestaofinanceira" em BRAgroNavHost.kt +
            // startInGestao em FinanceiroScreen.kt). "financeiro" não é um
            // domínio de verdade separado -- roteado pra tela do Financeiro.
            SectorTarget.Domain("gestaofinanceira", "Gestão Financeira", category = "Lançamentos"),
            // "Relatórios": visões consolidadas/analíticas, não lançamento.
            SectorTarget.Special("dre", "DRE", category = "Relatórios"),
            SectorTarget.Special("analises", "Análises cruzadas", category = "Relatórios"),
            // Livro Caixa do Produtor Rural (Task #58) -- ja existia no site
            // (Task #49), faltava no app. Mesmo criterio de agrupamento
            // (relatorio financeiro, mesmo setor de DRE/Analises).
            SectorTarget.Special("livrocaixa", "Livro Caixa", category = "Relatórios"),
            // "Compras": aquisição de insumos/serviços de terceiros.
            // "Importar NF-e" saiu daqui -- virou o botão "Importar XML"
            // dentro do próprio Financeiro (ver FinanceiroScreen.kt), pedido
            // do usuário ("crie um botão importar xml e unifique esses dois
            // módulos").
            SectorTarget.Domain("pedidos", "Pedidos", category = "Compras"),
            // Existia como domínio (colunas, cálculo automático de Índice de
            // Vantagem, listas suspensas próprias já cadastradas no banco --
            // categorias_cotacao/itens_estoque/entidades_financeiro/
            // unidades/formas_pgto, todas conferidas), mas nunca tinha
            // entrado nessa lista suspensa -- só aparecia na tela "Módulos"
            // (grade completa, ModulosScreen.kt), não no atalho principal do
            // dia a dia. Pra quem só usa a aba Financeiro, o módulo parecia
            // não existir -- pedido do usuário ("ainda não foi implementado
            // o módulo cotações fornecedores"). Corrigido aqui.
            SectorTarget.Domain("cotacoesfornecedores", "Cotações de Fornecedores", category = "Compras"),
            SectorTarget.Domain("contratos", "Contratos", category = "Compras"),
            // "Faturamento": movimento de caixa/cobrança -- mesmo termo já
            // usado no site pro bloco externo de Cobranças (Task #218).
            SectorTarget.Domain("caixainterno", "Caixa Interno", category = "Faturamento"),
            // Cobranças e NFS-e unificados numa única entrada -- pedido do
            // usuário ("no módulo cobranças e nfse unifique e me um só
            // módulo"). Abre em Cobranças, com um alternador pra NFS-e
            // dentro da própria tela (ver DomainListScreen.kt).
            SectorTarget.Domain("cobrancas", "Cobranças / NFS-e", category = "Faturamento"),
            // "Patrimônio": ativos cadastrados, não movimento de caixa.
            // Migrou aqui de Frota/Estoque (que perderam a lista suspensa) --
            // pedido do usuário ("botão financeiro acrescente na lista
            // suspensa inventário").
            SectorTarget.Domain("inventario", "Inventário", category = "Patrimônio"),
        ),
    ),
    // Acesso direto -- pedido do usuário ("botão estoque retire a lista
    // suspensa e deixe botão direto estoque").
    BottomTab("estoque", "Estoque", Icons.Filled.Inventory2, directDomainId = "estoque"),
    BottomTab(
        "rh", "RH", Icons.Filled.People,
        items = listOf(
            SectorTarget.Domain("rh", "RH"),
            // Migrou aqui de Frota/Estoque -- pedido do usuário ("botão rh
            // acrescente na lista suspensa controle interno").
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
    else -> nativeId
}

private fun isAllowed(allowedModules: Set<String>, nativeId: String): Boolean =
    allowedModules.contains("*") || allowedModules.contains(permissionIdFor(nativeId))

@Composable
fun BRAgroBottomBar(
    currentDomainId: String?,
    // Módulos liberados pro usuário logado -- ver comentário de
    // permissionIdFor() acima. Vazio = nenhum módulo liberado (não deveria
    // acontecer em uso normal -- todo papel tem pelo menos "dashboard"),
    // mostra só a Início e mais nada.
    allowedModules: Set<String>,
    onNavigateDomain: (String) -> Unit,
    onOpenDre: () -> Unit,
    onOpenAnalises: () -> Unit,
    onOpenLivroCaixa: () -> Unit,
    onOpenDrone: () -> Unit,
    onOpenFieldview: () -> Unit,
    onOpenControleInsumos: () -> Unit,
    onOpenOperacoes: () -> Unit,
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
    val visibleTabs = remember(allowedModules) {
        BOTTOM_TABS.mapNotNull { tab ->
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
    // Setor com poucos módulos (equivalente nativo do "isFewModulesSector"
    // do site, ver layout.tsx: workModules.length<=2) -- pedido do usuário
    // ("transforme configurações e base de dados em ícones conforme a
    // plataforma"). O site conta entradas do catálogo MODULES (granularidade
    // por permissão, não por setor); o app não tem esse catálogo em Kotlin,
    // então a unidade equivalente aqui é a própria ABA de setor (Safra/
    // Frota/Financeiro/Estoque/RH) -- ter só 1-2 abas visíveis É o
    // equivalente funcional de "poucos módulos" na navegação do app.
    // "*" (OWNER/ADMIN, vê tudo) nunca conta como poucos módulos, mesmo
    // critério do site (isFewModulesSector também nunca se aplica a quem tem
    // acesso total).
    val fewModulesSector = !allowedModules.contains("*") && visibleTabs.isNotEmpty() && visibleTabs.size <= 2
    // Configurações/Base de Dados saem do menu "Módulos" (2 níveis de toque)
    // e viram ícone de acesso direto na própria barra -- mesmo espírito do
    // site (showConfiguracoesIcon/showBaseDeDadosIcon no Topbar, fora de
    // qualquer menu). "Acessos" (seguranca) não tem equivalente de ícone no
    // site (fica só na sidebar do dono) -- continua dentro do menu "Módulos"
    // aqui também, promovido ou não.
    val promotedSistemaLinks = if (fewModulesSector) {
        visibleSistemaLinks.filter { it.path == "configuracoes" || it.path == "base-de-dados" }
    } else {
        emptyList()
    }
    val menuSistemaLinks = visibleSistemaLinks - promotedSistemaLinks.toSet()

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
            }
        }
    }

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
        visibleTabs.forEach { tab ->
            val selected = tab.directDomainId == currentDomainId ||
                tab.items.any { it is SectorTarget.Domain && it.domainId == currentDomainId }
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
                        if (tab.directDomainId != null) onNavigateDomain(tab.directDomainId)
                        else {
                            // Fecha se já estava aberto (toque de novo no
                            // mesmo botão), senão abre este e reseta o
                            // acordeão de categoria (ver comentário acima).
                            openTabId = if (openTabId == tab.id) null else tab.id
                            expandedCategory = null
                        }
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
                if (tab.directDomainId == null) {
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
                        // Categorias como botões (acordeão) -- pedido do
                        // usuário: "coloque os títulos como botões e dentro
                        // dos botões as listas suspensas correspondente a
                        // cada categoria de títulos" (antes era um texto de
                        // cabeçalho fixo com tudo já expandido embaixo, ver
                        // git history). Agrupa os itens em blocos contíguos
                        // por "category" (já vêm agrupados na declaração de
                        // BOTTOM_TABS acima) -- "category" null (nenhuma aba
                        // usa isso hoje, mas o modo continua suportado)
                        // renderiza os itens direto, sem botão nenhum.
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
        // Configurações/Base de Dados promovidos a ícone de acesso direto --
        // ver comentário completo em "promotedSistemaLinks" acima. Mesmo
        // visual/comportamento das abas de acesso direto (Frota/Estoque):
        // toque único já navega, sem dropdown.
        promotedSistemaLinks.forEach { link ->
            Box(modifier = Modifier.weight(1f)) {
                this@NavigationBar.NavigationBarItem(
                    selected = false,
                    onClick = {
                        when (link.path) {
                            "configuracoes" -> onOpenSettings()
                            "base-de-dados" -> onOpenBaseDeDados()
                        }
                    },
                    icon = { Icon(link.icon, contentDescription = link.label, modifier = Modifier.size(22.dp)) },
                    label = { Text(link.label, maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelSmall) },
                    colors = BottomNavColors,
                )
            }
        }
        // Aba "Módulos" (Acessos, e Configurações/Base de Dados quando NÃO
        // promovidos acima) some por completo se sobrar zero itens -- mesmo
        // critério de filtragem das demais abas. Na prática hoje só
        // OWNER/ADMIN (allowedModules = "*") veem isso quando não é setor de
        // poucos módulos, já que nenhum papel padrão (AGRONOMO/FINANCEIRO/
        // RH/OPERADOR) inclui essas permissões por padrão (ver ROLE_MODULES
        // em lib/permissions.ts) -- um CUSTOM pode ganhar acesso explícito lá
        // na tela de Acessos.
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

// Item de um dropdown de setor (rótulo + ícone) -- extraído do corpo de
// BRAgroBottomBar pra ser reaproveitado tanto por itens SEM categoria quanto
// pelos itens DENTRO de uma categoria expandida (ver comentário no botão de
// categoria acima). Mesmo cálculo de rótulo/ícone que já existia inline
// antes da reestruturação em acordeão.
@Composable
private fun SectorMenuItem(item: SectorTarget, tabIcon: ImageVector, onClick: () -> Unit) {
    val label = when (item) {
        is SectorTarget.Domain -> item.label
        is SectorTarget.Special -> item.label
    }
    val icon = when (item) {
        is SectorTarget.Domain -> domainIcon(item.domainId)
        // Antes cada "Special" caía no ícone da aba pai (tabIcon) -- DRE e
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
            else -> tabIcon
        }
    }
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
