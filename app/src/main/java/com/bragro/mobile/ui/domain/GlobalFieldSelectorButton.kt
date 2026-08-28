package com.bragro.mobile.ui.domain

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
fun GlobalFieldSelectorButton(
    selection: GlobalFieldSelection,
    icon: ImageVector,
    label: String,
    lookupCategory: String,
    // Rótulo de texto abaixo do ícone -- pedido do usuário ("coloque
    // rótulos" nos ícones globais fazenda/safra/cultura do Início). Só o
    // Início usa showLabel=true; os demais módulos (TopAppBar) continuam só
    // com o ícone (sem espaço sobrando pra texto ali).
    showLabel: Boolean = false,
    // asPill/onChanged -- mesmo padrão de FarmSelectorButton.kt (ver
    // comentário completo lá): pedido do usuário ("substitua os ícones
    // fazenda, safra e cultura por esses filtros da plataforma").
    asPill: Boolean = false,
    onChanged: () -> Unit = {},
) {
    val context = LocalContext.current
    // Reativo -- mesmo motivo/comentario completo de FarmSelectorButton.kt
    // (bug de valor excluído continuando na lista suspensa/filtro).
    val options by remember { ConfigRepository(context).observeLookupsByCategory(lookupCategory) }.collectAsState(initial = emptyList())
    var menuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { selection.load(context) }

    if (options.isEmpty()) return

    val selected = selection.selected.value

    // Plural irregular só pra estes dois campos ("safra"->"safras",
    // "cultura"->"culturas") -- suficiente pro rótulo "Todas as X" bater com
    // o texto do site ("Todas as safras"/"Todas as culturas", ver
    // canvas-filters.tsx). Se um dia entrar um 3º campo global cujo plural
    // não seja "+s", ajustar aqui.
    val labelPlural = "${label}s"
    if (asPill) {
        Row(
            modifier = Modifier
                // widthIn(max=...) + Ellipsis -- mesmo fix de
                // FarmSelectorButton.kt (ver comentário completo lá): pedido
                // do usuário ("limite a tela, não deixe nenhum caractere
                // passar do limite da tela").
                .widthIn(max = 160.dp)
                .clip(RoundedCornerShape(999.dp))
                // Preenchimento em vez de borda -- pedido do usuário ("tire
                // todas as bordas de todo app"); mesmo critério de
                // FarmSelectorButton.kt (ver comentário completo lá).
                .background(MaterialTheme.colorScheme.surface)
                .clickable { menuOpen = true }
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                selected ?: "Todas as $labelPlural",
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Icon(Icons.Filled.ExpandMore, contentDescription = null, modifier = Modifier.size(14.dp).padding(start = 2.dp))
        }
    } else if (showLabel) {
        Column(
            modifier = Modifier.clickable { menuOpen = true }.padding(horizontal = 6.dp, vertical = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, contentDescription = "Filtrar por $label: ${selected ?: "todas"}", tint = MaterialTheme.colorScheme.primary)
            Text(
                label.replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
        }
    } else {
        IconButton(onClick = { menuOpen = true }) {
            Icon(icon, contentDescription = "Filtrar por $label: ${selected ?: "todas"}", tint = MaterialTheme.colorScheme.primary)
        }
    }
    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
        BasicText(
            text = "Filtrar por $label",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium),
        )
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text("Todas as $labelPlural", fontWeight = if (selected == null) FontWeight.Bold else FontWeight.Normal) },
            onClick = { selection.choose(context, null); menuOpen = false; onChanged() },
        )
        HorizontalDivider()
        options.forEach { opt ->
            DropdownMenuItem(
                // maxLines/ellipsis -- achado de auditoria (mesmo critério
                // de FarmSelectorButton.kt).
                text = {
                    Text(
                        opt.label,
                        fontWeight = if (selected == opt.value) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                onClick = { selection.choose(context, opt.value); menuOpen = false; onChanged() },
            )
        }
    }
}

@Composable
fun SafraSelectorButton(showLabel: Boolean = false, asPill: Boolean = false, onChanged: () -> Unit = {}) =
    GlobalFieldSelectorButton(SafraSelection, Icons.Filled.Eco, "safra", "safras", showLabel, asPill, onChanged)

@Composable
fun CulturaSelectorButton(showLabel: Boolean = false, asPill: Boolean = false, onChanged: () -> Unit = {}) =
    GlobalFieldSelectorButton(CulturaSelection, Icons.Filled.Grass, "cultura", "culturas", showLabel, asPill, onChanged)
