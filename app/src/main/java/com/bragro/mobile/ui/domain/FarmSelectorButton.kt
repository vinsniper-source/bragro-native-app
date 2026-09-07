package com.bragro.mobile.ui.domain

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
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
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bragro.mobile.data.local.FarmEntity
import com.bragro.mobile.data.repo.ConfigRepository

/**
 * Botao de filtro global de fazenda -- equivalente ao FarmSelector do site
 * (cabecalho), ver comentario completo em FarmSelection.kt. Reutilizado no
 * topo de Inicio e de cada tela de modulo. So aparece quando ha pelo menos
 * uma fazenda cadastrada (senao nao ha o que filtrar).
 *
 * asPill=true -- pedido do usuário ("substitua os ícones fazenda, safra e
 * cultura por esses filtros da plataforma"): visual tipo "chip" com o VALOR
 * atual escrito por extenso ("Todas as fazendas"/nome da fazenda) + seta,
 * igual ao FarmSelector do cabeçalho web (topbar.tsx) -- em vez do ícone +
 * rótulo fixo "Fazenda" (showLabel=true, mantido intacto pra quem ainda usa
 * esse visual, ex. TopAppBar de outros módulos). onChanged é chamado depois
 * de escolher uma fazenda -- quem usa a pill (Início) passa aqui o callback
 * que refaz o fetch do Canvas já filtrado (ver HomeViewModel.onFiltroGlobalChanged).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FarmSelectorButton(showLabel: Boolean = false, asPill: Boolean = false, onChanged: () -> Unit = {}) {
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
    if (asPill) {
        Row(
            modifier = Modifier
                // widthIn(max=...) -- pedido do usuário ("limite a tela, não
                // deixe nenhum caractere passar do limite da tela"): sem
                // limite, um nome de fazenda longo media a Text no tamanho
                // intrínseco (1 linha) e empurrava a pill inteira pra fora
                // da tela em vez de truncar com "...". Aumentado de 160dp
                // pra 200dp e padding/fonte maiores -- pedido do usuário
                // ("acrescente tamanho do campo fazenda da lista suspensa da
                // página principal do app"): só este pill (Início), não o
                // de Safra/Cultura (GlobalFieldSelectorButton.kt).
                .widthIn(max = 200.dp)
                .clip(RoundedCornerShape(999.dp))
                // Preenchimento em vez de borda -- pedido do usuário ("tire
                // todas as bordas de todo app"); sem fundo a pill ficaria sem
                // nenhum contorno visível (mesmo erro já corrigido antes nos
                // Cards, ver AppCard.kt).
                .background(MaterialTheme.colorScheme.surface)
                .clickable { menuOpen = true }
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                selected ?: "Todas as fazendas",
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier.weight(1f, fill = false).basicMarquee(),
            )
            Icon(Icons.Filled.ExpandMore, contentDescription = null, modifier = Modifier.size(18.dp).padding(start = 3.dp))
        }
    } else if (showLabel) {
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
            onClick = { FarmSelection.choose(context, null); menuOpen = false; onChanged() },
        )
        HorizontalDivider()
        farms.forEach { farm ->
            DropdownMenuItem(
                // maxLines/ellipsis -- achado de auditoria (nome de fazenda
                // pode ser bem mais longo que "Todas as fazendas" acima, sem
                // proteção nenhuma contra quebra de linha no menu).
                text = {
                    Text(
                        farm.name,
                        fontWeight = if (selected == farm.name) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                onClick = { FarmSelection.choose(context, farm.name); menuOpen = false; onChanged() },
            )
        }
    }
}
