package com.bragro.mobile.ui.domain

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * v22 -- ícone de contorno com traço mais grosso ("negrito" minimalista):
 * o mesmo desenho repetido com deslocamentos de fração de dp engrossa o
 * traço sem preencher o ícone.
 */
/** v23 -- false no corpo do Início: lá os ícones ficam no traço fino. */
val LocalIconeNegrito = androidx.compose.runtime.compositionLocalOf { true }

@Composable
fun IconeNegrito(icon: ImageVector, contentDescription: String?, tint: Color, size: Dp, modifier: Modifier = Modifier) {
    if (!LocalIconeNegrito.current) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = modifier.size(size))
        return
    }
    val d = 0.45.dp
    Box(modifier.size(size)) {
        listOf(0.dp to 0.dp, d to 0.dp, -d to 0.dp, 0.dp to d, 0.dp to -d).forEachIndexed { i, (x, y) ->
            Icon(icon, contentDescription = if (i == 0) contentDescription else null, tint = tint, modifier = Modifier.size(size).offset(x, y))
        }
    }
}

/** v1.6.59 (site v302/v305) -- Salvar/Lançar como ✓ verde negrito (único
 * ícone verde). */
@Composable
fun SalvarIcone(onClick: () -> Unit, enabled: Boolean = true, loading: Boolean = false, descricao: String = "Salvar") {
    androidx.compose.material3.IconButton(onClick = onClick, enabled = enabled && !loading) {
        if (loading) androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        else IconeNegrito(
            androidx.compose.material.icons.Icons.Outlined.Check,
            contentDescription = descricao,
            tint = if (enabled) androidx.compose.material3.MaterialTheme.colorScheme.primary else androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            size = 26.dp,
        )
    }
}

/** ✕ fechar na cor do texto (substitui o botão Cancelar). */
@Composable
fun FecharIcone(onClick: () -> Unit, enabled: Boolean = true) {
    androidx.compose.material3.IconButton(onClick = onClick, enabled = enabled) {
        IconeNegrito(
            androidx.compose.material.icons.Icons.Outlined.Close,
            contentDescription = "Fechar",
            tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
            size = 22.dp,
        )
    }
}

/** Título de janela com as ações (✓ e ✕) na mesma linha, à direita. */
@Composable
fun TituloJanela(titulo: String, onFechar: (() -> Unit)? = null, fecharEnabled: Boolean = true, acoes: @Composable () -> Unit = {}) {
    androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        androidx.compose.material3.Text(titulo, modifier = Modifier.weight(1f))
        acoes()
        if (onFechar != null) FecharIcone(onFechar, fecharEnabled)
    }
}

/** Na barra do topo (TopAppBar): ✓ verde alinhado com a seta/título. */
@Composable
fun SalvarTopo(onClick: () -> Unit, enabled: Boolean = true, loading: Boolean = false, descricao: String = "Salvar") {
    androidx.compose.foundation.layout.Column {
        androidx.compose.foundation.layout.Spacer(Modifier.size(16.dp))
        SalvarIcone(onClick, enabled, loading, descricao)
    }
}

/** v1.6.59 (site v289, item 15) -- excluir confirma na própria linha: a
 * lixeira vira "Excluir? Sim  Não" no mesmo lugar, sem janela por cima.
 * Volta sozinha depois de alguns segundos. */
@Composable
fun ExcluirInline(onConfirm: () -> Unit, tamanhoBotao: Dp = 28.dp, tamanhoIcone: Dp = 16.dp) {
    var confirmando by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    if (!confirmando) {
        androidx.compose.material3.IconButton(onClick = { confirmando = true }, modifier = Modifier.size(tamanhoBotao)) {
            Icon(androidx.compose.material.icons.Icons.Outlined.Delete, contentDescription = "Excluir registro", modifier = Modifier.size(tamanhoIcone), tint = androidx.compose.material3.MaterialTheme.colorScheme.error)
        }
        return
    }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(6000)
        confirmando = false
    }
    val laranja = corAtencao()
    androidx.compose.foundation.layout.Row(
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(horizontal = 4.dp),
    ) {
        val estilo = androidx.compose.material3.MaterialTheme.typography.labelMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
        androidx.compose.material3.Text("Excluir?", style = estilo, color = laranja)
        androidx.compose.material3.Text(
            "Sim", style = estilo, color = laranja,
            modifier = Modifier.clickable { confirmando = false; onConfirm() }.padding(vertical = 6.dp),
        )
        androidx.compose.material3.Text(
            "Não", style = estilo, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clickable { confirmando = false }.padding(vertical = 6.dp),
        )
    }
}
