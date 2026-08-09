package com.bragro.mobile.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/** Borda fina em TODO Card do app -- pedido do usuário ("borda verde bem
 * fina de acordo com o modo claro/escuro para não dar problema em todos os
 * blocos de toda app"). Em vez de editar centenas de chamadas Card(...)
 * espalhadas pelas telas, cada arquivo troca só o import de
 * androidx.compose.material3.Card por este (MESMA assinatura, MESMOS nomes
 * de parâmetro -- nenhuma chamada existente precisa mudar) e o app inteiro
 * ganha a borda de uma vez, sem duplicar o Card do Material3 (ele só
 * delega, acrescentando o `border` como default).
 *
 * Voltou a ser verde (BrGreen) -- pedido do usuário ("todas as bordas
 * finas contornadas com verde"), depois que a textura de fundo/bloco
 * passou a ser uma cor plana só (branco/quase-preto, ver Theme.kt) e a
 * borda ficou responsável por demarcar cada bloco.
 */
@Composable
private fun cardBorderColor(): androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary

@Composable
fun Card(
    modifier: Modifier = Modifier,
    shape: Shape = CardDefaults.shape,
    colors: CardColors = CardDefaults.cardColors(),
    elevation: CardElevation = CardDefaults.cardElevation(),
    border: BorderStroke = BorderStroke(1.dp, cardBorderColor()),
    content: @Composable ColumnScope.() -> Unit,
) {
    androidx.compose.material3.Card(
        modifier = modifier,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        content = content,
    )
}

@Composable
fun Card(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = CardDefaults.shape,
    colors: CardColors = CardDefaults.cardColors(),
    elevation: CardElevation = CardDefaults.elevatedCardElevation(),
    border: BorderStroke = BorderStroke(1.dp, cardBorderColor()),
    content: @Composable ColumnScope.() -> Unit,
) {
    androidx.compose.material3.Card(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        content = content,
    )
}
