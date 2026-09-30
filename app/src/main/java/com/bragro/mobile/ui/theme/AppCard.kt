package com.bragro.mobile.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextOverflow
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

/** Dropdown pesquisável e EDITÁVEL, compartilhado por todo o app -- pedido
 * real do usuário ("todos os campos com lista suspensa... quando seleciono
 * o nome e preciso trocar não consigo excluir no cursor, e quando clico
 * vazio o app é fechado totalmente"). A causa raiz: dezenas de telas
 * (BaseDeDadosScreen, FinanceiroItensInline, CotacaoMultiItemScreen,
 * DreScreen, AnalisesScreen, OrcamentoScreen, NfeScreen, LivroCaixaScreen,
 * FieldviewScreen, PragaFotoScreen, RomaneioQuickScreen, DroneScreen,
 * ProviderIntegrationCard, DossieScreen, SegurancaScreen, PrescricaoNovoScreen,
 * QuickAbastecimentoDialog, PedidoMultiItemScreen, NfeImportScreen,
 * EstoqueFazendaExtras, HomeScreen, OrcamentoListScreen) montavam seu próprio
 * `ExposedDropdownMenuBox` com `readOnly = true` e `onValueChange = {}` --
 * backspace não tem efeito nenhum nesse estado (dá a impressão de "não
 * consigo apagar"). O único select do app que já era editável de verdade é o
 * branch "select" de DomainFormScreen.kt (task #26) -- esta função é
 * exatamente aquele mesmo padrão, já comprovado seguro (filtro nunca indexa
 * lista vazia, "(vazio)" limpa sem crashar, fecha sem match reverte pro
 * valor selecionado), extraído pra cá pra ser reusado por todas as telas
 * acima em vez de cada uma reimplementar (e errar) o próprio dropdown.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchableDropdownField(
    value: String,
    label: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    // Rótulo do item que limpa a seleção -- null = não mostrar essa opção
    // (telas onde o campo é obrigatório e não faz sentido "esvaziar").
    emptyOptionLabel: String? = "(nenhuma)",
    dense: Boolean = false,
) {
    val optionLabels = remember(options) { options.associate { it.first to it.second } }
    var expanded by remember { mutableStateOf(false) }
    var query by remember(value, options) { mutableStateOf(optionLabels[value] ?: value) }
    val filtered = remember(query, options) {
        val q = query.trim()
        if (q.isEmpty()) options else options.filter { it.second.contains(q, ignoreCase = true) }
    }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = query,
            onValueChange = { typed -> query = typed; expanded = true },
            label = { Text(label, style = if (dense) MaterialTheme.typography.labelSmall else LocalTextStyle.current, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            placeholder = placeholder?.let { p -> { Text(p) } },
            textStyle = if (dense) MaterialTheme.typography.bodySmall else LocalTextStyle.current,
            // autoCorrect = false -- mesmo motivo do select genérico em
            // DomainFormScreen.kt: evita o teclado (Gboard/Samsung Keyboard)
            // reinserir caracteres apagados por autocorreção, o que dava a
            // falsa impressão de "backspace não funciona".
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(autoCorrect = false),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            colors = appFieldColors(),
        )
        // DropdownMenu (não ExposedDropdownMenu) + exposedDropdownSize() --
        // ExposedDropdownMenu do material3 1.2.1 NÃO expõe `properties` (só
        // em versões mais novas), então precisou trocar pro DropdownMenu
        // "cru" com exposedDropdownSize() (replica a largura do campo
        // âncora) pra poder passar properties = PopupProperties(focusable =
        // false) -- mesmo fix e mesmo motivo do select genérico em
        // DomainFormScreen.kt (ver comentário lá).
        DropdownMenu(
            expanded = expanded,
            modifier = Modifier.exposedDropdownSize(),
            properties = androidx.compose.ui.window.PopupProperties(focusable = false),
            onDismissRequest = {
                expanded = false
                // Fechou sem escolher nada novo -- volta o texto pro que
                // realmente está selecionado (senão um texto digitado e não
                // confirmado ficaria "preso" no campo).
                query = optionLabels[value] ?: value
            },
        ) {
            if (emptyOptionLabel != null) {
                DropdownMenuItem(text = { Text(emptyOptionLabel) }, onClick = {
                    // try/catch -- mesmo fix do select genérico (evita
                    // fechar o app inteiro se onSelect("") lançar por algum
                    // efeito colateral inesperado do chamador).
                    try {
                        onSelect("")
                        query = ""
                    } catch (e: Exception) {
                        com.bragro.mobile.data.AppLog.e("SearchableDropdownField", "Erro ao esvaziar campo \"$label\"", e)
                    }
                    expanded = false
                })
            }
            if (filtered.isEmpty()) {
                DropdownMenuItem(text = { Text("Nenhum resultado", color = MaterialTheme.colorScheme.onSurfaceVariant) }, onClick = {}, enabled = false)
            }
            for ((optValue, optLabel) in filtered) {
                DropdownMenuItem(text = { Text(optLabel) }, onClick = {
                    try {
                        onSelect(optValue)
                        query = optLabel
                    } catch (e: Exception) {
                        com.bragro.mobile.data.AppLog.e("SearchableDropdownField", "Erro ao selecionar valor em \"$label\"", e)
                    }
                    expanded = false
                })
            }
        }
    }
}
