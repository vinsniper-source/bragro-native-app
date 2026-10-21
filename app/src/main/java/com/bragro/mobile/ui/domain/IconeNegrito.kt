package com.bragro.mobile.ui.domain

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
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
