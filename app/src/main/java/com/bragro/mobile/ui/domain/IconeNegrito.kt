package com.bragro.mobile.ui.domain

import androidx.compose.ui.graphics.vector.path
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
import androidx.compose.ui.unit.sp

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
            // v272 (site v315, itens 7 e 10) -- sempre no verde da plataforma.
            tint = androidx.compose.material3.MaterialTheme.colorScheme.primary,
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
fun TituloJanela(titulo: String, onFechar: (() -> Unit)? = null, fecharEnabled: Boolean = true, dica: String? = null, acoes: @Composable () -> Unit = {}) {
    // v273 (mockup v328) -- título numa linha só, ⓘ quando há mensagem e os
    // ícones na mesma linha, à direita.
    androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        androidx.compose.foundation.layout.Row(modifier = Modifier.weight(1f), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            // v281 (mockup v343) -- nas janelas, "Novo"/"Nova" vira o ícone novo + hífen.
            val novo = Regex("^Nov[oa] ", RegexOption.IGNORE_CASE).containsMatchIn(titulo)
            val tituloExib = if (novo) titulo.substring(5).replaceFirstChar { it.uppercase() } else titulo
            if (novo) NovoLancamentoIcone()
            androidx.compose.material3.Text(tituloExib, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, maxLines = 1, /* v283 (mockup v355 item 3) -- negrito */ overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            if (!dica.isNullOrBlank()) com.bragro.mobile.ui.theme.DicaInfo(dica)
        }
        androidx.compose.runtime.CompositionLocalProvider(LocalIconeNegrito provides true) {
            acoes()
            if (onFechar != null) FecharIcone(onFechar, fecharEnabled)
        }
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
            Icon(androidx.compose.material.icons.Icons.Outlined.Delete, contentDescription = "Excluir registro", modifier = Modifier.size(tamanhoIcone), tint = corAtencao())
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

/**
 * v274 (mockup v329) -- ícone do módulo com um "+" elevado: novo lançamento.
 * Fica na linha do título (lista e formulário), na cor do texto.
 */
@Composable
fun NovoModuloIcone(domainId: String, onClick: (() -> Unit)? = null) {
    val conteudo: @Composable () -> Unit = {
        androidx.compose.foundation.layout.Box {
            IconeNegrito(com.bragro.mobile.ui.home.domainIcon(domainId), contentDescription = if (onClick != null) "Novo lançamento" else null, tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurface, size = 22.dp)
            androidx.compose.material3.Text(
                "+",
                fontSize = 13.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.align(androidx.compose.ui.Alignment.TopEnd).offset(x = 8.dp, y = (-9).dp),
            )
        }
    }
    if (onClick != null) androidx.compose.material3.IconButton(onClick = onClick) { conteudo() }
    else androidx.compose.foundation.layout.Box(Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) { conteudo() }
}


/** v278 (mockups v335 itens 9 e 15) -- caixinha de marcar sem quadrado e sem
 * fundo: só o ✓, cinza desmarcado e verde marcado. `colors` é ignorado (fica
 * só pra trocar Checkbox(...) por VistoCheck(...) sem mexer nos argumentos). */
@Composable
fun VistoCheck(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @Suppress("UNUSED_PARAMETER") colors: Any? = null,
) {
    val cor = if (checked) androidx.compose.material3.MaterialTheme.colorScheme.primary
        else androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    val icone: @Composable () -> Unit = {
        Icon(androidx.compose.material.icons.Icons.Outlined.Check, contentDescription = if (checked) "Marcado" else "Desmarcado", tint = cor, modifier = Modifier.size(22.dp))
    }
    if (onCheckedChange != null) {
        androidx.compose.material3.IconButton(onClick = { onCheckedChange(!checked) }, enabled = enabled, modifier = modifier) { icone() }
    } else {
        androidx.compose.foundation.layout.Box(modifier = modifier.size(40.dp), contentAlignment = androidx.compose.ui.Alignment.Center) { icone() }
    }
}


/** v278 (mockup v336) -- ícone "novo lançamento": V vazado com seta na ponta.
 * Fica antes do título dos formulários de novo lançamento (não é botão). */
private fun androidx.compose.ui.graphics.vector.ImageVector.Builder.traco(corpo: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit) {
    path(
        fill = null,
        stroke = androidx.compose.ui.graphics.SolidColor(androidx.compose.ui.graphics.Color.Black),
        strokeLineWidth = 1.6f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
        pathBuilder = corpo,
    )
}

val NovoLancamentoVetor: androidx.compose.ui.graphics.vector.ImageVector by lazy {
    androidx.compose.ui.graphics.vector.ImageVector.Builder(
        name = "NovoLancamento", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        // v279 (mockup v338) -- folha com a dobra no canto e o traço (imagem 8).
        traco { moveTo(4f, 3f); lineTo(15f, 3f); lineTo(20f, 8f); lineTo(20f, 21f); lineTo(4f, 21f); close() }
        traco { moveTo(15f, 3f); lineTo(15f, 8f); lineTo(20f, 8f) }
        // v281 (mockup v343) -- sem o traço diagonal: folha com dobra, como o do Word.
    }.build()
}

@Composable
fun NovoLancamentoIcone(modifier: Modifier = Modifier) {
    // v279 (mockup v338) -- ícone, tracinho e o título.
    androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, modifier = modifier) {
        Icon(NovoLancamentoVetor, contentDescription = "Novo lançamento", tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
        androidx.compose.material3.Text(" - ", color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
