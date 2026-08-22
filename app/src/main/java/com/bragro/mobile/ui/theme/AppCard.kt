package com.bragro.mobile.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
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
 * Verde (MaterialTheme.colorScheme.primary, já ajustado por tema em
 * Theme.kt: mais escuro/saturado no claro, mais suave no escuro) -- pedido
 * do usuário ("todas as bordas finas contornadas com verde"), depois que a
 * textura de fundo/bloco passou a ser uma cor plana só (branco/quase-preto,
 * ver Theme.kt) e a borda ficou responsável por demarcar cada bloco.
 *
 * No modo escuro a borda ganha transparência (35%) além do verde já mais
 * suave -- pedido do usuário ("aumente a transparência, ex: 20% a 40% de
 * opacidade na borda... evita o efeito de vibração visual"). No claro
 * fica opaca, pra manter o contraste alto pedido também.
 */
@Composable
private fun cardBorderColor(): androidx.compose.ui.graphics.Color {
    val primary = MaterialTheme.colorScheme.primary
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (isDark) primary.copy(alpha = 0.35f) else primary
}

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

/** Cores compartilhadas de OutlinedTextField pro app inteiro fora do motor
 * genérico (DomainFormScreen.kt já tem sua própria versão, greenFieldColors)
 * -- pedido do usuário ("preeencha os campos da mesma cor dos blocos e
 * rretire as bordas odss campos"), estendido às telas de vários itens
 * (Pedido/Cotação/Nota) que montam seus próprios OutlinedTextField em vez de
 * usar o motor genérico. Container = colorScheme.surface (mesmo tom dos
 * Cards, ver Theme.kt) e borda transparente nos dois estados -- quem
 * demarca o campo agora é só o preenchimento, igual um bloco. */
@Composable
fun appFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Color.Transparent,
    unfocusedBorderColor = Color.Transparent,
    disabledBorderColor = Color.Transparent,
    focusedContainerColor = MaterialTheme.colorScheme.surface,
    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
    disabledContainerColor = MaterialTheme.colorScheme.surface,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    cursorColor = MaterialTheme.colorScheme.primary,
    focusedTrailingIconColor = MaterialTheme.colorScheme.primary,
)
