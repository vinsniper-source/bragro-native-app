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

/** SEM borda em TODO Card do app -- pedido do usuário ("tire todas as
 * bordas de todo app"), que reverte a fase anterior deste arquivo (borda
 * verde fina por padrão). Vários call sites (Início, calculadoras) já
 * vinham sobrescrevendo a borda pra Transparent manualmente; blocos de
 * ícone/rótulo (ModuleIconButton/LabeledIconButton em ModuleIconRow.kt) e
 * cards de outros módulos (ex.: ModuloCard em ModulosScreen.kt) NÃO
 * sobrescreviam e continuavam com a borda verde antiga -- esse era o "faltou
 * alguns módulos que não retiraram as bordas" relatado pelo usuário. Como
 * TODO Card do app passa por aqui (mesmo padrão de import-swap descrito
 * antes), zerar o default de uma vez só resolve pra tudo, sem precisar
 * caçar call site por call site. Mesmo critério do site (globals.css:
 * `--border: transparent`).
 *
 * cardBorderColor() fica só como referência caso o usuário peça borda de
 * volta em algum ponto específico no futuro -- nenhum call site usa mais.
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
    border: BorderStroke = BorderStroke(0.dp, Color.Transparent),
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
    border: BorderStroke = BorderStroke(0.dp, Color.Transparent),
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
