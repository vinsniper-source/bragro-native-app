package com.bragro.mobile.ui.domain

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bragro.mobile.data.local.FarmEntity
import com.bragro.mobile.data.repo.ConfigRepository

/**
 * Botao de filtro global de fazenda -- equivalente ao FarmSelector do site
 * (cabecalho), ver comentario completo em FarmSelection.kt. Reutilizado no
 * topo de Inicio e de cada tela de modulo. So aparece quando ha pelo menos
 * uma fazenda cadastrada (senao nao ha o que filtrar).
 */
@Composable
fun FarmSelectorButton() {
    val context = LocalContext.current
    var farms by remember { mutableStateOf<List<FarmEntity>>(emptyList()) }
    var menuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        FarmSelection.load(context)
        farms = ConfigRepository(context).farms()
    }

    if (farms.isEmpty()) return

    val selected = FarmSelection.selected.value

    // LocationOn (pino de mapa), nao um icone de trator -- "Frota" ja usa
    // trator no menu/bottom nav (pedido do usuario: "troque o icone fazenda
    // por um mais relevante"); pino de local combina melhor com "escolher
    // uma fazenda", sem confundir com o modulo de frota.
    IconButton(onClick = { menuOpen = true }) {
        Icon(Icons.Filled.LocationOn, contentDescription = "Filtrar por fazenda: ${selected ?: "todas"}", tint = if (selected != null) MaterialTheme.colorScheme.primary else LocalContentColor.current)
    }
    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
        BasicText(
            text = "Filtrar por fazenda",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium),
        )
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text("Todas as fazendas", fontWeight = if (selected == null) FontWeight.Bold else FontWeight.Normal) },
            onClick = { FarmSelection.choose(context, null); menuOpen = false },
        )
        HorizontalDivider()
        farms.forEach { farm ->
            DropdownMenuItem(
                text = { Text(farm.name, fontWeight = if (selected == farm.name) FontWeight.Bold else FontWeight.Normal) },
                onClick = { FarmSelection.choose(context, farm.name); menuOpen = false },
            )
        }
    }
}
