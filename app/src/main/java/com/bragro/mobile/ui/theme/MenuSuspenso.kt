package com.bragro.mobile.ui.theme

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.PopupProperties

/** Cor do bloco (Card) em volta, junto com a View onde ele foi desenhado --
 * assim uma janela aberta de dentro de um bloco não herda a cor dele. */
val LocalCorMenu = compositionLocalOf<Pair<android.view.View, Color>?> { null }

/**
 * v1.6.16/v1.6.17 (mockups v119 e v120) -- lista suspensa na cor de onde
 * abre: dentro de um bloco, na cor do bloco; dentro de uma janela, na cor da
 * janela; fora disso, na cor do fundo da tela.
 */
@Composable
fun MenuSuspenso(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
    properties: PopupProperties = PopupProperties(focusable = true),
    content: @Composable ColumnScope.() -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val view = LocalView.current
    val bloco = LocalCorMenu.current?.takeIf { it.first === view }?.second
    val cor = bloco ?: if (view is DialogWindowProvider) cs.surfaceContainerHigh else cs.background
    MaterialTheme(colorScheme = cs.copy(surfaceContainer = cor)) {
        androidx.compose.material3.DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            offset = offset,
            properties = properties,
            content = content,
        )
    }
}
