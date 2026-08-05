package com.bragro.mobile.ui.domain

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import android.app.Application
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import com.bragro.mobile.ui.theme.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bragro.mobile.data.local.LookupEntity
import com.bragro.mobile.data.repo.ConfigRepository
import com.bragro.mobile.ui.print.HtmlPrinter
import kotlinx.coroutines.launch

// Réplica mobile do agrupamento de Financeiro em src/app/(app)/m/[domain]/page.tsx
// + data-table.tsx: seletor de visão (Todos/Pagar/Receber/Conciliado/Fluxo de
// Caixa/Rateio Direto/Rateio Indireto), círculo de conciliar, vencimento em
// negrito, dropdown Período (8 categorias + intervalo manual, ver
// PeriodoFilters.kt) e dropdown Banco (só em Conciliado). Tudo calculado em
// memória sobre os registros já sincronizados (Room) -- funciona offline,
// igual ao resto do app, sem nenhum endpoint novo.
/** Só carrega a lista de bancos cadastrados (categoria "bancos" dos lookups
 * já sincronizados) -- usada pelo dropdown Banco, visível só na visão
 * Conciliado. Mesma fonte que já alimenta os dropdowns de Frota/Local/
 * Colaborador em QuickAbastecimentoDialog.kt. */
class FinanceiroFiltersViewModel(app: Application) : AndroidViewModel(app) {
    private val configRepository = ConfigRepository(app)
    var bancos = mutableStateOf<List<LookupEntity>>(emptyList())
        private set

    fun load() {
        viewModelScope.launch {
            bancos.value = configRepository.lookupsByCategory("bancos").sortedBy { it.label }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinanceiroScreen(
    onBack: () -> Unit,
    onNewRecord: () -> Unit,
    onEditRecord: (String) -> Unit,
    onOpenBankImport: () -> Unit,
    // Antes era um item separado no dropdown "Módulos" (Importar NF-e) --
    // pedido do usuário ("no módulo financeiro crie um botão importar xml e
    // unifique esses dois módulos"): agora vive DENTRO do Financeiro, ao
    // lado do Extrato bancário.
    onOpenNfeImport: () -> Unit,
    viewModel: DomainListViewModel = viewModel(),
    filtersViewModel: FinanceiroFiltersViewModel = viewModel(),
) {
    LaunchedEffect(Unit) {
        viewModel.load("financeiro")
        filtersViewModel.load()
    }
    val config by viewModel.config
    val allRecords by viewModel.records
    val refreshing by viewModel.refreshing
    val offline by viewModel.offline
    val context = LocalContext.current
    val bancoOptions by filtersViewModel.bancos

    var view by remember { mutableStateOf(FinanceiroView.TODOS) }
    val isQuickView = view != FinanceiroView.TODOS

    // Período (8 categorias, ver PeriodoFilters.kt) + Intervalo manual +
    // Banco (só em Conciliado) -- espelho do dropdown "Período"/"Banco" do
    // site, filtrando em memória sobre os registros já sincronizados.
    var periodo by remember { mutableStateOf<PeriodoCategoria?>(null) }
    var intervalFrom by remember { mutableStateOf("") }
    var intervalTo by remember { mutableStateOf("") }
    var banco by remember { mutableStateOf<String?>(null) }
    val dateKey = if (view == FinanceiroView.PAGAR || view == FinanceiroView.RECEBER) "vcto" else "data"

    // Recolher/expandir todos os cards de lançamento de uma vez -- mesmo
    // controle do módulo genérico (DomainListScreen.kt), pedido do usuário
    // ("mostre o bloco completo... recolha todos de uma vez").
    var allExpanded by remember { mutableStateOf(false) }
    val cardOverrides = remember { androidx.compose.runtime.mutableStateMapOf<String, Boolean>() }

    val filtered = remember(allRecords, view, periodo, intervalFrom, intervalTo, banco) {
        var result = filterByFinanceiroView(allRecords, view)
        result = filterByFinanceiroPeriodo(result, periodo, view)
        result = filterByDateInterval(result, dateKey, intervalFrom, intervalTo)
        if (view == FinanceiroView.CONCILIADO && !banco.isNullOrBlank()) {
            result = result.filter { it["banco"] == banco }
        }
        result
    }
    val presetKeys = FINANCEIRO_VIEW_COLUMN_KEYS[view]
    val fluxoRows = remember(filtered, view) {
        if (view == FinanceiroView.FLUXO) computeFluxoRows(filtered.sortedBy { it["vcto"] ?: "" }) else null
    }

    // Colunas do preset da visão atual (Pagar/Receber/Conciliado têm um
    // subconjunto fixo, ver FINANCEIRO_VIEW_COLUMN_KEYS) -- é sobre ESSE
    // conjunto que o botão "Colunas" (pedido do usuário) deixa escolher um
    // subconjunto ainda menor, tanto pra tela quanto pro CSV/PDF exportado.
    val viewColumns = remember(config, view) {
        val c = config
        if (c == null) emptyList() else presetKeys?.mapNotNull { key -> c.columns.find { it.key == key } } ?: c.columns.filter { !it.hideInTable }
    }
    var customColumnKeys by remember(view) { mutableStateOf<Set<String>?>(null) }
    val effectiveColumns = customColumnKeys?.let { keys -> viewColumns.filter { keys.contains(it.key) } } ?: viewColumns

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isQuickView) "Financeiro — ${view.label}" else "Financeiro") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar") } },
                actions = {
                    IconButton(onClick = { viewModel.refresh("financeiro") }) {
                        if (refreshing) CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                        else Icon(Icons.Filled.Refresh, contentDescription = "Atualizar")
                    }
                    if (filtered.isNotEmpty()) {
                        IconButton(onClick = { allExpanded = !allExpanded; cardOverrides.clear() }) {
                            Icon(
                                if (allExpanded) Icons.Filled.UnfoldLess else Icons.Filled.UnfoldMore,
                                contentDescription = if (allExpanded) "Recolher todos os lançamentos" else "Expandir todos os lançamentos",
                            )
                        }
                    }
                    // "Extrato" (importação de CSV bancário) -- mesmo critério
                    // do site: vive dentro de Financeiro, escondido nas
                    // visões rápidas (ver page.tsx: aba só existe em "Todos").
                    if (!isQuickView) {
                        IconButton(onClick = onOpenBankImport) {
                            Icon(Icons.Filled.Upload, contentDescription = "Extrato bancário")
                        }
                        IconButton(onClick = onOpenNfeImport) {
                            Icon(Icons.Filled.Description, contentDescription = "Importar XML (NF-e)")
                        }
                    }
                    val cfg = config
                    if (cfg != null) {
                        ColumnsPickerButton(
                            allColumns = viewColumns,
                            visibleKeys = customColumnKeys ?: viewColumns.map { it.key }.toSet(),
                            onChange = { customColumnKeys = it },
                        )
                        if (filtered.isNotEmpty()) {
                            IconButton(onClick = { exportCsv(context, "financeiro-${view.name.lowercase()}", effectiveColumns, filtered) }) {
                                Icon(Icons.Filled.FileDownload, contentDescription = "Exportar CSV")
                            }
                            IconButton(onClick = { HtmlPrinter.printList(context, cfg, filtered, effectiveColumns.map { it.key }.toSet()) }) {
                                Icon(Icons.Filled.Print, contentDescription = "Imprimir / exportar PDF")
                            }
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            // Mesmo critério do site (isQuickView): visões rápidas são só
            // leitura, sem "Novo Lançamento".
            if (!isQuickView) {
                FloatingActionButton(onClick = onNewRecord) {
                    Icon(Icons.Filled.Add, contentDescription = "Novo lançamento")
                }
            }
        },
    ) { padding ->
        val cfg = config
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FinanceiroView.values().forEach { v ->
                    FilterChip(
                        selected = v == view,
                        onClick = { view = v },
                        label = { Text(v.label) },
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PeriodoDropdown(
                    periodo = periodo,
                    view = view,
                    intervalFrom = intervalFrom,
                    intervalTo = intervalTo,
                    onPeriodo = { periodo = it; if (it != null) { intervalFrom = ""; intervalTo = "" } },
                    onInterval = { from, to -> intervalFrom = from; intervalTo = to; if (from.isNotBlank() || to.isNotBlank()) periodo = null },
                )
                if (view == FinanceiroView.CONCILIADO) {
                    BancoDropdown(banco = banco, options = bancoOptions, onSelect = { banco = it })
                }
            }

            when {
                cfg == null -> {
                    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                        Text("Carregando...", modifier = Modifier.padding(24.dp))
                    }
                }
                filtered.isEmpty() -> {
                    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                        if (offline) {
                            Text(
                                "Sem conexão -- não foi possível verificar se há lançamentos salvos no servidor.",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 24.dp),
                            )
                        }
                        Text("Nenhum lançamento nesta visão.", modifier = Modifier.padding(24.dp))
                    }
                }
                else -> {
                    if (offline) {
                        Text(
                            "Sem conexão -- mostrando o último resultado salvo neste aparelho.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                    }
                    val fRows = fluxoRows
                    if (fRows != null) {
                        LazyColumn(
                            contentPadding = PaddingValues(12.dp, 4.dp, 12.dp, 80.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(fRows, key = { it.original["id"] ?: it.hashCode().toString() }) { row ->
                                FluxoCard(row, onClick = { row.original["id"]?.let(onEditRecord) })
                            }
                        }
                    } else {
                        val cols = effectiveColumns
                        LazyColumn(
                            contentPadding = PaddingValues(12.dp, 4.dp, 12.dp, 80.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            // Gráficos/Calculadoras/Recalcular Vencimentos só
                            // aparecem na visão "Todos" -- mesmo critério do
                            // site ("visão rápida... sem Gráficos/
                            // Calculadoras/Recalcular Vencimentos").
                            if (!isQuickView) {
                                item(key = "charts") { ModuleChartsCard("financeiro") }
                                item(key = "calculators") { CalculatorsCard("financeiro") }
                                item(key = "recalcular-vencimentos") { RecalcularVencimentosButton() }
                            }
                            items(filtered, key = { it["id"] ?: it.hashCode().toString() }) { record ->
                                val recordId = record["id"]
                                // Mostra TODAS as colunas (não só as 6
                                // primeiras) -- mesmo pedido do usuário já
                                // aplicado ao módulo genérico
                                // (DomainListScreen.kt): "mostre o bloco
                                // completo... com todas as informações dos
                                // lançamentos", em 2 colunas quando expandido.
                                val allCols = cols.filter { it.key == "conciliar" || !record[it.key].isNullOrBlank() }
                                val summaryCols = allCols.take(6)
                                val hasMore = allCols.size > summaryCols.size
                                val expanded = cardOverrides[recordId ?: ""] ?: allExpanded
                                val colsToShow = if (expanded) allCols else summaryCols
                                Card(
                                    onClick = { if (recordId != null) onEditRecord(recordId) },
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        if (hasMore) {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                                IconButton(
                                                    onClick = { cardOverrides[recordId ?: ""] = !expanded },
                                                    modifier = Modifier.size(28.dp),
                                                ) {
                                                    Icon(
                                                        if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                                        contentDescription = if (expanded) "Recolher lançamento" else "Expandir lançamento",
                                                        modifier = Modifier.size(20.dp),
                                                    )
                                                }
                                            }
                                        }
                                        if (expanded) {
                                            colsToShow.chunked(2).forEach { pair ->
                                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                                    pair.forEach { col -> Column(modifier = Modifier.weight(1f)) { FinanceiroFieldLine(col, record[col.key]) } }
                                                    if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
                                                }
                                            }
                                        } else {
                                            colsToShow.forEach { col -> FinanceiroFieldLine(col, record[col.key]) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Uma linha de campo dentro do card de lançamento do Financeiro -- trata o
// caso especial "conciliar" (bolinha) + status-like (pill) + o negrito de
// isFinanceiroBoldColumn, mesmo critério de antes, só que reaproveitado em
// 1 ou 2 colunas.
@Composable
private fun FinanceiroFieldLine(col: com.bragro.mobile.data.model.ColumnConfig, value: String?) {
    when {
        col.key == "conciliar" -> {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                ConciliarDot(value == "true")
                Text(" ${col.label}", style = MaterialTheme.typography.bodySmall)
            }
        }
        value.isNullOrBlank() -> {}
        isStatusLikeColumn(col.key) -> StatusBadge(value)
        else -> Text(
            "${col.label}: ${if (col.money) formatMoneyValue(value) else displayValueFor(col.key, value, col.type)}",
            fontWeight = if (isFinanceiroBoldColumn(col.key)) FontWeight.Bold else FontWeight.Normal,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.padding(vertical = 2.dp),
        )
    }
}

/** Espelho de ConciliarDot (data-table.tsx): círculo preenchido (verde) se
 * conciliado, só contorno se pendente. */
@Composable
private fun ConciliarDot(conciliado: Boolean) {
    val borderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
    Box(
        modifier = Modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(if (conciliado) MaterialTheme.colorScheme.primary else Color.Transparent)
            .border(1.dp, borderColor, CircleShape),
    )
}

/** Botão-dropdown "Período" -- 8 categorias de recorrência/vencimento + um
 * intervalo de datas manual (De/Até), espelho do dropdown Período do site
 * (data-table.tsx). Em Contas a Pagar/Receber as categorias viram janela de
 * Vencimento; nas demais visões casam pelo campo "Periodo" do lançamento. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeriodoDropdown(
    periodo: PeriodoCategoria?,
    view: FinanceiroView,
    intervalFrom: String,
    intervalTo: String,
    onPeriodo: (PeriodoCategoria?) -> Unit,
    onInterval: (String, String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var fromText by remember(intervalFrom) { mutableStateOf(intervalFrom) }
    var toText by remember(intervalTo) { mutableStateOf(intervalTo) }
    val hasFilter = periodo != null || intervalFrom.isNotBlank() || intervalTo.isNotBlank()
    val label = periodo?.label ?: if (intervalFrom.isNotBlank() || intervalTo.isNotBlank()) "Intervalo" else "Período"

    Box {
        if (hasFilter) {
            Button(onClick = { expanded = true }) {
                Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                Text(label)
            }
        } else {
            OutlinedButton(onClick = { expanded = true }) {
                Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                Text(label)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("Todos os períodos") }, onClick = { onPeriodo(null); expanded = false })
            HorizontalDivider()
            Text(
                if (view == FinanceiroView.PAGAR || view == FinanceiroView.RECEBER) "Filtrar por vencimento" else "Filtrar por recorrência",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
            PeriodoCategoria.values().forEach { cat ->
                DropdownMenuItem(text = { Text(cat.label) }, onClick = { onPeriodo(cat); expanded = false })
            }
            HorizontalDivider()
            Text(
                "Ou por intervalo de datas (${if (view == FinanceiroView.PAGAR || view == FinanceiroView.RECEBER) "Vencimento" else "Data"})",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                OutlinedTextField(
                    value = fromText,
                    onValueChange = { fromText = it },
                    label = { Text("De") },
                    placeholder = { Text("AAAA-MM-DD") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                )
                OutlinedTextField(
                    value = toText,
                    onValueChange = { toText = it },
                    label = { Text("Até") },
                    placeholder = { Text("AAAA-MM-DD") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = { onInterval(fromText, toText); expanded = false },
                    modifier = Modifier.padding(top = 6.dp),
                ) { Text("Aplicar intervalo") }
            }
        }
    }
}

/** Botão-dropdown "Banco" -- só na visão Conciliado, mesma lista de bancos
 * cadastrados (lookups categoria "bancos") usada no formulário de lançamento. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BancoDropdown(banco: String?, options: List<LookupEntity>, onSelect: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val label = banco?.takeIf { it.isNotBlank() } ?: "Banco"

    Box {
        if (!banco.isNullOrBlank()) {
            Button(onClick = { expanded = true }) {
                Icon(Icons.Filled.AccountBalance, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                Text(label)
            }
        } else {
            OutlinedButton(onClick = { expanded = true }) {
                Icon(Icons.Filled.AccountBalance, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                Text(label)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("Todos os bancos") }, onClick = { onSelect(null); expanded = false })
            HorizontalDivider()
            Text("Filtrar por banco", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
            options.forEach { opt ->
                DropdownMenuItem(text = { Text(opt.label) }, onClick = { onSelect(opt.value); expanded = false })
            }
        }
    }
}

@Composable
private fun FluxoCard(row: FluxoRow, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("${row.original["entidade"] ?: "—"} — ${row.original["categoria"] ?: "—"}", fontWeight = FontWeight.Medium)
            Text("Data movimento: ${row.dataMovimento?.let { displayValueFor("data", it, "date") } ?: "—"}", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (row.entrada > 0) Text("Entrada: ${formatMoneyValue(row.entrada.toString())}", color = Color(0xFF2F6F4F))
                if (row.saida > 0) Text("Saída: ${formatMoneyValue(row.saida.toString())}", color = MaterialTheme.colorScheme.error)
            }
            Text("Saldo acumulado: ${formatMoneyValue(row.saldoAcumulado.toString())}", fontWeight = FontWeight.Bold)
        }
    }
}
