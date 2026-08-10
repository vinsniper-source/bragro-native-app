package com.bragro.mobile.ui.domain

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import com.bragro.mobile.ui.theme.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bragro.mobile.data.model.GenericChartData
import com.bragro.mobile.data.model.ModuleChartsResponse
import com.bragro.mobile.data.repo.ChartsRepository
import com.bragro.mobile.ui.print.HtmlPrinter
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// Réplica mobile do bloco "Gráficos" (ModuleChartsPanel) -- colapsável,
// fechado por padrão, só busca dados quando o usuário abre (mesmo critério
// de performance do site: "o custo só existe se o usuário realmente
// clicar"). Aparece em QUALQUER módulo -- alguns simplesmente não têm
// nenhum gráfico extra (cobrancas/nfse, por exemplo) e mostram "nenhum
// gráfico disponível", igual ao site.
class ModuleChartsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = ChartsRepository(app)
    var data = mutableStateOf<ModuleChartsResponse?>(null)
        private set
    var loading = mutableStateOf(false)
        private set
    var loaded = mutableStateOf(false)
        private set

    fun load(domainId: String) {
        if (loaded.value || loading.value) return
        loading.value = true
        viewModelScope.launch {
            data.value = repo.fetch(domainId)
            loading.value = false
            loaded.value = true
        }
    }
}

@Composable
fun ModuleChartsCard(domainId: String, viewModel: ModuleChartsViewModel = viewModel(), showHeader: Boolean = true) {
    // Quando chamado sem cabeçalho (showHeader = false), quem controla se
    // este bloco aparece ou não é a fileira de ícones do módulo
    // (ModuleIconRow) -- então já nasce "aberto", sem precisar de um
    // segundo toque no cabeçalho pra revelar o conteúdo.
    var open by remember { mutableStateOf(!showHeader) }
    LaunchedEffect(open) { if (open) viewModel.load(domainId) }

    val data by viewModel.data
    val loading by viewModel.loading
    val loaded by viewModel.loaded

    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            if (showHeader) {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { open = !open }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.BarChart, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text("Gráficos", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = if (open) "Recolher" else "Expandir")
                }
            }
            if (open) {
                val context = LocalContext.current
                val generic = data?.generic
                val extras = data?.extras.orEmpty().mapNotNull { parseChartSpec(it) }
                // Um bloco (Card) por gráfico -- pedido do usuário ("separe
                // os gráficos por blocos"), em vez de todos empilhados soltos
                // dentro de um Column só. Cada Triple é (título, cabeçalhos,
                // linhas) já formatado como tabela -- mesma representação
                // usada pelo Imprimir/PDF abaixo, então os dois sempre
                // mostram exatamente os mesmos dados.
                val printable = remember(generic, extras) { chartsToPrintData(domainId, generic, extras) }
                Row(modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // Eficiência de Frota (L/h por máquina) mora dentro
                        // deste mesmo bloco colapsável -- pedido do usuário
                        // ("esconda o gráfico eficiência de frota dentro do
                        // bloco gráficos"), em vez de um card próprio sempre
                        // visível na lista. Ganha seu próprio bloco também.
                        if (domainId == "frota") {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(12.dp)) { FleetEfficiencyCard() }
                            }
                        }
                        when {
                            loading && !loaded -> Text("Carregando gráficos...", style = MaterialTheme.typography.bodySmall)
                            data == null -> Text("Sem conexão -- não foi possível carregar os gráficos.", style = MaterialTheme.typography.bodySmall)
                            else -> {
                                if (generic == null && extras.isEmpty() && domainId != "frota") {
                                    Text("Nenhum gráfico disponível para este módulo ainda.", style = MaterialTheme.typography.bodySmall)
                                }
                                if (generic != null && generic.data.isNotEmpty()) {
                                    Card(modifier = Modifier.fillMaxWidth()) {
                                        Column(modifier = Modifier.padding(12.dp)) { GenericChartBlock(generic) }
                                    }
                                }
                                extras.forEach { spec ->
                                    Card(modifier = Modifier.fillMaxWidth()) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            when (spec) {
                                                is ChartSpec.Bar -> BarChartBlock(spec)
                                                is ChartSpec.Table -> TableChartBlock(spec)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    // Imprimir/PDF do lado direito, na vertical -- pedido do
                    // usuário ("coloque do lado direito na vertical os
                    // seguintes ícones imprimir e pdf"). Desabilitados
                    // enquanto não há nenhum gráfico pra exportar.
                    if (printable.isNotEmpty()) {
                        Column(
                            modifier = Modifier.width(48.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            IconButton(onClick = { HtmlPrinter.printCharts(context, domainId, printable) }) {
                                Icon(Icons.Filled.Print, contentDescription = "Imprimir gráficos")
                            }
                            IconButton(onClick = { HtmlPrinter.exportChartsPdfDirect(context, domainId, printable) }) {
                                Icon(Icons.Filled.PictureAsPdf, contentDescription = "Exportar gráficos em PDF")
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Converte os gráficos já carregados (genérico + extras) pra tabela
 * simples de título+linhas, usada pelos ícones Imprimir/PDF acima -- os
 * dados são os mesmos exibidos na tela, só sem a barra/gráfico visual. */
private fun chartsToPrintData(
    domainId: String,
    generic: GenericChartData?,
    extras: List<ChartSpec>,
): List<HtmlPrinter.ChartPrintData> {
    val result = mutableListOf<HtmlPrinter.ChartPrintData>()
    if (generic != null && generic.data.isNotEmpty()) {
        result += HtmlPrinter.ChartPrintData(
            title = generic.title,
            headers = listOf("Item", "Valor"),
            rows = generic.data.map { listOf(it.name, if (generic.isMoney) formatChartValue(it.value, true) else it.value.toString()) },
        )
    }
    extras.forEach { spec ->
        when (spec) {
            is ChartSpec.Bar -> {
                result += HtmlPrinter.ChartPrintData(
                    title = spec.title,
                    headers = listOf("Categoria") + spec.series.map { it.second },
                    rows = spec.categories.mapIndexed { i, cat ->
                        listOf(cat) + spec.series.map { (_, _, values) -> if (spec.money) formatChartValue(values.getOrElse(i) { 0.0 }, true) else values.getOrElse(i) { 0.0 }.toString() }
                    },
                )
            }
            is ChartSpec.Table -> {
                result += HtmlPrinter.ChartPrintData(
                    title = spec.title,
                    headers = spec.columns.map { it.second },
                    rows = spec.rows.map { row -> spec.columns.map { (_, label, _) -> row[label] ?: "—" } },
                )
            }
        }
    }
    return result
}

@Composable
private fun GenericChartBlock(data: GenericChartData) {
    Column {
        Text(data.title, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 6.dp))
        SimpleBarChart(
            categories = data.data.map { it.name },
            series = listOf(BarSeries("valor", data.data.map { it.value }, defaultSeriesColor(0))),
            isMoney = data.isMoney,
        )
    }
}

@Composable
private fun BarChartBlock(spec: ChartSpec.Bar) {
    Column {
        Text(spec.title, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
        if (spec.description.isNotBlank()) {
            Text(spec.description, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = 6.dp))
        }
        SimpleBarChart(
            categories = spec.categories,
            series = spec.series.mapIndexed { i, (_, label, values) -> BarSeries(label, values, defaultSeriesColor(i)) },
            isMoney = spec.money,
        )
    }
}

@Composable
private fun TableChartBlock(spec: ChartSpec.Table) {
    Column {
        Text(spec.title, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
        if (spec.description.isNotBlank()) {
            Text(spec.description, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = 6.dp))
        }
        spec.rows.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                spec.columns.forEach { (_, label, value) ->
                    Text("$label: ${row[label] ?: "—"}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

// -- Parsing de ExtraChartSpec (JSON bruto -> sealed class Kotlin) --

sealed class ChartSpec {
    data class Bar(
        val title: String,
        val description: String,
        val money: Boolean,
        val categories: List<String>,
        /** Triple(key, label, values) por série, values alinhado com categories. */
        val series: List<Triple<String, String, List<Double>>>,
    ) : ChartSpec()

    data class Table(
        val title: String,
        val description: String,
        /** Triple(key, label, money) por coluna. */
        val columns: List<Triple<String, String, Boolean>>,
        /** Cada linha: label da coluna -> valor já formatado como String. */
        val rows: List<Map<String, String>>,
    ) : ChartSpec()
}

private fun parseChartSpec(obj: JsonObject): ChartSpec? {
    val kind = obj["kind"]?.jsonPrimitive?.contentOrNull ?: return null
    val title = obj["title"]?.jsonPrimitive?.contentOrNull ?: return null
    val description = obj["description"]?.jsonPrimitive?.contentOrNull ?: ""
    val dataRows = obj["data"]?.jsonArray.orEmpty().mapNotNull { it as? JsonObject }

    return when (kind) {
        "bar" -> {
            val dataKey = obj["dataKey"]?.jsonPrimitive?.contentOrNull ?: return null
            val money = obj["money"]?.jsonPrimitive?.booleanOrNull ?: false
            val seriesSpecs = obj["series"]?.jsonArray.orEmpty().mapNotNull { s ->
                val sObj = s as? JsonObject ?: return@mapNotNull null
                val key = sObj["key"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val label = sObj["label"]?.jsonPrimitive?.contentOrNull ?: key
                key to label
            }
            val categories = dataRows.map { it[dataKey]?.jsonPrimitive?.contentOrNull ?: "—" }
            val series = seriesSpecs.map { (key, label) ->
                Triple(key, label, dataRows.map { it[key]?.jsonPrimitive?.doubleOrNull ?: 0.0 })
            }
            ChartSpec.Bar(title, description, money, categories, series)
        }
        "table" -> {
            val columnSpecs = obj["columns"]?.jsonArray.orEmpty().mapNotNull { c ->
                val cObj = c as? JsonObject ?: return@mapNotNull null
                val key = cObj["key"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val label = cObj["label"]?.jsonPrimitive?.contentOrNull ?: key
                val money = cObj["money"]?.jsonPrimitive?.booleanOrNull ?: false
                Triple(key, label, money)
            }
            val rows = dataRows.map { row ->
                columnSpecs.associate { (key, label, money) ->
                    val raw = row[key]?.jsonPrimitive?.contentOrNull
                    val display = if (raw == null) "—" else if (money) formatChartValue(raw.toDoubleOrNull() ?: 0.0, true) else raw
                    label to display
                }
            }
            ChartSpec.Table(title, description, columnSpecs, rows)
        }
        else -> null
    }
}
