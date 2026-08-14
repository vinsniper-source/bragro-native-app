package com.bragro.mobile.ui.domain

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Grass
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bragro.mobile.data.local.LookupEntity
import com.bragro.mobile.data.repo.ConfigRepository

/**
 * Botão de filtro global genérico (Safra/Cultura) -- mesmo padrão visual de
 * [FarmSelectorButton], generalizado pra não duplicar a UI duas vezes. Só
 * aparece quando há pelo menos uma opção cadastrada na lista suspensa
 * correspondente (senão não há o que filtrar) -- mesma regra do de fazenda.
 * Verde sempre (tint = primary), mesma cor do ícone fazenda -- pedido do
 * usuário ("fazenda, safra e cultura, cor verde de fazenda").
 */
@Composable
fun GlobalFieldSelectorButton(selection: GlobalFieldSelection, icon: ImageVector, label: String, lookupCategory: String) {
    val context = LocalContext.current
    // Reativo -- mesmo motivo/comentario completo de FarmSelectorButton.kt
    // (bug de valor excluído continuando na lista suspensa/filtro).
    val options by remember { ConfigRepository(context).observeLookupsByCategory(lookupCategory) }.collectAsState(initial = emptyList())
    var menuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { selection.load(context) }

    if (options.isEmpty()) return

    val selected = selection.selected.value

    IconButton(onClick = { menuOpen = true }) {
        Icon(icon, contentDescription = "Filtrar por $label: ${selected ?: "todas"}", tint = MaterialTheme.colorScheme.primary)
    }
    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
        BasicText(
            text = "Filtrar por $label",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium),
        )
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text("Todas", fontWeight = if (selected == null) FontWeight.Bold else FontWeight.Normal) },
            onClick = { selection.choose(context, null); menuOpen = false },
        )
        HorizontalDivider()
        options.forEach { opt ->
            DropdownMenuItem(
                text = { Text(opt.label, fontWeight = if (selected == opt.value) FontWeight.Bold else FontWeight.Normal) },
                onClick = { selection.choose(context, opt.value); menuOpen = false },
            )
        }
    }
}

@Composable
fun SafraSelectorButton() = GlobalFieldSelectorButton(SafraSelection, Icons.Filled.Eco, "safra", "safras")

@Composable
fun CulturaSelectorButton() = GlobalFieldSelectorButton(CulturaSelection, Icons.Filled.Grass, "cultura", "culturas")
