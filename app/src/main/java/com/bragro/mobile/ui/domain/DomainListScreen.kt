package com.bragro.mobile.ui.domain

import android.app.Application
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WbSunny
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bragro.mobile.data.model.DomainConfig
import com.bragro.mobile.data.model.WeatherResponse
import com.bragro.mobile.data.repo.ConfigRepository
import com.bragro.mobile.data.repo.RecordRepository
import com.bragro.mobile.data.repo.WeatherRepository
import com.bragro.mobile.ui.print.HtmlPrinter
import com.bragro.mobile.ui.theme.BrYellow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class DomainListViewModel(app: Application) : AndroidViewModel(app) {
    private val configRepository = ConfigRepository(app)
    private val recordRepository = RecordRepository(app)
    private val weatherRepository = WeatherRepository()

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
    // Bloco de KPIs do Clima (pedido do usuário, réplica de
    // src/app/(app)/clima/clima-card.tsx: previsão completa de 6 dias, não
    // só o "agora" resumido que já existe no mini card do Início) -- só
    // busca quando o domínio aberto é "clima", mesma rota pública
    // /api/mobile/weather do WeatherRepository do Início.
    var weather = mutableStateOf<WeatherResponse?>(null)
        private set

    fun load(domainId: String) {
        viewModelScope.launch {
            config.value = configRepository.domainConfig(domainId)
            recordRepository.observeRecords(domainId).collectLatest { records.value = it }
        }
        if (domainId == "clima") {
            viewModelScope.launch { weather.value = weatherRepository.fetch() }
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
    // Antes era um item separado no dropdown "Módulos" (Romaneio Rápido) --
    // pedido do usuário ("coloque romaneio rápido como um botão dentro de
    // romaneio, unifique"): agora é um 2º FAB só no módulo Romaneios, mesmo
    // padrão do "Abastecimento rápido" da Frota logo abaixo. Nulo em
    // qualquer outro domínio.
    onOpenRomaneioQuick: (() -> Unit)? = null,
    // Cobranças/NFS-e unificados (ver BottomNavBar.kt/BRAgroNavHost.kt):
    // quando não-nulo, mostra um alternador no topo da lista pra trocar de
    // domínio sem passar pelo menu -- pedido do usuário ("no módulo
    // cobranças e nfse unifique e me um só módulo"). Continuam sendo 2
    // domínios/telas distintas no servidor; só a navegação fica unificada.
    linkedDomains: List<Pair<String, String>>? = null,
    onSwitchDomain: ((String) -> Unit)? = null,
    viewModel: DomainListViewModel = viewModel(),
) {
    LaunchedEffect(domainId) { viewModel.load(domainId) }
    val config by viewModel.config
    val records by viewModel.records
    val refreshing by viewModel.refreshing
    val offline by viewModel.offline
    val weather by viewModel.weather
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

    // Recolher/expandir todos os cards de uma vez -- pedido do usuário
    // ("recolha todos de uma vez"), além da seta individual de cada card.
    // `cardOverrides` guarda exceções pontuais (usuário abriu/fechou UM card
    // na mão); "Recolher/Expandir tudo" reseta essas exceções pra valer o
    // mesmo estado em todos de novo.
    var allExpanded by remember(domainId) { mutableStateOf(false) }
    val cardOverrides = remember(domainId) { mutableStateMapOf<String, Boolean>() }
    // Bloco "Filtros" (Período) também colapsável, com sua própria setinha
    // individual -- mas segue a seta única do cabeçalho (acima) quando o
    // usuário não abriu/fechou ele na mão ainda, mesmo padrão de override
    // pontual do `cardOverrides` para os cards de lançamento.
    var filtrosOverride by remember(domainId) { mutableStateOf<Boolean?>(null) }
    val filtrosExpanded = filtrosOverride ?: allExpanded

    // Botão "Colunas" (espelho do site) -- null = ainda não customizado pelo
    // usuário, mostra todas as colunas não ocultas (comportamento de sempre).
    var customVisibleKeys by remember(domainId) { mutableStateOf<Set<String>?>(null) }
    val allNonHiddenKeys = config?.columns?.filter { !it.hideInTable }?.map { it.key }?.toSet().orEmpty()
    val visibleKeys = customVisibleKeys ?: allNonHiddenKeys
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
                    // Seta única de recolher/expandir tudo de uma vez -- pedido
                    // do usuário ("crie uma única seta para recolher e
                    // expandir tudo de uma vez"), substitui o par de ícones
                    // Unfold que existia antes. Controla tanto os cards de
                    // lançamento quanto o bloco de Filtros abaixo; cada um
                    // ainda tem sua própria setinha individual para exceções.
                    if (filteredRecords.isNotEmpty() || dateCol != null) {
                        IconButton(onClick = { allExpanded = !allExpanded; cardOverrides.clear(); filtrosOverride = null }) {
                            Icon(
                                if (allExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = if (allExpanded) "Recolher tudo" else "Expandir tudo",
                            )
                        }
                    }
                    // Fase 2 (Task #41): imprime/exporta em PDF a lista
                    // atual (registros ja cacheados no Room) via o dialogo
                    // de impressao nativo do Android -- mesmo principio do
                    // botao "Exportar PDF" do site (tabela HTML + impressao
                    // do sistema, sem gerar PDF no servidor).
                    val cfg = config
                    if (cfg != null) {
                        // Pedido do usuário: "coloque botão colunas como em
                        // plataforma para selecionar o cabeçalho que quiser
                        // e coloque botão csv/pdf" -- espelho do toolbar de
                        // data-table.tsx (Colunas + CSV/PDF), afetando tanto
                        // a lista na tela quanto os dois exports.
                        ColumnsPickerButton(
                            allColumns = cfg.columns.filter { !it.hideInTable },
                            visibleKeys = visibleKeys,
                            onChange = { customVisibleKeys = it },
                        )
                        if (filteredRecords.isNotEmpty()) {
                            IconButton(onClick = { exportCsv(context, cfg.label, cfg.columns.filter { !it.hideInTable && visibleKeys.contains(it.key) }, filteredRecords) }) {
                                Icon(Icons.Filled.FileDownload, contentDescription = "Exportar CSV")
                            }
                            IconButton(onClick = { HtmlPrinter.printList(context, cfg, filteredRecords, visibleKeys) }) {
                                Icon(Icons.Filled.Print, contentDescription = "Imprimir / exportar PDF")
                            }
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
            } else if (domainId == "romaneios" && onOpenRomaneioQuick != null) {
                // Romaneio Rápido (leitura da balança) unificado aqui como
                // 2º FAB -- pedido do usuário ("coloque romaneio rápido como
                // um botão dentro de romaneio, unifique"), mesmo padrão do
                // "Abastecimento rápido" da Frota acima.
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FloatingActionButton(
                        onClick = onOpenRomaneioQuick,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Icon(Icons.Filled.MonitorWeight, contentDescription = "Romaneio rápido (balança)")
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
        LazyColumn(
            contentPadding = PaddingValues(12.dp, padding.calculateTopPadding() + 4.dp, 12.dp, 80.dp),
            // Sem isso os blocos (Gráficos, Calculadoras, Recalcular Área,
            // Período, cards de lançamento) ficavam grudados um no outro --
            // pedido do usuário ("em todos módulos gráficos vem primeiro
            // coloque um espaço entre os blocos").
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Alternador Cobranças/NFS-e -- fica ACIMA até de Gráficos de
            // propósito (é navegação de tela, não conteúdo do módulo).
            if (linkedDomains != null && onSwitchDomain != null) {
                item(key = "linked-domain-switch") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 4.dp)) {
                        linkedDomains.forEach { (id, label) ->
                            val active = id == domainId
                            if (active) {
                                Button(onClick = {}, enabled = false) { Text(label) }
                            } else {
                                OutlinedButton(onClick = { onSwitchDomain(id) }) { Text(label) }
                            }
                        }
                    }
                }
            }
            // Gráficos sempre primeiro, no topo -- pedido explícito do
            // usuário ("o bloco gráficos sempre será o primeiro do topo").
            // Calculadoras (Safra/Colheita) -- CalculatorsCard() não desenha
            // nada nos outros domínios (mesmo "return null" do site).
            item(key = "charts") { ModuleChartsCard(domainId) }
            item(key = "calculators") { CalculatorsCard(domainId) }
            // Bloco de KPIs do Clima -- réplica de clima-card.tsx (previsão
            // completa de 6 dias, colapsável e fechada por padrão, mesmo
            // critério do site). Fica logo após Gráficos por causa da regra
            // já estabelecida em todo o app ("Gráficos sempre primeiro no
            // topo") -- no site esse card vem ANTES dos Gráficos só na rota
            // própria de Clima, mas manter Gráficos em 1º lugar em TODOS os
            // módulos evita uma exceção visual sozinha só aqui.
            if (domainId == "clima") {
                item(key = "clima-weather") { ClimaForecastCard(weather) }
            }
            if (domainId == "safra" || domainId == "frota") {
                item(key = "recalcular-area") { RecalcularAreaButton(domainId) }
            }
            if (dateCol != null) {
                // Bloco "Filtros" colapsável (fechado por padrão) -- pedido
                // do usuário ("crie um ícone de filtrar e coloque todos os
                // filtros com setinha para recolher"), mesmo padrão visual
                // do ClimaForecastCard (ícone + título + seta individual).
                item(key = "periodo") {
                    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Filled.FilterAlt, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                                Text(
                                    "Filtros",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(onClick = { filtrosOverride = !filtrosExpanded }) {
                                    Icon(
                                        if (filtrosExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                        contentDescription = if (filtrosExpanded) "Recolher filtros" else "Expandir filtros",
                                    )
                                }
                            }
                            if (filtrosExpanded) {
                                Row(modifier = Modifier.padding(top = 12.dp)) {
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
                    // Mostra TODAS as colunas visíveis (não só as 4
                    // primeiras) -- pedido do usuário ("mostre o bloco
                    // completo... com todas as informações dos
                    // lançamentos"), em 2 colunas quando o card está
                    // expandido. Recolhido, continua com um resumo curto (as
                    // mesmas 4 primeiras de antes) pra lista não ficar
                    // gigante por padrão.
                    val allVisibleCols = cfg.columns.filter { !it.hideInTable && visibleKeys.contains(it.key) && !record[it.key].isNullOrBlank() }
                    val summaryCols = allVisibleCols.take(4)
                    val hasMore = allVisibleCols.size > summaryCols.size
                    val expanded = cardOverrides[recordId ?: ""] ?: allExpanded
                    val colsToShow = if (expanded) allVisibleCols else summaryCols
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
                                        pair.forEach { col ->
                                            Column(modifier = Modifier.weight(1f)) { RecordFieldLine(col, record[col.key]!!) }
                                        }
                                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                                    }
                                }
                            } else {
                                colsToShow.forEach { col -> RecordFieldLine(col, record[col.key]!!) }
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

// Uma linha de campo dentro do card de lançamento -- mesmo critério
// genérico já usado (StatusBadge pra colunas "status-like", texto simples
// pro resto), com maxLines/ellipsis porque agora divide espaço em 2 colunas
// quando expandido (sem isso, um valor longo espremia o layout).
@Composable
private fun RecordFieldLine(col: com.bragro.mobile.data.model.ColumnConfig, value: String) {
    if (isStatusLikeColumn(col.key)) {
        StatusBadge(value)
    } else {
        Text(
            "${col.label}: ${if (col.money) formatMoneyValue(value) else displayValueFor(col.key, value, col.type)}",
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(vertical = 2.dp),
        )
    }
}

// Localização fixa (mesmas coordenadas de src/app/(app)/clima/page.tsx) --
// ainda não existe latitude/longitude por fazenda no banco, então mostrar a
// localização real das coordenadas é mais honesto do que atribuir a uma
// fazenda escolhida ao acaso (mesmo motivo do comentário lá no site).
private const val CLIMA_REGIAO_PADRAO = "Tupaciguara/MG"

/** Bloco de KPIs do Clima -- réplica nativa e completa de
 * src/app/(app)/clima/clima-card.tsx: card colapsável (fechado por padrão)
 * com o "agora" (ícone + temperatura atual) e a previsão dos próximos 6 dias
 * (ícone, máx/mín, chuva prevista em mm) -- bem mais completo que o mini
 * card "Clima (agora)" do Início, que só mostra o resumo do dia atual. */
@Composable
private fun ClimaForecastCard(weather: WeatherResponse?) {
    var open by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.WbSunny, contentDescription = null, tint = BrYellow, modifier = Modifier.padding(end = 8.dp))
                Text(
                    "Clima — $CLIMA_REGIAO_PADRAO",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { open = !open }) {
                    Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = if (open) "Recolher" else "Expandir")
                }
            }
            if (open) {
                val clima = weather?.weather
                if (clima == null) {
                    Text(
                        "Não foi possível carregar a previsão no momento.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
                        Text(clima.currentIcon, style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.width(10.dp))
                        Text("${clima.currentTempC.toInt()}°C", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(8.dp))
                        Text("agora", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (clima.forecast.isNotEmpty()) {
                        Row(modifier = Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            clima.forecast.take(6).forEach { day ->
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(weekdayShortBr(day.date), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(day.icon, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 2.dp))
                                    Text("${day.maxC.toInt()}°/${day.minC.toInt()}°", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                    Text("${day.precipMm.toInt()}mm", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Réplica de fmtWeekdayShortUTC (lib/date-fmt.ts) -- parseia "yyyy-MM-dd" em
// UTC (mesma data em qualquer fuso do aparelho) e formata como abreviação de
// dia da semana em português.
private fun weekdayShortBr(isoDate: String): String {
    return try {
        val parser = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        parser.timeZone = java.util.TimeZone.getTimeZone("UTC")
        val date = parser.parse(isoDate) ?: return isoDate
        val fmt = java.text.SimpleDateFormat("EEE", java.util.Locale("pt", "BR"))
        fmt.timeZone = java.util.TimeZone.getTimeZone("UTC")
        fmt.format(date).removeSuffix(".").replaceFirstChar { it.uppercase() }
    } catch (e: Exception) {
        isoDate
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
