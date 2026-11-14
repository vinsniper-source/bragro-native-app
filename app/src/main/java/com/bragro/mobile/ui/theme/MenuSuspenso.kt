package com.bragro.mobile.ui.theme

import androidx.compose.ui.graphics.luminance
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
    // v1.6.29 (mockup v132e) -- cor fixa (menus da barra/cabeçalho: branco no
    // claro, preto no escuro); null = regra de sempre (cor de onde abre).
    corFixa: androidx.compose.ui.graphics.Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val view = LocalView.current
    val bloco = LocalCorMenu.current?.takeIf { it.first === view }?.second
    val cor = corFixa ?: bloco ?: if (view is DialogWindowProvider) cs.surfaceContainerHigh else cs.background
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

/** v1.6.29 (mockup v132e) -- menus da barra de baixo e do cabeçalho: branco no modo claro, preto no escuro. */
@Composable
fun corMenuBarra(): androidx.compose.ui.graphics.Color =
    // v194 -- no claro, branco gelo (cor da barra inferior).
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) androidx.compose.ui.graphics.Color.Black else MaterialTheme.colorScheme.background

