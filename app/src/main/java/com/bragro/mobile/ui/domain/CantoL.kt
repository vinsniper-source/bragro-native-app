package com.bragro.mobile.ui.domain

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * v21 -- no lugar das setinhas de abrir/recolher: um "L" discreto no canto
 * inferior direito, num tom bem próximo da borda do bloco. Recolhido, ganha
 * um leve toque de verde.
 */
@Composable
fun CantoL(aberto: Boolean, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val base = cs.outlineVariant
    val cor = if (aberto) cs.onSurface.copy(alpha = 0.18f).compositeOver(base) else cs.primary.copy(alpha = 0.45f).compositeOver(base)
    Canvas(modifier.size(14.dp).semantics { contentDescription = if (aberto) "Recolher" else "Abrir" }) {
        val w = 1.5.dp.toPx()
        val r = size.width - w / 2
        val b = size.height - w / 2
        val len = size.width * 0.72f
        drawLine(cor, Offset(r, b - len), Offset(r, b), strokeWidth = w, cap = StrokeCap.Round)
        drawLine(cor, Offset(r - len, b), Offset(r, b), strokeWidth = w, cap = StrokeCap.Round)
    }
}
