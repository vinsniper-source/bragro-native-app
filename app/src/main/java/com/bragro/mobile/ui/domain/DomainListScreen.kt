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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardDoubleArrowDown
import androidx.compose.material.icons.filled.KeyboardDoubleArrowUp
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TableChart
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
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
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
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
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
    // Filtros de coluna (Local/Categoria/Safra etc.) -- espelho de
    // filterableSelectCols em data-table.tsx, pedido do usuário ("consolide
    // todos os filtros de cada módulo num bloco Filtros só, com ícone").
    // Opções vêm dos próprios registros já carregados (não do catálogo de
    // Base de Dados inteiro) -- só mostra valor que realmente aparece na
    // lista, suficiente pra filtrar e evita outra chamada de rede aqui.
    val filterableSelectCols = config?.columns?.filter {
        it.type == "select" && !it.computed && (it.lookupCategory != null || !it.staticOptions.isNullOrEmpty())
    } ?: emptyList()
    val columnFilters = remember(domainId) { mutableStateMapOf<String, String>() }

    // Recolher/expandir todos os LANÇAMENTOS de uma vez -- pedido do usuário
    // ("a setinha só mexe nos cards de registro, sem abrir/fechar Filtros,
    // Gráficos, Calculadoras"; "os cards não ocultam" -- antes esse estado só
    // trocava entre resumo/completo, nunca escondia a lista de fato).
    // `allExpanded = false` esconde a lista inteira de lançamentos (tela
    // limpa); `true` mostra todos de novo, cada card começando no resumo.
    // Começa fechado -- pedido do usuário ("ao clicar no módulo a tela
    // deverá estar vazia").
    var allExpanded by remember(domainId) { mutableStateOf(false) }
    val cardOverrides = remember(domainId) { mutableStateMapOf<String, Boolean>() }
    // Bloco "Filtros" -- colapsável só pela própria setinha (ModuleIconButton
    // "filtros"), independente da seta de recolher/expandir lançamentos
    // acima (que não deve abrir/fechar Filtros, Gráficos ou Calculadoras).
    var filtrosOverride by remember(domainId) { mutableStateOf(false) }
    val filtrosExpanded = filtrosOverride

    // Estado de quais blocos "compatíveis com ícone" (Gráficos,
    // Calculadoras, Clima, Estoque por Fazenda, Recalcular Área) estão
    // abertos -- pedido do usuário ("reduza os blocos que são compatíveis a
    // ícones"). Tudo começa fechado; tocar o ícone na fileira revela o
    // bloco correspondente.
    val expandedBlocks = remember(domainId) { mutableStateMapOf<String, Boolean>() }
    // Nome de cada bloco, na mesma ordem em que aparecem na fileira de
    // ícones -- usado só pra saber qual nome mostrar no título quando um
    // bloco está aberto (ver título abaixo).
    val iconRowLabels = remember(domainId) {
        linkedMapOf(
            "charts" to "Gráficos",
            "calculators" to "Calculadoras",
            "clima-weather" to "Previsão do tempo",
            "estoque-fazenda" to "Transferências entre Fazendas",
            "recalcular-area" to "Recalcular Área",
            "filtros" to "Filtros",
        )
    }
    // Título alterna pro nome do bloco aberto -- pedido do usuário ("quando
    // clicar vai alternar o nome, por exemplo onde está a palavra
    // financeiro"); sem nenhum bloco aberto, volta pro nome do módulo.
    val activeBlockLabel = iconRowLabels.entries.firstOrNull { (key, _) ->
        if (key == "filtros") filtrosExpanded else expandedBlocks[key] == true
    }?.value

    // Botão "Colunas" (espelho do site) -- null = ainda não customizado pelo
    // usuário, mostra todas as colunas não ocultas (comportamento de sempre).
    var customVisibleKeys by remember(domainId) { mutableStateOf<Set<String>?>(null) }
    val allNonHiddenKeys = config?.columns?.filter { !it.hideInTable }?.map { it.key }?.toSet().orEmpty()
    val visibleKeys = customVisibleKeys ?: allNonHiddenKeys
    val filteredRecords = remember(records, periodo, intervalFrom, intervalTo, dateCol, columnFilters.toMap()) {
        var result = records
        if (dateCol != null) {
            if (periodo != null) {
                val (from, to) = genericPeriodoRange(periodo!!)
                result = filterByDateInterval(result, dateCol.key, from, to)
            }
            if (intervalFrom.isNotBlank() || intervalTo.isNotBlank()) {
                result = filterByDateInterval(result, dateCol.key, intervalFrom, intervalTo)
            }
        }
        columnFilters.forEach { (key, value) ->
            if (value.isNotBlank()) result = result.filter { it[key] == value }
        }
        result
    }

    Scaffold(
        topBar = {
            TopAppBar(
                // Título desce uma linha (mesmo ajuste do Financeiro) e
                // alterna: sem nenhum ícone/bloco aberto mostra o nome do
                // módulo; com um bloco aberto mostra SÓ o nome dele, sem
                // repetir o nome do módulo junto -- pedido do usuário.
                title = {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        // Setinha de recolher/expandir ao lado do título --
                        // pedido do usuário -- além da cópia que já existe na
                        // fileira de ícones (mesma ação nos dois lugares).
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text(activeBlockLabel ?: (config?.label ?: domainId))
                            if (filteredRecords.isNotEmpty()) {
                                IconButton(onClick = { allExpanded = !allExpanded; cardOverrides.clear() }, modifier = Modifier.size(28.dp)) {
                                    Icon(
                                        if (allExpanded) Icons.Filled.KeyboardDoubleArrowUp else Icons.Filled.KeyboardDoubleArrowDown,
                                        contentDescription = if (allExpanded) "Recolher todos os lançamentos" else "Expandir todos os lançamentos",
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar") }
                },
                // Sem "actions" aqui -- Atualizar/Recolher-Expandir/Colunas/
                // Exportar mudaram pra fileira de ícones abaixo do título
                // (item "module-icon-row"), pedido do usuário ("os ícones
                // que estavam do lado do título, coloque-os saltando uma
                // linha abaixo do título, e os insira no bloco").
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
            // propósito (é navegação de tela, não conteúdo do módulo). Virou
            // ícone (em vez de botão com texto) dentro de um bloco com borda
            // fina -- pedido do usuário ("transforme os botões [...] em
            // ícones sem mexer na estrutura dos blocos, coloque uma borda
            // fina em volta do bloco"). Toque no ícone já alterna o título
            // pro nome do outro módulo, então dispensa o rótulo escrito.
            if (linkedDomains != null && onSwitchDomain != null) {
                item(key = "linked-domain-switch") {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(8.dp),
                        ) {
                            linkedDomains.forEach { (id, label) ->
                                val active = id == domainId
                                ModuleIconButton(
                                    ModuleIconItem(id, linkedDomainIcon(id), label, active = active),
                                ) { if (!active) onSwitchDomain(id) }
                            }
                        }
                    }
                }
            }
            // Blocos "compatíveis com ícone" (Gráficos, Calculadoras, Clima,
            // Estoque por Fazenda, Recalcular Área, Filtros, Período) agora
            // moram numa fileira só de ícones logo abaixo do título do
            // módulo, em vez de cada um ocupar uma linha inteira só pro
            // próprio cabeçalho -- pedido do usuário ("reduza os blocos que
            // são compatíveis a ícones, distribua numa linha só abaixo do
            // título"). Tocar um ícone revela o conteúdo do bloco
            // correspondente logo abaixo desta fileira (Gráficos continua
            // sendo o primeiro conteúdo a aparecer, mesma regra de sempre);
            // toque longo mostra o nome do ícone.
            val showCalc = domainId == "safra" || domainId == "colheita" || domainId == "financeiro"
            val showClima = domainId == "clima"
            val showEstoqueFazenda = domainId == "estoque"
            val showRecalcularArea = domainId == "safra" || domainId == "frota"
            val showFiltros = filterableSelectCols.isNotEmpty()
            val activeFilterCountGeneric = columnFilters.values.count { it.isNotBlank() }
            item(key = "module-icon-row") {
                // Borda fina em volta do bloco -- mesmo padrão de todos os
                // outros Cards do app (Task #147) -- pedido do usuário
                // ("coloque uma borda fina em volta do bloco").
                Card(modifier = Modifier.fillMaxWidth()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    ModuleIconButton(
                        ModuleIconItem("charts", Icons.Filled.BarChart, "Gráficos", active = expandedBlocks["charts"] == true),
                    ) { expandedBlocks["charts"] = expandedBlocks["charts"] != true }
                    if (showCalc) {
                        ModuleIconButton(
                            ModuleIconItem("calculators", Icons.Filled.Calculate, "Calculadoras", active = expandedBlocks["calculators"] == true),
                        ) { expandedBlocks["calculators"] = expandedBlocks["calculators"] != true }
                    }
                    if (showClima) {
                        ModuleIconButton(
                            ModuleIconItem("clima-weather", Icons.Filled.WbSunny, "Previsão do tempo", active = expandedBlocks["clima-weather"] == true),
                        ) { expandedBlocks["clima-weather"] = expandedBlocks["clima-weather"] != true }
                    }
                    if (showEstoqueFazenda) {
                        ModuleIconButton(
                            ModuleIconItem("estoque-fazenda", Icons.Filled.CompareArrows, "Transferências entre Fazendas", active = expandedBlocks["estoque-fazenda"] == true),
                        ) { expandedBlocks["estoque-fazenda"] = expandedBlocks["estoque-fazenda"] != true }
                    }
                    if (showRecalcularArea) {
                        // Icone diferente do "Atualizar" da AppBar (Refresh)
                        // -- mesmo icone pra acoes diferentes na mesma tela
                        // confundia, pedido do usuario ("substitua icones
                        // que estejam iguais mas com funcoes diferentes").
                        ModuleIconButton(
                            ModuleIconItem("recalcular-area", Icons.Filled.Autorenew, "Recalcular Área"),
                        ) { expandedBlocks["recalcular-area"] = expandedBlocks["recalcular-area"] != true }
                    }
                    if (showFiltros) {
                        ModuleIconButton(
                            ModuleIconItem("filtros", Icons.Filled.FilterAlt, "Filtros", active = filtrosExpanded, badgeCount = activeFilterCountGeneric),
                        ) { filtrosOverride = !filtrosExpanded }
                    }
                    if (dateCol != null) {
                        GenericPeriodoDropdown(
                            periodo = periodo,
                            intervalFrom = intervalFrom,
                            intervalTo = intervalTo,
                            dateLabel = dateCol.label,
                            onPeriodo = { periodo = it; if (it != null) { intervalFrom = ""; intervalTo = "" } },
                            onInterval = { from, to -> intervalFrom = from; intervalTo = to; if (from.isNotBlank() || to.isNotBlank()) periodo = null },
                        )
                    }
                    // A partir daqui: ícones que antes moravam na AppBar
                    // (Atualizar/Recolher-Expandir/Colunas/Exportar) --
                    // pedido do usuário ("os ícones que estavam do lado do
                    // título, saltando uma linha, insira-os no bloco"; "os
                    // ícones que couberem no mesmo bloco unifique os
                    // blocos"). Mesmo comportamento de antes, só mudou de
                    // lugar.
                    IconButton(onClick = { viewModel.refresh(domainId) }) {
                        if (refreshing) CircularProgressIndicator(modifier = Modifier.padding(4.dp).size(20.dp))
                        else Icon(Icons.Filled.Refresh, contentDescription = "Atualizar")
                    }
                    if (filteredRecords.isNotEmpty()) {
                        // Só mexe nos cards de lançamento -- não fecha Filtros
                        // nem os outros blocos (Gráficos/Calculadoras/etc.),
                        // pedido do usuário.
                        IconButton(onClick = { allExpanded = !allExpanded; cardOverrides.clear() }) {
                            Icon(
                                if (allExpanded) Icons.Filled.KeyboardDoubleArrowUp else Icons.Filled.KeyboardDoubleArrowDown,
                                contentDescription = if (allExpanded) "Recolher todos os lançamentos" else "Expandir todos os lançamentos",
                            )
                        }
                    }
                    ColumnsPickerButton(
                        allColumns = cfg.columns.filter { !it.hideInTable },
                        visibleKeys = visibleKeys,
                        onChange = { customVisibleKeys = it },
                    )
                    if (filteredRecords.isNotEmpty()) {
                        // Ícones separados (mesmo padrão do Financeiro) em vez
                        // de um menu único -- pedido do usuário ("implemente
                        // nos módulos que não tiverem... csv, pdf, imprimir,
                        // compartilhar, nuvem").
                        IconButton(onClick = {
                            exportCsv(context, cfg.label, cfg.columns.filter { !it.hideInTable && visibleKeys.contains(it.key) }, filteredRecords)
                        }) {
                            Icon(Icons.Filled.TableChart, contentDescription = "Exportar CSV")
                        }
                        IconButton(onClick = {
                            HtmlPrinter.exportPdfDirect(context, cfg, filteredRecords, visibleKeys)
                        }) {
                            Icon(Icons.Filled.PictureAsPdf, contentDescription = "Exportar PDF")
                        }
                        IconButton(onClick = {
                            HtmlPrinter.printList(context, cfg, filteredRecords, visibleKeys)
                        }) {
                            Icon(Icons.Filled.Print, contentDescription = "Imprimir")
                        }
                        IconButton(onClick = {
                            shareRecordsResumo(context, cfg.label, cfg.columns.filter { !it.hideInTable && visibleKeys.contains(it.key) }, filteredRecords)
                        }) {
                            Icon(Icons.Filled.IosShare, contentDescription = "Compartilhar")
                        }
                    }
                    // Ícone nuvem (armazenamento/offline) -- reflete o estado
                    // de conexão, mesmo padrão do Financeiro ("Registros").
                    IconButton(onClick = {
                        val msg = if (offline) "Sem conexão -- mostrando o último resultado salvo neste aparelho." else "Conectado -- dados sincronizados com o servidor."
                        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(if (offline) Icons.Filled.CloudOff else Icons.Filled.Cloud, contentDescription = "Armazenamento")
                    }
                }
                }
            }
            if (expandedBlocks["charts"] == true) {
                item(key = "charts") { ModuleChartsCard(domainId, showHeader = false) }
            }
            if (showCalc && expandedBlocks["calculators"] == true) {
                item(key = "calculators") { CalculatorsCard(domainId, showHeader = false) }
            }
            if (showClima && expandedBlocks["clima-weather"] == true) {
                item(key = "clima-weather") { ClimaForecastCard(weather, showHeader = false) }
            }
            if (showEstoqueFazenda && expandedBlocks["estoque-fazenda"] == true) {
                item(key = "estoque-fazenda") { TransferenciasFazendaCard(showHeader = false) }
            }
            if (showRecalcularArea && expandedBlocks["recalcular-area"] == true) {
                item(key = "recalcular-area") { RecalcularAreaButton(domainId, showHeader = false) }
            }
            // Bloco "Filtros" -- conteúdo (só os dropdowns de coluna) some
            // se abre pelo ícone da fileira acima, sem cabeçalho próprio
            // (o ícone já cumpre esse papel agora).
            if (showFiltros && filtrosExpanded) {
                item(key = "filtros") {
                    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // 3 colunas por linha -- pedido do usuário
                            // ("os filtros coloquem em tres colunas dentro
                            // do mesmo bloco"); quebra pra linha de baixo
                            // se sobrar filtro.
                            filterableSelectCols.chunked(3).forEach { rowCols ->
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    rowCols.forEach { col ->
                                        val options = remember(records, col.key) {
                                            records.mapNotNull { it[col.key] }.filter { it.isNotBlank() }.distinct().sorted()
                                        }
                                        ColumnFilterRow(
                                            col = col,
                                            options = options,
                                            selected = columnFilters[col.key] ?: "",
                                            onSelect = { columnFilters[col.key] = it },
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                    repeat(3 - rowCols.size) { Spacer(Modifier.weight(1f)) }
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
            } else if (allExpanded) {
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
                    // Nível de detalhe do card (resumo x completo) é só o
                    // controle individual dele -- sempre começa no resumo; o
                    // `allExpanded` do topo agora só mostra/esconde a lista
                    // inteira (ver `if (allExpanded)` que envolve este `items`).
                    val expanded = cardOverrides[recordId ?: ""] ?: false
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
        val displayValue = if (col.money) formatMoneyValue(value) else displayValueFor(col.key, value, col.type)
        // Negrito só no valor preenchido, cabeçalho fica normal/mais claro --
        // pedido do usuário ("coloque ou o cabeçalho ou o campo preenchido em
        // negrito"), assim a linha não fica toda em negrito nem toda plana.
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) { append("${col.label}: ") }
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(displayValue) }
            },
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
private fun ClimaForecastCard(weather: WeatherResponse?, showHeader: Boolean = true) {
    // Sem cabeçalho, quem controla a visibilidade é a fileira de ícones do
    // módulo (ModuleIconRow) -- já nasce aberto.
    var open by remember { mutableStateOf(!showHeader) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (showHeader) {
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

    // Só ícone (sem texto "Período"/categoria selecionada) -- pedido do
    // usuário ("período isolado, só o ícone"), mesmo critério aplicado no
    // site (data-table.tsx). O estado ativo continua visível pela cor
    // preenchida do botão (igual a antes), só não escreve mais o rótulo.
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

/** Uma linha do bloco "Filtros" -- um dropdown de coluna (Local/Categoria/
 * Safra etc.), espelho do <Select> por coluna do site (filterableSelectCols
 * em data-table.tsx). Opções vêm dos valores distintos já presentes nos
 * registros carregados (sem chamada de rede extra). */
@Composable
private fun ColumnFilterRow(
    col: com.bragro.mobile.data.model.ColumnConfig,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = modifier.padding(bottom = 10.dp)) {
        Text(
            col.label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(bottom = 2.dp),
        )
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(selected.ifBlank { "Todos" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(text = { Text("Todos") }, onClick = { onSelect(""); expanded = false })
                if (options.isNotEmpty()) HorizontalDivider()
                options.forEach { opt ->
                    DropdownMenuItem(text = { Text(opt) }, onClick = { onSelect(opt); expanded = false })
                }
            }
        }
    }
}

/** Ícone de cada módulo "linkado" (Cobranças/NFS-e) no alternador -- só
 * esses dois ids existem hoje (ver BRAgroNavHost.kt), cada um com um ícone
 * bem distinto do outro pra não confundir. */
private fun linkedDomainIcon(domainId: String) = when (domainId) {
    "cobrancas" -> Icons.Filled.Receipt
    "nfse" -> Icons.Filled.Description
    else -> Icons.Filled.Description
}
