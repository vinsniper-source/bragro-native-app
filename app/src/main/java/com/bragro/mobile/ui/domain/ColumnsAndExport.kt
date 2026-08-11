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
import com.bragro.mobile.data.export.XlsxWriter
import com.bragro.mobile.data.model.ColumnConfig
import com.bragro.mobile.ui.util.shareBinaryFile
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
        // Rótulo abaixo do ícone -- varredura geral pedida pelo usuário
        // ("alguns ícones não receberam rótulos como colunas e períodos,
        // filtros"), mesmo padrão visual do LabeledIconButton.
        LabeledIconButton(icon = Icons.Filled.ViewColumn, label = "Colunas", onClick = { expanded = true })
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

/** Monta a matriz (cabeçalho + linhas, tudo já formatado como texto de
 * exibição via [cellText]) que alimenta tanto o .xlsx (XlsxWriter.buildXlsx)
 * quanto qualquer outro consumidor futuro que precise da tabela crua --
 * separado de [exportXlsx] pra poder ser testado/reaproveitado sem precisar
 * de Context/Android. */
private fun buildExportRows(columns: List<ColumnConfig>, records: List<Map<String, String?>>): List<List<String>> =
    records.map { record -> columns.map { col -> cellText(col, record[col.key]) } }

/** Botão "Excel" (antes "CSV") -- espelho do botão CSV/PDF do site
 * (data-table.tsx), só que agora gera um .xlsx real (XlsxWriter.buildXlsx,
 * zip+XML feito à mão, sem lib pesada) em vez de texto CSV. Grava um
 * arquivo temporário e abre o menu "Compartilhar" do Android (mesmo
 * mecanismo do botão "Backup" do Início, ver ui/util/FileShare.kt). */
fun exportXlsx(context: Context, title: String, columns: List<ColumnConfig>, records: List<Map<String, String?>>) {
    val headers = columns.map { it.label }
    val rows = buildExportRows(columns, records)
    val bytes = XlsxWriter.buildXlsx(headers, rows)
    val safeTitle = title.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "-").trim('-')
    val fileName = "$safeTitle-${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}.xlsx"
    shareBinaryFile(context, bytes, fileName)
}

// shareRecordsResumo (ícone Compartilhar em txt) removido -- pedido do
// usuário ("exclua o ícone de compartilhar em txt... em todos os outros").
