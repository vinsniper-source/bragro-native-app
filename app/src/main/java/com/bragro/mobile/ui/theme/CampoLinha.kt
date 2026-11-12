package com.bragro.mobile.ui.theme

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** v143 (mockup aprovado, opção B) -- regra geral do app: campo como linha
 * ("nome ______"), sem caixa. Todas as telas importam esta função com o
 * nome `OutlinedTextField` (mesmos parâmetros do Material 3), então trocar
 * aqui troca o app inteiro. Por baixo é o `TextField` do Material 3, que já
 * desenha só a linha embaixo; fundo transparente, linha cinza e verde no
 * foco (cores de [appFieldColors]). */

/** Cor da linha embaixo do campo (mesma da plataforma, --linha-campo). */
@Composable
fun linhaCampoColor(): Color =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFF6E716D) else Color(0xFF9A9E97)

/** true = o nome do campo já está escrito ao lado (formulários de registro),
 * então o campo não repete o rótulo dentro dele. */
val LocalNomeAoLado = compositionLocalOf { false }

private val SEM_CANTO: Shape = RoundedCornerShape(0.dp)

@Composable
fun LinhaTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    prefix: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    shape: Shape = SEM_CANTO,
    colors: TextFieldColors = appFieldColors(),
) {
    TextField(
        value = value, onValueChange = onValueChange, modifier = modifier, enabled = enabled, readOnly = readOnly,
        textStyle = textStyle, label = if (LocalNomeAoLado.current) null else label, placeholder = placeholder,
        leadingIcon = leadingIcon, trailingIcon = trailingIcon, prefix = prefix, suffix = suffix,
        supportingText = supportingText, isError = isError, visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions, keyboardActions = keyboardActions, singleLine = singleLine,
        maxLines = maxLines, minLines = minLines, interactionSource = interactionSource, shape = SEM_CANTO, colors = colors,
    )
}

@Composable
fun LinhaTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    prefix: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    shape: Shape = SEM_CANTO,
    colors: TextFieldColors = appFieldColors(),
) {
    TextField(
        value = value, onValueChange = onValueChange, modifier = modifier, enabled = enabled, readOnly = readOnly,
        textStyle = textStyle, label = if (LocalNomeAoLado.current) null else label, placeholder = placeholder,
        leadingIcon = leadingIcon, trailingIcon = trailingIcon, prefix = prefix, suffix = suffix,
        supportingText = supportingText, isError = isError, visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions, keyboardActions = keyboardActions, singleLine = singleLine,
        maxLines = maxLines, minLines = minLines, interactionSource = interactionSource, shape = SEM_CANTO, colors = colors,
    )
}

/** Formulário de registro: "nome ______" na mesma linha. O nome fica à
 * esquerda e o campo ocupa o resto (sem repetir o rótulo dentro). */
@Composable
fun NomeAoLado(nome: String, obrigatorio: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            nome + if (obrigatorio) " *" else "",
            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 18.dp),
        )
        CompositionLocalProvider(LocalNomeAoLado provides true) {
            Column(Modifier.weight(1f)) { content() }
        }
    }
}

/** Campo automático: nome em cinza, linha pontilhada e AUTO na ponta. */
@Composable
fun CampoAutoLinha(nome: String, valor: String) {
    val linha = linhaCampoColor()
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(nome, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        Row(
            Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .drawBehind {
                    val y = size.height - 1.dp.toPx()
                    drawLine(linha, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 3.dp.toPx())))
                }
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                valor.ifBlank { "—" },
                style = MaterialTheme.typography.bodyLarge,
                color = if (valor.isBlank() || valor == "—") cs.onSurfaceVariant.copy(alpha = 0.6f) else cs.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text("AUTO", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = cs.primary, letterSpacing = 0.6.sp)
        }
    }
}
