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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

/** Borda fina verde em TODO Card do app -- pedido do usuário ("borda verde
 * bem fina de acordo com o modo claro/escuro para não dar problema em todos
 * os blocos de toda app"). Em vez de editar centenas de chamadas Card(...)
 * espalhadas pelas telas, cada arquivo troca só o import de
 * androidx.compose.material3.Card por este (MESMA assinatura, MESMOS nomes
 * de parâmetro -- nenhuma chamada existente precisa mudar) e o app inteiro
 * ganha a borda de uma vez, sem duplicar o Card do Material3 (ele só
 * delega, acrescentando o `border` como default).
 *
 * A cor do traço é derivada da luminância de `colorScheme.surface` (em vez
 * de `isSystemInDarkTheme()`) porque o app tem um seletor de tema próprio
 * (Automático/Claro/Escuro, ver Theme.kt/ThemeController) que pode divergir
 * do tema do sistema -- ler a luminância do scheme REALMENTE ativo garante
 * contraste correto em qualquer combinação, exatamente a preocupação do
 * usuário ("para não dar problema").
 */
@Composable
private fun cardBorderColor(): androidx.compose.ui.graphics.Color {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    // Verde mais escuro no modo escuro, mais claro no modo claro -- pedido
    // do usuário (antes usava o mesmo BrGreen nos dois, só variando o alpha).
    return if (isDark) BrGreenDark.copy(alpha = 0.85f) else BrGreenLight.copy(alpha = 0.55f)
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
