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
import androidx.compose.ui.draw.shadow
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

/** Sombra verde (cor primária do app) em vez da sombra cinza padrão do
 * Material -- pedido do usuário ("aplique o verde de fundo nas sombras dos
 * campos não só nesse módulo mas em todos que esteja na mesma situação").
 * Nasceu como `greenCardShadow()` isolado em CotacaoMultiItemScreen.kt
 * (pedido anterior, só naquela tela: "troque a sombra do bloco pelo verde
 * da imagem"), mas como ESTE arquivo já é o Card compartilhado por TODO o
 * app (todo call site importa `com.bragro.mobile.ui.theme.Card` em vez do
 * Material3 puro -- ver comentário histórico abaixo), aplicar aqui uma vez
 * resolve pra todos os módulos de uma vez só, sem precisar caçar Card por
 * Card. A elevation real do Material3 Card fica sempre 0.dp (elevation do
 * parâmetro é ignorada de propósito) pra não desenhar duas sombras
 * empilhadas (cinza padrão + verde), mesma razão documentada no local de
 * origem. */
@Composable
private fun greenShadow(shape: Shape): Modifier {
    val green = MaterialTheme.colorScheme.primary
    return Modifier.shadow(elevation = 6.dp, shape = shape, ambientColor = green, spotColor = green)
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
        modifier = greenShadow(shape).then(modifier),
        shape = shape,
        colors = colors,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
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
        modifier = greenShadow(shape).then(modifier),
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
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
