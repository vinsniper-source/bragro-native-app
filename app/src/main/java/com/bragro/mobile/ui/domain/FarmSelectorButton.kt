package com.bragro.mobile.ui.domain

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
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
fun FarmSelectorButton(showLabel: Boolean = false) {
    val context = LocalContext.current
    // Reativo (Flow/collectAsState), nao mais LaunchedEffect(Unit) + fetch
    // unico -- BUG real corrigido (pedido do usuario: "as listas suspensas
    // de todo app continuam desatualizadas, ainda ha fazendas que ja
    // exclui"): a leitura unica so pegava o que o Room tinha NAQUELE
    // instante e nunca mais recarregava sozinha, mesmo com o bootstrap em
    // segundo plano atualizando a tabela pouco depois. Ver comentario
    // completo em Daos.kt (FarmDao.observeAll).
    val farms by remember { ConfigRepository(context).observeFarms() }.collectAsState(initial = emptyList())
    var menuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { FarmSelection.load(context) }

    if (farms.isEmpty()) return

    val selected = FarmSelection.selected.value

    // LocationOn (pino de mapa), nao um icone de trator -- "Frota" ja usa
    // trator no menu/bottom nav (pedido do usuario: "troque o icone fazenda
    // por um mais relevante"); pino de local combina melhor com "escolher
    // uma fazenda", sem confundir com o modulo de frota.
    // Verde sempre -- pedido do usuario ("nos modulos coloque a cor verde no
    // icone fazenda"): antes so ficava verde (primary) com uma fazenda
    // filtrada selecionada; em "Todas as fazendas" (selected == null) caia
    // pra LocalContentColor (neutro), o que deixava o icone sem cor de
    // destaque na maior parte do tempo (estado padrao, sem filtro).
    if (showLabel) {
        Column(
            modifier = Modifier.clickable { menuOpen = true }.padding(horizontal = 6.dp, vertical = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Filled.LocationOn, contentDescription = "Filtrar por fazenda: ${selected ?: "todas"}", tint = MaterialTheme.colorScheme.primary)
            Text("Fazenda", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1)
        }
    } else {
        IconButton(onClick = { menuOpen = true }) {
            Icon(Icons.Filled.LocationOn, contentDescription = "Filtrar por fazenda: ${selected ?: "todas"}", tint = MaterialTheme.colorScheme.primary)
        }
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
