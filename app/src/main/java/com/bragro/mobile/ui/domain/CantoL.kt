package com.bragro.mobile.ui.domain

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * v23 -- o L de abrir/recolher fica EM CIMA da borda do canto inferior
 * direito do bloco (Card), na cor do fundo do app: branco no modo claro,
 * cinza leve no modo escuro. Cada Card do app (ui.theme.Card) oferece um
 * [CantoHost]; o primeiro [CantoL] dentro dele "assume" o canto e deixa de
 * ser desenhado no meio do conteúdo. Fora de um Card, o L continua inline.
 */
@Stable
class CantoHost {
    var dono by mutableStateOf<Any?>(null)
    var aberto by mutableStateOf(true)
    var onClick by mutableStateOf<(() -> Unit)?>(null)
}

val LocalCantoHost = compositionLocalOf<CantoHost?> { null }

@Composable
fun corCantoBorda(): Color =
    if (MaterialTheme.colorScheme.background.luminance() > 0.5f) Color.White else Color(0xFF8A8F8C)

/** Desenha o L na borda inferior direita (acompanhando o arredondado) e
 * aceita toque nesse canto quando o dono do canto passou um onClick. */
@Composable
fun Modifier.cantoBorda(host: CantoHost, raio: Dp = 12.dp): Modifier {
    val cor = corCantoBorda()
    return this
        .drawWithContent {
            drawContent()
            if (host.dono != null) {
                val w = 2.2.dp.toPx()
                val i = 0.7.dp.toPx()
                val r = (raio.toPx() - i).coerceAtLeast(1f)
                val ext = 6.dp.toPx()
                val W = size.width - i
                val H = size.height - i
                val p = Path().apply {
                    moveTo(W, H - r - ext)
                    lineTo(W, H - r)
                    arcTo(Rect(W - 2 * r, H - 2 * r, W, H), 0f, 90f, false)
                    lineTo(W - r - ext, H)
                }
                drawPath(p, cor, style = Stroke(width = w, cap = StrokeCap.Round))
            }
        }
        .pointerInput(host) {
            // Só consome o toque quando ele cai no canto E o canto tem ação --
            // nos outros casos o evento segue para quem estiver por fora
            // (ex.: Card com clickable no modifier).
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = true)
                val zona = 36.dp.toPx()
                val noCanto = down.position.x > size.width - zona && down.position.y > size.height - zona
                if (!noCanto || host.onClick == null) return@awaitEachGesture
                val up = waitForUpOrCancellation()
                if (up != null) {
                    up.consume()
                    host.onClick?.invoke()
                }
            }
        }
}

@Composable
fun CantoL(aberto: Boolean, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, borda: Boolean = true) {
    // borda = false: L de linha/subitem (DRE, atividades, categorias...) que
    // fica sempre inline e não assume o canto do bloco.
    val host = if (borda) LocalCantoHost.current else null
    val chave = remember { Any() }
    if (host != null) {
        DisposableEffect(host) {
            if (host.dono == null) host.dono = chave
            onDispose { if (host.dono === chave) { host.dono = null; host.onClick = null } }
        }
        if (host.dono === chave) {
            SideEffect { host.aberto = aberto; host.onClick = onClick }
            return
        }
    }
    // Sem Card em volta (ou o canto já tem dono): L pequeno inline.
    val cs = MaterialTheme.colorScheme
    val base = cs.outlineVariant
    val cor = if (aberto) cs.onSurface.copy(alpha = 0.14f).compositeOver(base) else cs.primary.copy(alpha = 0.35f).compositeOver(base)
    val desenho: @Composable (Modifier) -> Unit = { m ->
        Canvas(m.size(8.dp).semantics { contentDescription = if (aberto) "Recolher" else "Abrir" }) {
            val sw = 1.2.dp.toPx()
            val r = size.width - sw / 2
            val b = size.height - sw / 2
            drawLine(cor, Offset(r, 0f), Offset(r, b), strokeWidth = sw, cap = StrokeCap.Round)
            drawLine(cor, Offset(0f, b), Offset(r, b), strokeWidth = sw, cap = StrokeCap.Round)
        }
    }
    if (onClick != null) {
        Box(modifier.size(28.dp).clickable { onClick() }, contentAlignment = Alignment.BottomEnd) { desenho(Modifier) }
    } else {
        desenho(modifier)
    }
}
