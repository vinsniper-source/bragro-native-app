package com.bragro.mobile.ui.nav

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Inventory2
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import com.bragro.mobile.data.repo.BridgeRepository
import com.bragro.mobile.ui.home.domainIcon
import com.bragro.mobile.ui.theme.BrGreen
import kotlinx.coroutines.launch

// Pedido do usuário: "coloque o tom de verde da barra inferior dos botões em
// todo app conforme o modo claro e escuro" -- o Material3 antes derivava a
// cor do item selecionado automaticamente da paleta tonal (`primary` fica
// bem esmaecido dentro do NavigationBarItem padrão), o que resultava numa
// aba selecionada quase sem verde visível. Fixamos a cor explícita do
// BrGreen (mesma em claro/escuro) pro ícone/rótulo/indicador da aba ativa.
private val BottomNavColors: androidx.compose.material3.NavigationBarItemColors
    @Composable
    get() = NavigationBarItemDefaults.colors(
        selectedIconColor = BrGreen,
        selectedTextColor = BrGreen,
        indicatorColor = BrGreen.copy(alpha = 0.18f),
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
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

private data class BottomTab(val id: String, val label: String, val icon: ImageVector, val items: List<SectorTarget>)

private val BOTTOM_TABS = listOf(
    BottomTab(
        "safra", "Safra", Icons.Filled.Eco,
        listOf(
            SectorTarget.Domain("safra", "Safra"),
            SectorTarget.Domain("planejamentosafra", "Planejamento de Safra"),
            SectorTarget.Domain("colheita", "Colheita"),
            SectorTarget.Domain("romaneios", "Romaneios"),
            SectorTarget.Special("romaneio_quick", "Romaneio rápido (balança)"),
            SectorTarget.Domain("pragas", "Pragas"),
            SectorTarget.Domain("receituarios", "Receituários"),
            SectorTarget.Domain("clima", "Clima"),
        ),
    ),
    BottomTab(
        "frota", "Frota", Icons.Filled.DirectionsCar,
        listOf(
            SectorTarget.Domain("frota", "Frota"),
            SectorTarget.Domain("estoque", "Estoque"),
            SectorTarget.Domain("controleinterno", "Controle Interno"),
            SectorTarget.Domain("inventario", "Inventário"),
        ),
    ),
    BottomTab(
        "financeiro", "Financeiro", Icons.Filled.AccountBalanceWallet,
        listOf(
            SectorTarget.Domain("financeiro", "Financeiro (Lançamentos)"),
            SectorTarget.Special("dre", "DRE"),
            SectorTarget.Special("analises", "Análises cruzadas"),
            SectorTarget.Special("nfe_import", "Importar NF-e (XML)"),
            SectorTarget.Domain("pedidos", "Pedidos"),
            SectorTarget.Domain("contratos", "Contratos"),
            SectorTarget.Domain("caixainterno", "Caixa Interno"),
            SectorTarget.Domain("cobrancas", "Cobranças"),
            SectorTarget.Domain("nfse", "NFS-e"),
        ),
    ),
    BottomTab(
        "estoque", "Estoque", Icons.Filled.Inventory2,
        listOf(
            SectorTarget.Domain("estoque", "Estoque"),
            SectorTarget.Domain("frota", "Frota"),
            SectorTarget.Domain("controleinterno", "Controle Interno"),
            SectorTarget.Domain("inventario", "Inventário"),
        ),
    ),
    BottomTab(
        "rh", "RH", Icons.Filled.People,
        listOf(SectorTarget.Domain("rh", "RH")),
    ),
)

/** As 3 únicas telas administrativas do site que o app ainda não replica
 * nativamente (Configurações/Base de Dados/Acessos) -- pedido do usuário:
 * "no botão módulos deixe apenas na lista suspensa configurações base de
 * dados e acessos". Sem tela nativa própria ainda, abrem no navegador do
 * aparelho (exige estar logado no site também pelo navegador -- são telas
 * raras de usar fora do computador, então esse atalho já resolve o essencial
 * sem duplicar todo o CRUD de administração em Kotlin agora). */
private val SISTEMA_LINKS = listOf(
    "configuracoes" to "Configurações",
    "base-de-dados" to "Base de Dados",
    "seguranca" to "Acessos",
)

@Composable
fun BRAgroBottomBar(
    currentDomainId: String?,
    onNavigateDomain: (String) -> Unit,
    onOpenDre: () -> Unit,
    onOpenAnalises: () -> Unit,
    onOpenNfeImport: () -> Unit,
    onOpenRomaneioQuick: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bridgeRepo = remember { BridgeRepository(context) }
    var openTabId by remember { mutableStateOf<String?>(null) }

    fun openSector(target: SectorTarget) {
        openTabId = null
        when (target) {
            is SectorTarget.Domain -> onNavigateDomain(target.domainId)
            is SectorTarget.Special -> when (target.routeKey) {
                "dre" -> onOpenDre()
                "analises" -> onOpenAnalises()
                "nfe_import" -> onOpenNfeImport()
                "romaneio_quick" -> onOpenRomaneioQuick()
            }
        }
    }

    NavigationBar {
        BOTTOM_TABS.forEach { tab ->
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
                    onClick = { openTabId = tab.id },
                    icon = { Icon(tab.icon, contentDescription = tab.label) },
                    // maxLines/softWrap: rótulos como "Financeiro" quebravam
                    // em 2 linhas com o espaçamento padrão -- pedido do
                    // usuário ("deixe apenas em uma linha").
                    label = { Text(tab.label, maxLines = 1, softWrap = false) },
                    colors = BottomNavColors,
                )
                DropdownMenu(expanded = openTabId == tab.id, onDismissRequest = { openTabId = null }) {
                    tab.items.forEach { item ->
                        val label = when (item) {
                            is SectorTarget.Domain -> item.label
                            is SectorTarget.Special -> item.label
                        }
                        val icon = when (item) {
                            is SectorTarget.Domain -> domainIcon(item.domainId)
                            is SectorTarget.Special -> tab.icon
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
        Box(modifier = Modifier.weight(1f)) {
            this@NavigationBar.NavigationBarItem(
                selected = false,
                onClick = { openTabId = if (openTabId == "sistema") null else "sistema" },
                icon = { Icon(Icons.Filled.GridView, contentDescription = "Módulos") },
                label = { Text("Módulos", maxLines = 1, softWrap = false) },
                colors = BottomNavColors,
            )
            DropdownMenu(expanded = openTabId == "sistema", onDismissRequest = { openTabId = null }) {
                SISTEMA_LINKS.forEach { (path, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                        onClick = {
                            openTabId = null
                            // Antes: Intent direto pro link simples, que abria
                            // sem sessão nenhuma no navegador (bearer token do
                            // app não é cookie) e caía no /login -- pedido do
                            // usuário ("estão sendo redirecionados para o app
                            // anterior"). Agora troca por um código de ponte
                            // que já abre logado (ver BridgeRepository.kt).
                            scope.launch {
                                val url = bridgeRepo.buildWebUrl(path)
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                        },
                    )
                }
            }
        }
    }
}
