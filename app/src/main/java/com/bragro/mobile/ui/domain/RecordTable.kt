package com.bragro.mobile.ui.domain

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.ScrollState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bragro.mobile.data.model.ColumnConfig

// Vista Tabela (grade real, colunas fixas + rolagem horizontal
// compartilhada) -- pedido do usuário (achado de auditoria: "insira tambem
// o icone e rótulo tabela intercalando com coluna, no mesmo modelo de
// expandir e recolher no mesmo botao, coloque em todos os módulos"):
// alternativa à vista em cards (padrão), réplica simplificada de
// data-table.tsx (site), adaptada pro toque/tela pequena do celular. Largura
// FIXA por coluna (em vez de medida intrínseca, que o LazyColumn não
// suporta entre itens diferentes) garante que cabeçalho e linhas fiquem
// sempre alinhados -- cabeçalho e cada linha compartilham o MESMO
// ScrollState (hoisted no chamador, ver DomainListScreen.kt/
// FinanceiroScreen.kt), então arrastar qualquer um dos dois rola todos
// juntos. Mesmo critério "retângulo com bordas" do resto do redesign (ver
// ModuleIconRow.kt) -- células com borda fina, sem cantos arredondados.
private val TABLE_CELL_WIDTH = 130.dp
private val TABLE_ACTIONS_WIDTH = 100.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecordTableHeader(columns: List<ColumnConfig>, hScroll: ScrollState, showActions: Boolean = true) {
    // Borda mais escura que o PRÓPRIO fundo da célula (mesmo critério de
    // ModuleIconRow.kt/darkerBorderColor) -- achado de auditoria ("as linhas
    // das tabelas estão desconfiguradas"): outlineVariant ficava quase
    // idêntico ao fundo no tema escuro do app, então a grade inteira
    // parecia sem bordas nenhuma.
    val headerBg = MaterialTheme.colorScheme.surfaceVariant
    val dividerColor = darkerBorderColor(headerBg)
    Row(modifier = Modifier.horizontalScroll(hScroll)) {
        columns.forEach { col ->
            Box(
                modifier = Modifier
                    .width(TABLE_CELL_WIDTH)
                    .background(headerBg)
                    .border(1.dp, dividerColor)
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    col.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.basicMarquee(),
                )
            }
        }
        // Coluna de ações fica no cabeçalho também, pra reservar o mesmo
        // espaço das linhas (senão o cabeçalho ficaria mais curto que
        // elas) -- só quando a linha realmente tem ações (ver showActions
        // em RecordTableRow: dados AGRUPADOS/computados, como
        // OperacaoAgrupadaData em Operações, não têm um registro único
        // editável/excluível, então não faz sentido reservar essa coluna).
        if (showActions) {
            Box(
                modifier = Modifier
                    .width(TABLE_ACTIONS_WIDTH)
                    .background(headerBg)
                    .border(1.dp, dividerColor)
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    "Ações",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecordTableRow(
    columns: List<ColumnConfig>,
    record: Map<String, String?>,
    domainId: String,
    hScroll: ScrollState,
    // Nulos pra linhas de dados AGRUPADOS/computados (ex.: OperacaoAgrupadaData
    // em Operações) -- não existe um registro único por trás pra ver/editar/
    // excluir, então a coluna de ações inteira some (ver showActions).
    onView: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    val rowBg = MaterialTheme.colorScheme.surface
    val dividerColor = darkerBorderColor(rowBg)
    val showActions = onView != null || onEdit != null || onDelete != null
    Row(modifier = Modifier.horizontalScroll(hScroll)) {
        columns.forEach { col ->
            Box(
                modifier = Modifier
                    .width(TABLE_CELL_WIDTH)
                    .background(rowBg)
                    .border(1.dp, dividerColor)
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                RecordTableCellValue(col, record[col.key] ?: "", record, domainId)
            }
        }
        if (showActions) {
            Box(
                modifier = Modifier
                    .width(TABLE_ACTIONS_WIDTH)
                    .background(rowBg)
                    .border(1.dp, dividerColor)
                    .padding(horizontal = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row {
                    if (onView != null) {
                        IconButton(onClick = onView, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Filled.Visibility, contentDescription = "Ver lançamento completo", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    if (onEdit != null) {
                        IconButton(onClick = onEdit, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Filled.Edit, contentDescription = "Editar lançamento", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    if (onDelete != null) {
                        IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Filled.Delete, contentDescription = "Excluir lançamento", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

// Reaproveita a MESMA lógica de formatação/badge/barra de progresso do
// RecordFieldLine (card view) -- mesmo critério pra valor não bater
// diferente entre as duas vistas (ver DomainListScreen.kt).
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecordTableCellValue(col: ColumnConfig, value: String, record: Map<String, String?>, domainId: String) {
    if (value.isBlank()) {
        Text("—", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val progress = if (domainId.isNotEmpty()) domainProgressCellInfo(domainId, col.key, record) else null
    when {
        progress != null -> DomainProgressCell(col.label, progress)
        isStatusLikeColumn(col.key) -> StatusBadge(value)
        else -> {
            val displayValue = if (col.money) formatMoneyValue(value) else displayValueFor(col.key, value, col.type)
            Text(
                displayValue,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                // BUG real de auditoria ("as linhas das tabelas estão
                // desconfiguradas"): sem `color` explícito aqui, o texto
                // herdava o LocalContentColor ambiente do resto da tela (não
                // o desta célula específica) -- num fundo `surface` escuro,
                // esse tom ambiente ficava ilegível/invisível na maioria das
                // colunas (só "—" e os badges de status, que já tinham cor
                // própria, apareciam). onSurface = mesma cor que qualquer
                // outro texto normal sobre um fundo `surface` no app.
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                modifier = Modifier.basicMarquee(),
            )
        }
    }
}
