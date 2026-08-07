package com.bragro.mobile.ui.domain

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Button
import com.bragro.mobile.ui.theme.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    val cardOverrides = remember { mutableStateMapOf<String, Boolean>() }

    // Blocos "compatíveis com ícone" (Gráficos, Calculadoras, Recalcular
    // Vencimentos, Filtros) consolidados numa fileira só, mesmo padrão do
    // módulo genérico (DomainListScreen.kt) -- pedido do usuário ("reduza os
    // blocos que são compatíveis a ícones, distribua numa linha só").
    val expandedBlocks = remember { mutableStateMapOf<String, Boolean>() }
    val iconRowLabels = remember {
        linkedMapOf(
            "charts" to "Gráficos",
            "calculators" to "Calculadoras",
            "recalcular-vencimentos" to "Recalcular Vencimentos",
        )
    }
    val activeBlockLabel = iconRowLabels.entries.firstOrNull { (key, _) -> expandedBlocks[key] == true }?.value

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
                // "Pule uma linha para aparecer o nome de cada ícone quando
                // clicado" -- "Financeiro" (ou "Financeiro — visão") continua
                // sempre na 1ª linha; o nome do bloco aberto (Gráficos,
                // Calculadoras, Recalcular Vencimentos) aparece numa 2ª
                // linha, menor, só enquanto ele estiver aberto.
                title = {
                    Column {
                        Text(if (isQuickView) "Financeiro — ${view.label}" else "Financeiro")
                        if (activeBlockLabel != null) {
                            Text(activeBlockLabel, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar") } },
                // Sem "actions" aqui -- Atualizar/Recolher-Expandir/Extrato/
                // Importar XML/Colunas/Exportar mudaram pra fileira de ícones
                // abaixo do título, pedido do usuário ("os ícones que
                // estavam do lado do título, saltando uma linha, insira-os
                // no bloco").
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
            // Seletor de visão (Todos/Contas a Pagar/Contas a Receber/
            // Conciliado/Fluxo de Caixa/Rateio Direto/Rateio Indireto) virou
            // ícone (em vez de chip com texto) dentro de um bloco com borda
            // fina -- pedido do usuário ("transforme os botões [...] em
            // ícones sem mexer na estrutura dos blocos, coloque uma borda
            // fina em volta do bloco"); toque no ícone já alterna o título
            // pro nome da visão (ver título acima), então dispensa o rótulo.
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    FinanceiroView.values().forEach { v ->
                        ModuleIconButton(
                            ModuleIconItem(v.name, financeiroViewIcon(v), v.label, active = v == view),
                        ) { view = v }
                    }
                }
            }

            Spacer(modifier = Modifier.size(8.dp))

            // Gráficos, Calculadoras, Recalcular Vencimentos, Período e
            // Filtros (Banco) -- antes cada um com seu próprio cabeçalho ou
            // numa linha separada, agora numa fileira só de ícones dentro de
            // um bloco com borda fina, igual ao módulo genérico
            // (DomainListScreen.kt).
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (!isQuickView) {
                        ModuleIconButton(
                            ModuleIconItem("charts", Icons.Filled.BarChart, "Gráficos", active = expandedBlocks["charts"] == true),
                        ) { expandedBlocks["charts"] = expandedBlocks["charts"] != true }
                        ModuleIconButton(
                            ModuleIconItem("calculators", Icons.Filled.Calculate, "Calculadoras", active = expandedBlocks["calculators"] == true),
                        ) { expandedBlocks["calculators"] = expandedBlocks["calculators"] != true }
                        // Icone diferente do "Atualizar" da AppBar (Refresh)
                        // -- mesmo icone pra acoes diferentes na mesma tela
                        // confundia, pedido do usuario ("substitua icones
                        // que estejam iguais mas com funcoes diferentes").
                        ModuleIconButton(
                            ModuleIconItem("recalcular-vencimentos", Icons.Filled.Autorenew, "Recalcular Vencimentos", active = expandedBlocks["recalcular-vencimentos"] == true),
                        ) { expandedBlocks["recalcular-vencimentos"] = expandedBlocks["recalcular-vencimentos"] != true }
                    }
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
                    // A partir daqui: ícones que antes moravam na AppBar
                    // (Atualizar/Recolher-Expandir/Extrato/Importar XML/
                    // Colunas/Exportar) -- pedido do usuário ("os ícones que
                    // couberem no mesmo bloco unifique os blocos"). Mesmo
                    // comportamento de antes, só mudou de lugar.
                    IconButton(onClick = { viewModel.refresh("financeiro") }) {
                        if (refreshing) CircularProgressIndicator(modifier = Modifier.padding(4.dp).size(20.dp))
                        else Icon(Icons.Filled.Refresh, contentDescription = "Atualizar")
                    }
                    if (filtered.isNotEmpty()) {
                        IconButton(onClick = { allExpanded = !allExpanded; cardOverrides.clear() }) {
                            Icon(
                                if (allExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = if (allExpanded) "Recolher todos os lançamentos" else "Expandir todos os lançamentos",
                            )
                        }
                    }
                    if (!isQuickView) {
                        IconButton(onClick = onOpenBankImport) {
                            Icon(Icons.Filled.Upload, contentDescription = "Extrato bancário")
                        }
                        IconButton(onClick = onOpenNfeImport) {
                            Icon(Icons.Filled.Description, contentDescription = "Importar XML (NF-e)")
                        }
                    }
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
                            // Calculadoras/Recalcular Vencimentos") -- e só
                            // quando o ícone correspondente na fileira acima
                            // está aberto (ver Card com FlowRow acima).
                            if (!isQuickView && expandedBlocks["charts"] == true) {
                                item(key = "charts") { ModuleChartsCard("financeiro", showHeader = false) }
                            }
                            if (!isQuickView && expandedBlocks["calculators"] == true) {
                                item(key = "calculators") { CalculatorsCard("financeiro", showHeader = false) }
                            }
                            if (!isQuickView && expandedBlocks["recalcular-vencimentos"] == true) {
                                item(key = "recalcular-vencimentos") { RecalcularVencimentosButton(showHeader = false) }
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
        else -> {
            val displayValue = if (col.money) formatMoneyValue(value) else displayValueFor(col.key, value, col.type)
            // Mesmo critério do módulo genérico: negrito só no valor
            // preenchido, cabeçalho normal -- "vcto" (isFinanceiroBoldColumn)
            // ganha um destaque extra de cor, sem dobrar o negrito na linha.
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) { append("${col.label}: ") }
                    withStyle(
                        SpanStyle(
                            fontWeight = FontWeight.Bold,
                            color = if (isFinanceiroBoldColumn(col.key)) MaterialTheme.colorScheme.primary else Color.Unspecified,
                        ),
                    ) { append(displayValue) }
                },
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.padding(vertical = 2.dp),
            )
        }
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

    // Só ícone (sem texto) -- pedido do usuário ("período isolado, só o
    // ícone"), mesmo critério do site (data-table.tsx) e do Período
    // genérico dos demais módulos (DomainListScreen.kt).
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                Icons.Filled.CalendarMonth,
                contentDescription = "Período",
                tint = if (hasFilter) MaterialTheme.colorScheme.primary else LocalContentColor.current,
            )
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

/** Botão-dropdown "Filtros" (Banco) -- só na visão Conciliado, mesma lista de
 * bancos cadastrados (lookups categoria "bancos") usada no formulário de
 * lançamento. Ícone de filtro em vez do específico de banco -- pedido do
 * usuário ("consolide todos os filtros de coluna num bloco Filtros, só
 * ícone"), mesmo critério do site (data-table.tsx). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BancoDropdown(banco: String?, options: List<LookupEntity>, onSelect: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val hasFilter = !banco.isNullOrBlank()

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                Icons.Filled.FilterAlt,
                contentDescription = "Filtros",
                tint = if (hasFilter) MaterialTheme.colorScheme.primary else LocalContentColor.current,
            )
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

/** Ícone de cada visão do Financeiro no seletor -- pedido do usuário
 * ("transforme os botões [...] em ícones"), um bem diferente do outro pra
 * não confundir (setas opostas pra Pagar/Receber, check pra Conciliado,
 * tendência pra Fluxo de Caixa, split/árvore pros dois Rateios). */
private fun financeiroViewIcon(v: FinanceiroView) = when (v) {
    FinanceiroView.TODOS -> Icons.Filled.ViewList
    FinanceiroView.PAGAR -> Icons.Filled.ArrowUpward
    FinanceiroView.RECEBER -> Icons.Filled.ArrowDownward
    FinanceiroView.CONCILIADO -> Icons.Filled.CheckCircle
    FinanceiroView.FLUXO -> Icons.Filled.TrendingUp
    FinanceiroView.RATEIO_DIRETO -> Icons.Filled.CallSplit
    FinanceiroView.RATEIO_INDIRETO -> Icons.Filled.AccountTree
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
