package com.bragro.mobile.ui.domain

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp

/**
 * Regra geral de texto (mockup v35, item 0): rótulo e valor ficam na mesma
 * linha só se couberem inteiros. Se não couberem, o valor desce inteiro para
 * a linha de baixo (alinhado à esquerda). Nada é cortado: o valor só quebra
 * entre palavras, nunca dentro de uma palavra ou número.
 */
@Composable
fun RotuloValor(
    rotulo: String,
    valor: String,
    modifier: Modifier = Modifier,
    corRotulo: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    corValor: Color = MaterialTheme.colorScheme.onSurface,
    estiloRotulo: TextStyle = MaterialTheme.typography.labelSmall,
    estiloValor: TextStyle = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
) {
    Layout(
        modifier = modifier.fillMaxWidth(),
        content = {
            Text(rotulo, style = estiloRotulo, color = corRotulo)
            Text(valor, style = estiloValor, color = corValor, textAlign = TextAlign.End)
        },
    ) { medidas, constraints ->
        val maxW = constraints.maxWidth
        val gap = 8.dp.roundToPx()
        val rotNatural = medidas[0].maxIntrinsicWidth(Constraints.Infinity)
        val valNatural = medidas[1].maxIntrinsicWidth(Constraints.Infinity)
        if (rotNatural + gap + valNatural <= maxW) {
            val rot = medidas[0].measure(Constraints(maxWidth = maxW))
            val val_ = medidas[1].measure(Constraints(maxWidth = maxW - rot.width - gap))
            val altura = maxOf(rot.height, val_.height)
            layout(maxW, altura) {
                rot.placeRelative(0, (altura - rot.height) / 2)
                val_.placeRelative(maxW - val_.width, (altura - val_.height) / 2)
            }
        } else {
            val rot = medidas[0].measure(Constraints(maxWidth = maxW))
            val val_ = medidas[1].measure(Constraints(maxWidth = maxW))
            layout(maxW, rot.height + val_.height) {
                rot.placeRelative(0, 0)
                val_.placeRelative(0, rot.height)
            }
        }
    }
}
