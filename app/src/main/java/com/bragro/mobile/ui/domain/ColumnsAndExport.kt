package com.bragro.mobile.ui.domain

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bragro.mobile.data.model.ColumnConfig
import com.bragro.mobile.ui.util.shareTextFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Botão "Colunas" -- espelho do dropdown de checkboxes de data-table.tsx
 * ("Mostrar campos — mesma ordem do Lançamento"), pedido do usuário
 * ("coloque botão colunas como em plataforma para selecionar o cabeçalho
 * que quiser"). Controla tanto a lista na tela quanto o que sai no PDF/CSV
 * (mesmo comportamento do site: Colunas afeta a própria exportação). */
@Composable
fun ColumnsPickerButton(allColumns: List<ColumnConfig>, visibleKeys: Set<String>, onChange: (Set<String>) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.ViewColumn, contentDescription = "Colunas")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Box(modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                Column {
                    allColumns.forEach { col ->
                        val checked = visibleKeys.contains(col.key)
                        DropdownMenuItem(
                            text = { Text(col.label) },
                            leadingIcon = {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { on ->
                                        onChange(if (on) visibleKeys + col.key else visibleKeys - col.key)
                                    },
                                )
                            },
                            onClick = {
                                onChange(if (checked) visibleKeys - col.key else visibleKeys + col.key)
                            },
                        )
                    }
                }
            }
        }
    }
}

/** Mesma formatação usada no PDF (HtmlPrinter) -- moeda via formatMoneyValue,
 * data/número via displayValueFor -- pra CSV e PDF nunca mostrarem números
 * diferentes pro mesmo registro. */
private fun cellText(col: ColumnConfig, raw: String?): String {
    val value = raw.orEmpty()
    return if (col.money && value.isNotBlank()) formatMoneyValue(value) else displayValueFor(col.key, value, col.type)
}

private fun csvEscape(value: String): String {
    val needsQuotes = value.contains(",") || value.contains("\"") || value.contains("\n")
    val escaped = value.replace("\"", "\"\"")
    return if (needsQuotes) "\"$escaped\"" else escaped
}

fun buildCsv(columns: List<ColumnConfig>, records: List<Map<String, String?>>): String {
    val header = columns.joinToString(",") { csvEscape(it.label) }
    val rows = records.joinToString("\n") { record ->
        columns.joinToString(",") { col -> csvEscape(cellText(col, record[col.key])) }
    }
    return "$header\n$rows"
}

/** Botão CSV -- espelho do botão CSV/PDF do site (data-table.tsx), grava um
 * arquivo temporário e abre o menu "Compartilhar" do Android (mesmo
 * mecanismo do botão "Backup" do Início, ver ui/util/FileShare.kt). */
fun exportCsv(context: Context, title: String, columns: List<ColumnConfig>, records: List<Map<String, String?>>) {
    val csv = buildCsv(columns, records)
    val safeTitle = title.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "-").trim('-')
    val fileName = "$safeTitle-${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}.csv"
    shareTextFile(context, fileName, "text/csv", csv)
}

// shareRecordsResumo (ícone Compartilhar em txt) removido -- pedido do
// usuário ("exclua o ícone de compartilhar em txt... em todos os outros").
