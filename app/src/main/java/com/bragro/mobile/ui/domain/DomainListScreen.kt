package com.bragro.mobile.ui.domain

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bragro.mobile.data.model.DomainConfig
import com.bragro.mobile.data.repo.ConfigRepository
import com.bragro.mobile.data.repo.RecordRepository
import com.bragro.mobile.ui.print.HtmlPrinter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class DomainListViewModel(app: Application) : AndroidViewModel(app) {
    private val configRepository = ConfigRepository(app)
    private val recordRepository = RecordRepository(app)

    var config = mutableStateOf<DomainConfig?>(null)
        private set
    var records = mutableStateOf<List<Map<String, String?>>>(emptyList())
        private set
    var refreshing = mutableStateOf(false)
        private set
    // Fase 3: o ViewModel ja tinha "refreshing", mas nada na tela mostrava
    // isso nem deixava o usuario puxar pra atualizar manualmente -- so
    // atualizava sozinho ao abrir a tela. "offline" segue o mesmo padrao
    // ja usado em Dashboard/DRE/Analises (true quando o ultimo refresh
    // falhou, pra avisar que a lista pode estar desatualizada).
    var offline = mutableStateOf(false)
        private set

    fun load(domainId: String) {
        viewModelScope.launch {
            config.value = configRepository.domainConfig(domainId)
            recordRepository.observeRecords(domainId).collectLatest { records.value = it }
        }
        refresh(domainId)
    }

    fun refresh(domainId: String) {
        refreshing.value = true
        viewModelScope.launch {
            val ok = recordRepository.refreshFromServer(domainId)
            offline.value = !ok
            refreshing.value = false
        }
    }
}

/** Uma unica tela de lista serve TODOS os 16 modulos -- guiada pelo
 * DomainConfig (mesma ideia do motor generico do site, ver
 * components/domain/data-table.tsx). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DomainListScreen(
    domainId: String,
    onBack: () -> Unit,
    onNewRecord: () -> Unit,
    onEditRecord: (String) -> Unit,
    viewModel: DomainListViewModel = viewModel(),
) {
    LaunchedEffect(domainId) { viewModel.load(domainId) }
    val config by viewModel.config
    val records by viewModel.records
    val refreshing by viewModel.refreshing
    val offline by viewModel.offline
    val context = LocalContext.current
    // Abastecimento rápido (Frota): FAB extra que abre um Dialog com só o
    // essencial preenchido -- espelho de quick-abastecimento-button.tsx.
    var showQuickAbastecimento by remember { mutableStateOf(false) }

    // Período genérico (espelho de genericPeriodoRange em data-table.tsx):
    // janela de data pra trás sobre a 1ª coluna de data do domínio -- só
    // aparece quando o domínio tem alguma coluna "date" (Financeiro tem seu
    // próprio Período com regras de vencimento, ver FinanceiroScreen.kt).
    var periodo by remember(domainId) { mutableStateOf<PeriodoCategoria?>(null) }
    var intervalFrom by remember(domainId) { mutableStateOf("") }
    var intervalTo by remember(domainId) { mutableStateOf("") }
    val dateCol = config?.columns?.firstOrNull { it.type == "date" && !it.computed }
    val filteredRecords = remember(records, periodo, intervalFrom, intervalTo, dateCol) {
        if (dateCol == null) {
            records
        } else {
            var result = records
            if (periodo != null) {
                val (from, to) = genericPeriodoRange(periodo!!)
                result = filterByDateInterval(result, dateCol.key, from, to)
            }
            if (intervalFrom.isNotBlank() || intervalTo.isNotBlank()) {
                result = filterByDateInterval(result, dateCol.key, intervalFrom, intervalTo)
            }
            result
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(config?.label ?: domainId) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar") }
                },
                actions = {
                    // Fase 3: o ViewModel ja tinha "refresh(domainId)"/
                    // "refreshing" prontos desde a Fase 1, mas nada na tela
                    // deixava o usuario disparar manualmente -- so
                    // atualizava sozinho ao abrir. Mesmo padrao de botao de
                    // atualizar ja usado em Dashboard/DRE/Analises.
                    IconButton(onClick = { viewModel.refresh(domainId) }) {
                        if (refreshing) CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                        else Icon(Icons.Filled.Refresh, contentDescription = "Atualizar")
                    }
                    // Fase 2 (Task #41): imprime/exporta em PDF a lista
                    // atual (registros ja cacheados no Room) via o dialogo
                    // de impressao nativo do Android -- mesmo principio do
                    // botao "Exportar PDF" do site (tabela HTML + impressao
                    // do sistema, sem gerar PDF no servidor).
                    val cfg = config
                    if (cfg != null && filteredRecords.isNotEmpty()) {
                        IconButton(onClick = { HtmlPrinter.printList(context, cfg, filteredRecords) }) {
                            Icon(Icons.Filled.Print, contentDescription = "Imprimir / exportar PDF")
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            // Frota ganha um segundo FAB menor -- "Abastecimento rápido"
            // (espelho de QuickAbastecimentoButton no site), sem substituir
            // o formulário completo de "Novo lançamento".
            if (domainId == "frota") {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FloatingActionButton(
                        onClick = { showQuickAbastecimento = true },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Icon(Icons.Filled.LocalGasStation, contentDescription = "Abastecimento rápido")
                    }
                    FloatingActionButton(onClick = onNewRecord) { Icon(Icons.Filled.Add, contentDescription = "Novo lançamento") }
                }
            } else {
                FloatingActionButton(onClick = onNewRecord) { Icon(Icons.Filled.Add, contentDescription = "Novo lançamento") }
            }
        },
    ) { padding ->
        val cfg = config
        if (cfg == null) {
            Column(modifier = Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.Center) {
                Text("Carregando...", modifier = Modifier.padding(24.dp))
            }
            return@Scaffold
        }
        // Divisor tracejado entre lançamentos do MESMO Nº Pedido+Item, só em
        // Pedidos -- espelho de pedidoItemBoundary (data-table.tsx). Os
        // registros já chegam ordenados por criadoEmMillis DESC (mesmo
        // critério do site: noPedido/item/criadoEm), então checar o
        // (noPedido,item) do próximo registro na lista já visível basta.
        val isPedidos = domainId == "pedidos"
        LazyColumn(contentPadding = PaddingValues(12.dp, padding.calculateTopPadding() + 4.dp, 12.dp, 80.dp)) {
            // Calculadoras (Safra/Colheita) -- CalculatorsCard() não desenha
            // nada nos outros domínios (mesmo "return null" do site).
            item(key = "calculators") { CalculatorsCard(domainId) }
            item(key = "charts") { ModuleChartsCard(domainId) }
            if (domainId == "safra" || domainId == "frota") {
                item(key = "recalcular-area") { RecalcularAreaButton(domainId) }
            }
            if (domainId == "frota") {
                item(key = "fleet-efficiency") { FleetEfficiencyCard() }
            }
            if (dateCol != null) {
                item(key = "periodo") {
                    Row(modifier = Modifier.padding(bottom = 8.dp)) {
                        GenericPeriodoDropdown(
                            periodo = periodo,
                            intervalFrom = intervalFrom,
                            intervalTo = intervalTo,
                            dateLabel = dateCol.label,
                            onPeriodo = { periodo = it; if (it != null) { intervalFrom = ""; intervalTo = "" } },
                            onInterval = { from, to -> intervalFrom = from; intervalTo = to; if (from.isNotBlank() || to.isNotBlank()) periodo = null },
                        )
                    }
                }
            }
            if (offline) {
                item(key = "offline-banner") {
                    Text(
                        "Sem conexão -- mostrando o último resultado salvo neste aparelho.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }
            if (filteredRecords.isEmpty()) {
                item(key = "empty") {
                    val msg = if (records.isEmpty()) "Nenhum lançamento ainda. Toque em + para adicionar." else "Nenhum lançamento neste período."
                    Text(msg, modifier = Modifier.padding(vertical = 24.dp))
                }
            } else {
                items(filteredRecords, key = { it["id"] ?: it.hashCode().toString() }) { record ->
                    val recordId = record["id"]
                    val isLastOfGroup = isPedidos && run {
                        val idx = filteredRecords.indexOf(record)
                        val next = filteredRecords.getOrNull(idx + 1)
                        next == null || next["noPedido"] != record["noPedido"] || next["item"] != record["item"]
                    }
                    Card(
                        onClick = { if (recordId != null) onEditRecord(recordId) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .then(
                                if (isLastOfGroup) Modifier.padding(bottom = 8.dp) else Modifier
                            ),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            cfg.columns.filter { !it.hideInTable }.take(4).forEach { col ->
                                val value = record[col.key]
                                if (!value.isNullOrBlank()) {
                                    // Colunas "status-like" (status/acaoRh/confere/
                                    // conferenNf/desvio) viram um pill colorido em
                                    // vez de texto simples -- mesmo critério
                                    // genérico do site (StatusCell em
                                    // data-table.tsx), vale pros ~18 módulos.
                                    if (isStatusLikeColumn(col.key)) {
                                        StatusBadge(value)
                                    } else {
                                        Text("${col.label}: ${if (col.money) "R$ $value" else displayValueFor(col.key, value)}")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showQuickAbastecimento) {
        QuickAbastecimentoDialog(
            onDismiss = { showQuickAbastecimento = false },
            onSaved = {
                showQuickAbastecimento = false
                viewModel.refresh(domainId)
            },
        )
    }
}

/** Espelho genérico do dropdown Período do Financeiro (ver PeriodoDropdown em
 * FinanceiroScreen.kt), aplicado aos demais ~17 módulos: 8 categorias como
 * janela de data PARA TRÁS a partir de hoje + intervalo manual, sobre a 1ª
 * coluna de data do domínio -- mesmo critério de genericPeriodoRange
 * (data-table.tsx). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GenericPeriodoDropdown(
    periodo: PeriodoCategoria?,
    intervalFrom: String,
    intervalTo: String,
    dateLabel: String,
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
            PeriodoCategoria.values().forEach { cat ->
                DropdownMenuItem(text = { Text(cat.label) }, onClick = { onPeriodo(cat); expanded = false })
            }
            HorizontalDivider()
            Text(
                "Ou por intervalo de datas ($dateLabel)",
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
