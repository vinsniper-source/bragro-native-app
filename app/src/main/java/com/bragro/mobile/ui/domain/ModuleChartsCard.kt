package com.bragro.mobile.ui.domain

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import com.bragro.mobile.ui.theme.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bragro.mobile.data.model.GenericChartData
import com.bragro.mobile.data.model.ModuleChartsResponse
import com.bragro.mobile.data.repo.ChartsRepository
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
                Column(
                    modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // Eficiência de Frota (L/h por máquina) mora dentro deste
                    // mesmo bloco colapsável -- pedido do usuário ("esconda o
                    // gráfico eficiência de frota dentro do bloco gráficos"),
                    // em vez de um card próprio sempre visível na lista.
                    if (domainId == "frota") FleetEfficiencyCard()
                    when {
                        loading && !loaded -> Text("Carregando gráficos...", style = MaterialTheme.typography.bodySmall)
                        data == null -> Text("Sem conexão -- não foi possível carregar os gráficos.", style = MaterialTheme.typography.bodySmall)
                        else -> {
                            val generic = data?.generic
                            val extras = data?.extras.orEmpty().mapNotNull { parseChartSpec(it) }
                            if (generic == null && extras.isEmpty() && domainId != "frota") {
                                Text("Nenhum gráfico disponível para este módulo ainda.", style = MaterialTheme.typography.bodySmall)
                            }
                            if (generic != null && generic.data.isNotEmpty()) {
                                GenericChartBlock(generic)
                            }
                            extras.forEach { spec ->
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
    }
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
