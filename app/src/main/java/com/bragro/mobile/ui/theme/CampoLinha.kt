package com.bragro.mobile.ui.theme

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.getValue
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.graphics.SolidColor
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

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
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
    if (LocalNomeAoLado.current) {
        // v147 (opção A) -- dentro do formulário: linha colada no texto.
        val st = textStyle.merge(TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp))
        BasicTextField(
            value = value, onValueChange = onValueChange, modifier = modifier.fillMaxWidth(), enabled = enabled, readOnly = readOnly,
            textStyle = st, cursorBrush = SolidColor(MaterialTheme.colorScheme.primary), visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions, keyboardActions = keyboardActions, singleLine = singleLine, maxLines = maxLines, minLines = minLines,
            interactionSource = interactionSource,
            decorationBox = { inner ->
                // v159 (mockup aprovado) -- decoração própria: setinha/calendário
                // pequenos em cima da linha (o padrão do Material reserva 48 dp
                // para o ícone e empurrava a linha para longe do nome).
                DecoracaoLinha(value.isEmpty(), inner, placeholder, leadingIcon, trailingIcon, prefix, suffix, supportingText, isError, enabled, interactionSource)
            },
        )
        return
    }
    TextField(
        value = value, onValueChange = onValueChange, modifier = modifier, enabled = enabled, readOnly = readOnly,
        textStyle = textStyle, label = if (LocalNomeAoLado.current) null else label, placeholder = placeholder,
        leadingIcon = leadingIcon, trailingIcon = trailingIcon, prefix = prefix, suffix = suffix,
        supportingText = supportingText, isError = isError, visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions, keyboardActions = keyboardActions, singleLine = singleLine,
        maxLines = maxLines, minLines = minLines, interactionSource = interactionSource, shape = SEM_CANTO, colors = colors,
    )
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
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
    if (LocalNomeAoLado.current) {
        // v147 (opção A) -- dentro do formulário: linha colada no texto.
        val st = textStyle.merge(TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp))
        BasicTextField(
            value = value, onValueChange = onValueChange, modifier = modifier.fillMaxWidth(), enabled = enabled, readOnly = readOnly,
            textStyle = st, cursorBrush = SolidColor(MaterialTheme.colorScheme.primary), visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions, keyboardActions = keyboardActions, singleLine = singleLine, maxLines = maxLines, minLines = minLines,
            interactionSource = interactionSource,
            decorationBox = { inner ->
                // v159 (mockup aprovado) -- decoração própria: setinha/calendário
                // pequenos em cima da linha (o padrão do Material reserva 48 dp
                // para o ícone e empurrava a linha para longe do nome).
                DecoracaoLinha(value.text.isEmpty(), inner, placeholder, leadingIcon, trailingIcon, prefix, suffix, supportingText, isError, enabled, interactionSource)
            },
        )
        return
    }
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
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            nome + if (obrigatorio) " *" else "",
            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 5.dp),
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
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(nome, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 5.dp))
        Row(
            Modifier
                .weight(1f)
                .heightIn(min = 28.dp)
                .drawBehind {
                    val y = size.height - 1.dp.toPx()
                    drawLine(linha, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 3.dp.toPx())))
                }
                .padding(start = 2.dp, end = 2.dp, top = 4.dp, bottom = 1.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            // v152 (mockup aprovado) -- sem AUTO: vazio mostra só um traço
            // verde no centro; preenchido mostra o valor.
            if (valor.isBlank() || valor == "—") {
                Text("—", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = cs.primary, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.weight(1f))
            } else {
                Text(valor, fontSize = 15.sp, color = cs.onSurface, modifier = Modifier.weight(1f))
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun DecoracaoLinha(
    vazio: Boolean,
    inner: @Composable () -> Unit,
    placeholder: @Composable (() -> Unit)?,
    leadingIcon: @Composable (() -> Unit)?,
    trailingIcon: @Composable (() -> Unit)?,
    prefix: @Composable (() -> Unit)?,
    suffix: @Composable (() -> Unit)?,
    supportingText: @Composable (() -> Unit)?,
    isError: Boolean,
    enabled: Boolean,
    interactionSource: MutableInteractionSource,
) {
    val foco by interactionSource.collectIsFocusedAsState()
    val cs = MaterialTheme.colorScheme
    val linha = linhaCampoColor()
    val cor = when {
        isError -> cs.error
        foco -> cs.primary
        !enabled -> linha.copy(alpha = 0.5f)
        else -> linha
    }
    val esp = if (foco) 2.dp else 1.5.dp
    // Ícone ocupa 22 dp na altura (desenha por cima; o toque continua de 48 dp).
    @Composable
    fun Icone(c: @Composable () -> Unit) {
        CompositionLocalProvider(
            androidx.compose.material3.LocalMinimumInteractiveComponentEnforcement provides false,
            androidx.compose.material3.LocalContentColor provides cs.primary,
        ) {
            Box(Modifier.width(28.dp).height(22.dp).wrapContentSize(unbounded = true), contentAlignment = Alignment.Center) { c() }
        }
    }
    Column {
        // v163 (mockup aprovado) -- a linha termina antes da setinha/calendário:
        // o ícone fica ao lado, 6 dp afastado e um pouco acima, sem linha embaixo.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .weight(1f)
                    .drawBehind {
                        val y = size.height - esp.toPx() / 2
                        drawLine(cor, Offset(0f, y), Offset(size.width, y), strokeWidth = esp.toPx())
                    }
                    .padding(start = 2.dp, end = 2.dp, top = 4.dp, bottom = 1.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (leadingIcon != null) Icone(leadingIcon)
                if (prefix != null) prefix()
                Box(Modifier.weight(1f).heightIn(min = 22.dp), contentAlignment = Alignment.CenterStart) {
                    if (vazio && placeholder != null) {
                        CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides cs.onSurfaceVariant.copy(alpha = 0.7f)) {
                            androidx.compose.material3.ProvideTextStyle(TextStyle(fontSize = 15.sp, color = cs.onSurfaceVariant.copy(alpha = 0.7f))) { placeholder() }
                        }
                    }
                    inner()
                }
                if (suffix != null) suffix()
            }
            if (trailingIcon != null) {
                Box(Modifier.padding(start = 6.dp).offset(y = (-2).dp)) { Icone(trailingIcon) }
            }
        }
        if (supportingText != null) {
            androidx.compose.material3.ProvideTextStyle(MaterialTheme.typography.labelSmall) {
                Box(Modifier.padding(top = 2.dp)) { supportingText() }
            }
        }
    }
}
