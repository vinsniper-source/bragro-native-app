package com.bragro.mobile.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.bragro.mobile.ui.home.domainIcon

private data class BottomTab(val domainId: String, val label: String)

// Atalhos fixos na barra inferior, mesmo padrão da versão mobile do site
// (bottom-tab-bar.tsx): os módulos mais usados no dia a dia (Safra/Frota/
// Financeiro/Estoque/RH) + "Módulos" pra ver a lista completa dos ~18
// domínios (ver ModulosScreen.kt). Reaproveita os mesmos ícones de
// DomainVisuals.kt usados na tela Módulos, pra manter o mesmo visual.
private val BOTTOM_TABS = listOf(
    BottomTab("safra", "Safra"),
    BottomTab("frota", "Frota"),
    BottomTab("financeiro", "Financeiro"),
    BottomTab("estoque", "Estoque"),
    BottomTab("rh", "RH"),
)

@Composable
fun BRAgroBottomBar(
    currentDomainId: String?,
    isOnModulos: Boolean,
    onNavigateDomain: (String) -> Unit,
    onNavigateModulos: () -> Unit,
) {
    NavigationBar {
        BOTTOM_TABS.forEach { tab ->
            NavigationBarItem(
                selected = currentDomainId == tab.domainId,
                onClick = { onNavigateDomain(tab.domainId) },
                icon = { Icon(domainIcon(tab.domainId), contentDescription = tab.label) },
                label = { Text(tab.label) },
            )
        }
        NavigationBarItem(
            selected = isOnModulos,
            onClick = onNavigateModulos,
            icon = { Icon(Icons.Filled.GridView, contentDescription = "Módulos") },
            label = { Text("Módulos") },
        )
    }
}
