package com.bragro.mobile.ui.domain

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp

/**
 * v39 -- regra de texto de todo o app, igual em qualquer tela:
 *
 * 1. Tudo encostado na margem ESQUERDA. Nada centralizado, nada à direita,
 *    nenhum letreiro deslizante.
 * 2. O valor vem logo depois dos dois-pontos, na mesma linha, se couber
 *    inteiro.
 * 3. Se não couber, o valor começa na LINHA DE BAIXO, a partir da mesma
 *    margem do rótulo, e segue por quantas linhas precisar.
 * 4. A quebra acontece só entre palavras: palavra e número nunca se partem,
 *    e nada vira coluna de uma letra por linha.
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
    RotuloValorSlot(
        rotulo = rotulo,
        modifier = modifier,
        corRotulo = corRotulo,
        estiloRotulo = estiloRotulo,
    ) {
        Text(valor, style = estiloValor, color = corValor)
    }
}

/** Mesma regra, com um estilo único para rótulo e valor (campos de cartão). */
@Composable
fun RotuloValorLinha(
    rotulo: String,
    valor: String,
    modifier: Modifier = Modifier,
    estilo: TextStyle = MaterialTheme.typography.bodySmall,
    corValor: Color = Color.Unspecified,
) {
    RotuloValorSlot(
        rotulo = rotulo,
        modifier = modifier,
        corRotulo = MaterialTheme.colorScheme.onSurfaceVariant,
        estiloRotulo = estilo,
    ) {
        Text(valor, style = estilo.copy(fontWeight = FontWeight.Bold), color = corValor)
    }
}

/** Mesma regra, com o valor em conteúdo livre (ex.: dois valores lado a lado). */
@Composable
fun RotuloValorSlot(
    rotulo: String,
    modifier: Modifier = Modifier,
    corRotulo: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    estiloRotulo: TextStyle = MaterialTheme.typography.labelSmall,
    valor: @Composable () -> Unit,
) {
    Layout(
        modifier = modifier.fillMaxWidth(),
        content = {
            Text("$rotulo:", style = estiloRotulo, color = corRotulo)
            Box { valor() }
        },
    ) { medidas, constraints ->
        val maxW = constraints.maxWidth
        val espaco = 4.dp.roundToPx()
        // Largura que cada parte ocuparia sem nenhuma quebra.
        val rotInteiro = medidas[0].maxIntrinsicWidth(Constraints.Infinity)
        val valInteiro = medidas[1].maxIntrinsicWidth(Constraints.Infinity)
        if (rotInteiro + espaco + valInteiro <= maxW) {
            // Cabem os dois inteiros: valor logo depois do rótulo.
            val rot = medidas[0].measure(Constraints(maxWidth = maxW))
            val val_ = medidas[1].measure(Constraints(maxWidth = maxW - rot.width - espaco))
            layout(maxW, maxOf(rot.height, val_.height)) {
                rot.placeRelative(0, 0)
                val_.placeRelative(rot.width + espaco, 0)
            }
        } else {
            // Não cabem: o valor desce inteiro e começa na margem, usando
            // quantas linhas forem necessárias.
            val rot = medidas[0].measure(Constraints(maxWidth = maxW))
            val val_ = medidas[1].measure(Constraints(maxWidth = maxW))
            layout(maxW, rot.height + val_.height) {
                rot.placeRelative(0, 0)
                val_.placeRelative(0, rot.height)
            }
        }
    }
}
