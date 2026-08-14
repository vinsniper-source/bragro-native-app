package com.bragro.mobile.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.bragro.mobile.ui.home.domainIcon

// Fundo da barra inferior inteira agora é verde (mesma cor do ícone
// fazenda/primary) -- pedido do usuário ("na barra inferior dos botões
// coloque o fundo a mesma cor por exemplo do ícone fazenda início"). Como o
// fundo virou sólido/verde, os ícones/rótulos precisaram trocar pra branco
// (em vez do próprio verde, que ficaria invisível em cima de verde) --
// opacidade menor pro item não-selecionado distingue do selecionado (branco
// cheio + pílula translúcida atrás), mesmo conceito de antes (aba ativa em
// destaque), só que invertido de "verde sobre fundo neutro" pra "branco
// sobre fundo verde".
private val BottomNavColors: androidx.compose.material3.NavigationBarItemColors
    @Composable
    get() = NavigationBarItemDefaults.colors(
        selectedIconColor = androidx.compose.ui.graphics.Color.White,
        selectedTextColor = androidx.compose.ui.graphics.Color.White,
        indicatorColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.18f),
        unselectedIconColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.70f),
        unselectedTextColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.70f),
    )

// Pedido do usuário: "os botões inferiores devem aparecer uma lista
// suspensa quando apertar de todas as operações e atividades de cada setor
// correspondente" -- cada aba deixa de navegar direto pra UM domínio e passa
// a abrir um menu com TODAS as atividades daquele setor (mesmo agrupamento
// de DomainVisuals.kt/modules.ts), inclusive as que antes só apareciam nos
// atalhos da extinta tela "Dashboard" (DRE/Análises/NF-e/Romaneio Rápido,
// ver SectorTarget.Special abaixo) -- o ícone de Dashboard não existe mais.
private sealed class SectorTarget {
    data class Domain(val domainId: String, val label: String) : SectorTarget()

    /** Telas que não são um domínio genérico (DRE/Análises/NF-e/Romaneio
     * Rápido) -- viviam na extinta tela "Dashboard" (atalhos), redistribuídas
     * aqui pro setor a que pertencem (mesmo critério de agrupamento de
     * lib/modules.ts no site: DRE/Análises/NF-e são "financeiro"; Romaneio
     * Rápido é "campo", junto de Romaneios). */
    data class Special(val routeKey: String, val label: String) : SectorTarget()
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
            SectorTarget.Domain("safra", "Safra"),
            SectorTarget.Domain("planejamentosafra", "Planejamento de Safra"),
            SectorTarget.Domain("colheita", "Colheita"),
            // "Romaneio rápido" saiu daqui -- virou um 2º FAB dentro do
            // próprio módulo Romaneios (ver DomainListScreen.kt), pedido do
            // usuário ("coloque romaneio rápido como um botão dentro de
            // romaneio, unifique").
            SectorTarget.Domain("romaneios", "Romaneios"),
            SectorTarget.Domain("pragas", "Pragas"),
            SectorTarget.Domain("receituarios", "Receituários"),
            SectorTarget.Domain("clima", "Clima"),
            // Réplica completa do site (Task #106/#107) -- ficam aqui no
            // setor Safra por serem dados de campo/talhão, mesmo critério
            // de agrupamento de Pragas/Clima acima. Igual DRE/Análises,
            // não são um domínio genérico (DomainConfig) -- rotas próprias.
            SectorTarget.Special("drone", "Drone"),
            SectorTarget.Special("fieldview", "FieldView"),
        ),
    ),
    // Acesso direto -- pedido do usuário ("botão frota acesso direto, retire
    // as listas suspensas").
    BottomTab("frota", "Frota", Icons.Filled.DirectionsCar, directDomainId = "frota"),
    BottomTab(
        "financeiro", "Financeiro", Icons.Filled.AccountBalanceWallet,
        items = listOf(
            // Renomeado -- pedido do usuário ("renomeie o nome financeiro
            // (lançamentos) apenas para lançamentos").
            SectorTarget.Domain("financeiro", "Lançamentos"),
            SectorTarget.Special("dre", "DRE"),
            SectorTarget.Special("analises", "Análises cruzadas"),
            // "Importar NF-e" saiu daqui -- virou o botão "Importar XML"
            // dentro do próprio Financeiro (ver FinanceiroScreen.kt), pedido
            // do usuário ("crie um botão importar xml e unifique esses dois
            // módulos").
            SectorTarget.Domain("pedidos", "Pedidos"),
            // Existia como domínio (colunas, cálculo automático de Índice de
            // Vantagem, listas suspensas próprias já cadastradas no banco --
            // categorias_cotacao/itens_estoque/entidades_financeiro/
            // unidades/formas_pgto, todas conferidas), mas nunca tinha
            // entrado nessa lista suspensa -- só aparecia na tela "Módulos"
            // (grade completa, ModulosScreen.kt), não no atalho principal do
            // dia a dia. Pra quem só usa a aba Financeiro, o módulo parecia
            // não existir -- pedido do usuário ("ainda não foi implementado
            // o módulo cotações fornecedores"). Corrigido aqui.
            SectorTarget.Domain("cotacoesfornecedores", "Cotações de Fornecedores"),
            SectorTarget.Domain("contratos", "Contratos"),
            SectorTarget.Domain("caixainterno", "Caixa Interno"),
            // Cobranças e NFS-e unificados numa única entrada -- pedido do
            // usuário ("no módulo cobranças e nfse unifique e me um só
            // módulo"). Abre em Cobranças, com um alternador pra NFS-e
            // dentro da própria tela (ver DomainListScreen.kt).
            SectorTarget.Domain("cobrancas", "Cobranças / NFS-e"),
            // Migrou aqui de Frota/Estoque (que perderam a lista suspensa) --
            // pedido do usuário ("botão financeiro acrescente na lista
            // suspensa inventário").
            SectorTarget.Domain("inventario", "Inventário"),
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

@Composable
fun BRAgroBottomBar(
    currentDomainId: String?,
    onNavigateDomain: (String) -> Unit,
    onOpenDre: () -> Unit,
    onOpenAnalises: () -> Unit,
    onOpenDrone: () -> Unit,
    onOpenFieldview: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenBaseDeDados: () -> Unit,
    onOpenSeguranca: () -> Unit,
) {
    var openTabId by remember { mutableStateOf<String?>(null) }

    fun openSector(target: SectorTarget) {
        openTabId = null
        when (target) {
            is SectorTarget.Domain -> onNavigateDomain(target.domainId)
            is SectorTarget.Special -> when (target.routeKey) {
                "dre" -> onOpenDre()
                "analises" -> onOpenAnalises()
                "drone" -> onOpenDrone()
                "fieldview" -> onOpenFieldview()
            }
        }
    }

    NavigationBar(containerColor = MaterialTheme.colorScheme.primary) {
        BOTTOM_TABS.forEach { tab ->
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
                        else openTabId = tab.id
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
                    DropdownMenu(expanded = openTabId == tab.id, onDismissRequest = { openTabId = null }) {
                        tab.items.forEach { item ->
                            val label = when (item) {
                                is SectorTarget.Domain -> item.label
                                is SectorTarget.Special -> item.label
                            }
                            val icon = when (item) {
                                is SectorTarget.Domain -> domainIcon(item.domainId)
                                // Antes cada "Special" caía no ícone da aba
                                // pai (tab.icon) -- DRE e Análises cruzadas
                                // ficavam iguais entre si e iguais ao item
                                // "Financeiro"/"Lançamentos" -- pedido do
                                // usuário ("troque icone dre e icone
                                // analises cruzadas").
                                is SectorTarget.Special -> when (item.routeKey) {
                                    "dre" -> Icons.Filled.Assessment
                                    "analises" -> Icons.Filled.Analytics
                                    "drone" -> Icons.Filled.FlightTakeoff
                                    "fieldview" -> Icons.Filled.Map
                                    else -> tab.icon
                                }
                            }
                            DropdownMenuItem(
                                text = { Text(label) },
                                leadingIcon = { Icon(icon, contentDescription = null) },
                                onClick = { openSector(item) },
                            )
                        }
                    }
                }
            }
        }
        Box(modifier = Modifier.weight(1f)) {
            this@NavigationBar.NavigationBarItem(
                selected = false,
                onClick = { openTabId = if (openTabId == "sistema") null else "sistema" },
                icon = { Icon(Icons.Filled.GridView, contentDescription = "Módulos", modifier = Modifier.size(22.dp)) },
                label = { Text("Módulos", maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelSmall) },
                colors = BottomNavColors,
            )
            DropdownMenu(expanded = openTabId == "sistema", onDismissRequest = { openTabId = null }) {
                SISTEMA_LINKS.forEach { link ->
                    DropdownMenuItem(
                        text = { Text(link.label) },
                        leadingIcon = { Icon(link.icon, contentDescription = null) },
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
