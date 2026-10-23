package com.bragro.mobile.ui.domain

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * v41 -- não existe mais marcador de abrir/recolher desenhado. Cada Card do
 * app (ui.theme.Card) oferece um [CantoHost]; o primeiro [CantoL] dentro dele
 * "assume" o bloco e passa a ditar duas coisas: o bloco inteiro responde ao
 * toque, e o bloco recolhido fica levemente apagado.
 */
@Stable
class CantoHost {
    var dono by mutableStateOf<Any?>(null)
    var aberto by mutableStateOf(true)
    var onClick by mutableStateOf<(() -> Unit)?>(null)
}

val LocalCantoHost = compositionLocalOf<CantoHost?> { null }

/** Apaga levemente o bloco recolhido e aceita o toque em qualquer ponto dele. */
@Composable
fun Modifier.cantoBorda(host: CantoHost, raio: Dp = 12.dp): Modifier {
    // v41 -- a curva no canto saiu de todo o app. O bloco recolhido fica
    // levemente apagado (só o suficiente para distinguir de um bloco aberto,
    // sem parecer desativado) e o toque vale no bloco inteiro.
    val apagado = host.dono != null && !host.aberto
    return this
        .graphicsLayer { alpha = if (apagado) 0.82f else 1f }
        .pointerInput(host) {
            // Toque em qualquer ponto do bloco abre e recolhe. Um botão ou
            // ícone dentro do bloco consome o próprio toque antes, então a
            // ação dele continua valendo.
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = true)
                if (host.onClick == null) return@awaitEachGesture
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
    // v41 -- fora de um Card (ou quando o bloco já tem dono), o L inline
    // também saiu: nada é desenhado.
}
