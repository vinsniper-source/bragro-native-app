package com.bragro.mobile.ui.domain

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardDoubleArrowDown
import androidx.compose.material.icons.filled.KeyboardDoubleArrowUp
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.bragro.mobile.data.NetworkStatus
import com.bragro.mobile.data.model.AuditEntry
import com.bragro.mobile.data.model.DomainConfig
import com.bragro.mobile.data.model.WeatherResponse
import com.bragro.mobile.data.repo.AuditInfoRepository
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
    private val auditInfoRepository = AuditInfoRepository(app)

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

    // "Editado por" + data/hora dentro do card (pedido do usuário, ver
    // AuditInfoRepository.kt) -- mapa recordId -> última edição, buscado
    // em UM lote sempre que a lista de registros muda (não um request por
    // card). Sem cache no Room de propósito, mesmo critério do bloco
    // Gráficos: só importa com a tela aberta e online.
    var auditInfo = mutableStateOf<Map<String, AuditEntry>>(emptyMap())
        private set

    fun loadAuditInfo(domainId: String, recordIds: List<String>) {
        viewModelScope.launch {
            val info = auditInfoRepository.fetch(domainId, recordIds)
            auditInfo.value = info
            // Task #124 (deteccao de conflito de sync) -- alem de exibir
            // "Editado por" (ja fazia isso), agora tambem grava esse mesmo
            // "createdAt" (timestamp ISO de RecordLastEdit.updatedAt no
            // servidor) como RecordEntity.expectedVersion de cada
            // lancamento -- e o que RecordRepository.updateRecord() manda
            // de volta em SyncRequest.expectedVersion quando o usuario
            // editar esse registro, pro backend detectar se outro
            // aparelho editou antes dele (409 CONFLICT).
            info.forEach { (recordId, entry) ->
                recordRepository.updateExpectedVersion(domainId, recordId, entry.createdAt)
            }
        }
    }

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

    // Ícone Excluir por lançamento -- pedido do usuário. `records` já é
    // observado via Flow (observeRecords, em load()), então a lista na tela
    // atualiza sozinha depois que o Room reflete a exclusão local -- não
    // precisa recarregar nada manualmente aqui.
    fun deleteRecord(domainId: String, recordId: String) {
        viewModelScope.launch { recordRepository.deleteRecord(domainId, recordId) }
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
    val auditInfo by viewModel.auditInfo
    val context = LocalContext.current

    // "Editado por" (ver comentário no ViewModel) -- busca em lote sempre
    // que a lista de ids muda (nova sincronização, filtro não afeta pois
    // usa "records" cru, não "filteredRecords"). LaunchedEffect só refaz a
    // chamada quando a lista de ids muda de verdade (List tem equals
    // estrutural), então não fica martelando a rede a cada recomposição.
    val recordIds = remember(records) { records.mapNotNull { it["id"] } }
    LaunchedEffect(domainId, recordIds) { viewModel.loadAuditInfo(domainId, recordIds) }
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

    // Filtro global de fazenda (ver FarmSelection.kt, equivalente ao
    // FarmSelector do cabecalho do site) -- quando ha uma fazenda escolhida
    // e este dominio e "farm-linked", pre-preenche o filtro de
    // Local/Fazenda deste modulo com ela, reaproveitando o MESMO
    // columnFilters usado pelos filtros manuais logo acima. O usuario ainda
    // pode trocar o filtro so nesta tela pelo dropdown de Filtros normal;
    // ao voltar e reabrir o modulo (ou trocar a selecao global), volta a
    // refletir a fazenda escolhida no cabecalho.
    LaunchedEffect(Unit) { FarmSelection.load(context) }
    val globalFarmField = remember(domainId) { FarmSelection.farmFieldFor(domainId) }
    val globalFarmSelected = FarmSelection.selected.value
    LaunchedEffect(domainId, globalFarmField, globalFarmSelected) {
        if (globalFarmField != null) {
            if (globalFarmSelected != null) columnFilters[globalFarmField] = globalFarmSelected else columnFilters.remove(globalFarmField)
        }
    }

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
    // Confirmação antes de excluir um lançamento (ícone Excluir, pedido do
    // usuário) -- guarda o id do lançamento aguardando confirmação; null =
    // nenhum diálogo aberto.
    var recordPendingDelete by remember(domainId) { mutableStateOf<String?>(null) }
    // "Ver" ganhou ação própria, separada da setinha de expandir -- pedido
    // do usuário ("a setinha será individual pra cada bloco... setinha
    // acima do ícone ver, depois editar e por último excluir"): guarda o id
    // do lançamento aberto no diálogo de leitura; null = nenhum diálogo.
    var recordBeingViewed by remember(domainId) { mutableStateOf<String?>(null) }
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
    // Título FIXO com o nome do setor/módulo -- pedido do usuário ("agora
    // que os ícones estão tendo rótulos, pode deixar fixo o nome do setor
    // ao invés de alterar para parecer o nome do ícone no título"): antes
    // o título trocava pro nome do bloco aberto (ex.: "Gráficos"), mas
    // agora que cada ícone já mostra seu próprio rótulo embaixo, não
    // precisa mais repetir esse nome no título -- reverte pro nome do
    // módulo sempre.

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
                // Setinha de recolher/expandir removida do título -- pedido
                // do usuário ("retire essa seta de recolher ao lado do
                // título"); ela mora só na fileira de ícones abaixo agora.
                title = {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        // maxLines/ellipsis defensivo -- pedido do usuário
                        // ("adapte ao tamanho da fonte sem cortes e dentro
                        // do limite da tela"): nomes de bloco dinâmicos
                        // longos (ex.: "Transferências entre Fazendas") sem
                        // isso podiam quebrar linha e cortar embaixo, já
                        // que a AppBar tem altura fixa.
                        Text(config?.label ?: domainId, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.primary)
                    }
                },
                // A seta de voltar ganha o mesmo espaçador do título -- pedido
                // do usuário ("a seta [voltar] tinha que ficar na mesma altura
                // do título"): sem isso ela fica mais alta, na altura ORIGINAL
                // da AppBar, enquanto o título desceu uma linha.
                navigationIcon = {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar", tint = MaterialTheme.colorScheme.primary) }
                    }
                },
                // Nuvem/Imprimir promovidos pro canto superior direito, sem
                // rótulo, na mesma linha do título -- pedido do usuário
                // ("coloque em todos os módulos os ícones imprimir e nuvem
                // do lado direito canto superior na mesma linha do título do
                // topo... retire o bloco e os rótulos dos dois"). Saem de
                // TODOS os blocos/fileiras abaixo (Registros/Distribuição/
                // Nuvem-Imprimir-Cobranças e a fileira única dos demais
                // módulos) -- essa é agora a ÚNICA cópia dos dois em todo o
                // módulo genérico. Além do filtro global de fazenda, que já
                // morava aqui.
                actions = {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row {
                            if (globalFarmField != null) {
                                FarmSelectorButton()
                            }
                            IconButton(onClick = {
                                val msg = if (offline) NetworkStatus.failureMessage(context) else "Conectado -- dados sincronizados com o servidor."
                                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(
                                    if (offline) Icons.Filled.CloudOff else Icons.Filled.Cloud,
                                    contentDescription = "Nuvem",
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                            if (config != null && filteredRecords.isNotEmpty()) {
                                IconButton(onClick = { HtmlPrinter.printList(context, config!!, filteredRecords, visibleKeys) }) {
                                    Icon(Icons.Filled.Print, contentDescription = "Imprimir", tint = MaterialTheme.colorScheme.primary)
                                }
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
            // propósito (é navegação de tela, não conteúdo do módulo). Virou
            // ícone (em vez de botão com texto) dentro de um bloco com borda
            // fina -- pedido do usuário ("transforme os botões [...] em
            // ícones sem mexer na estrutura dos blocos, coloque uma borda
            // fina em volta do bloco"). Toque no ícone já alterna o título
            // pro nome do outro módulo, então dispensa o rótulo escrito.
            if (linkedDomains != null && onSwitchDomain != null) {
                item(key = "linked-domain-switch") {
                    // Ganhou o título "Faturamento" acima do alternador
                    // NFSe/Cobranças (mesmo padrão de bloco com título dos
                    // outros módulos) e um bloco individual de Nuvem do lado,
                    // na mesma linha -- pedido do usuário ("coloque o nome da
                    // categoria de faturamento... e na mesma linha coloque o
                    // ícone nuvem em um bloco individual").
                    // Nuvem/Imprimir saíram daqui -- promovidos pro canto
                    // superior direito da TopAppBar (ver actions do Scaffold
                    // acima), sem rótulo, junto com os demais módulos --
                    // pedido do usuário ("coloque os ícones nuvem e imprimir
                    // no canto superior direito... retire o bloco e os
                    // rótulos dos dois"). Faturamento volta a ocupar a linha
                    // inteira sozinho.
                    val faturamentoBlock = ModuleBlockSpec("Faturamento", vertical = false) {
                        linkedDomains.forEach { (id, label) ->
                            val active = id == domainId
                            ModuleIconButton(
                                ModuleIconItem(id, linkedDomainIcon(id), label, active = active),
                            ) { if (!active) onSwitchDomain(id) }
                        }
                    }
                    ModuleCategoryBlock(faturamentoBlock, modifier = Modifier.fillMaxWidth())
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
            // Layout em blocos (Dados/Registros/Operações/Arquivos/
            // Distribuição), igual ao Financeiro -- pedido do usuário
            // ("aplique esse padrão nos módulos: romaneios, pragas,
            // receituarios, pedidos, contratos, caixa interno, inventário,
            // rh, controle interno"). Os outros módulos continuam com a
            // fileira única de ícones (sem blocos), inalterada.
            val useCategorizedBlocks = domainId in CATEGORIZED_BLOCK_DOMAINS
            // Safra/Clima/Planejamento Safra/Colheita/Frota: mesmo esqueleto
            // de blocos do padrão genérico acima, mas cada um com seu
            // próprio conjunto de ícones por bloco -- personalização
            // confirmada por imagem pra cada módulo (ver PER_MODULE_BLOCK_DOMAINS
            // abaixo).
            val useCustomBlocks = domainId in PER_MODULE_BLOCK_DOMAINS
            item(key = "module-icon-row") {
                if (useCategorizedBlocks) {
                    val dadosBlock = ModuleBlockSpec("Dados", vertical = false) {
                        ModuleIconButton(
                            ModuleIconItem("charts", Icons.Filled.BarChart, "Gráficos", active = expandedBlocks["charts"] == true),
                        ) { expandedBlocks["charts"] = expandedBlocks["charts"] != true }
                        if (showFiltros) {
                            ModuleIconButton(
                                ModuleIconItem("filtros", Icons.Filled.FilterAlt, "Filtros", active = filtrosExpanded, badgeCount = activeFilterCountGeneric),
                            ) { filtrosOverride = !filtrosExpanded }
                        }
                        ColumnsPickerButton(
                            allColumns = cfg.columns.filter { !it.hideInTable },
                            visibleKeys = visibleKeys,
                            onChange = { customVisibleKeys = it },
                        )
                        if (filteredRecords.isNotEmpty()) {
                            LabeledIconButton(
                                icon = if (allExpanded) Icons.Filled.KeyboardDoubleArrowUp else Icons.Filled.KeyboardDoubleArrowDown,
                                label = if (allExpanded) "Recolher" else "Expandir",
                                onClick = { allExpanded = !allExpanded; cardOverrides.clear() },
                            )
                        }
                    }
                    val operacoesBlock = ModuleBlockSpec("Operações", vertical = false) {
                        LabeledIconButton(
                            icon = Icons.Filled.Refresh,
                            label = "Atualizar",
                            loading = refreshing,
                            onClick = { viewModel.refresh(domainId) },
                        )
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
                    }
                    // Ícones compactos (36dp, mesma referência do
                    // ModuleIconButton) -- pedido do usuário ("coloque os
                    // blocos da linha 2 todos na horizontal e com limite de
                    // altura"): 2 IconButton no tamanho padrão (48dp) não
                    // cabiam lado a lado na largura do bloco Arquivos e
                    // quebravam pra 2 linhas, esticando a fileira inteira.
                    val arquivosBlock = ModuleBlockSpec("Arquivos", vertical = false) {
                        if (filteredRecords.isNotEmpty()) {
                            LabeledIconButton(
                                icon = Icons.Filled.GridOn,
                                label = "Excel",
                                onClick = {
                                    exportXlsx(context, cfg.label, cfg.columns.filter { !it.hideInTable && visibleKeys.contains(it.key) }, filteredRecords)
                                },
                            )
                            LabeledIconButton(
                                icon = Icons.Filled.PictureAsPdf,
                                label = "PDF",
                                onClick = {
                                    HtmlPrinter.exportPdfDirect(context, cfg, filteredRecords, visibleKeys)
                                },
                            )
                        }
                    }
                    // Nuvem/Imprimir e os blocos únicos que só existiam pra
                    // eles (Registros/Distribuição) saíram daqui -- pedido do
                    // usuário. Dados/Operações/Arquivos viraram uma barra
                    // oval (pill) que alterna entre categorias, mostrando só
                    // os ícones da categoria selecionada -- pedido do
                    // usuário ("use esse padrão com essa barra oval
                    // alternando entre as categorias, mostre apenas os
                    // ícones de cada categoria").
                    ModuleCategoryTabs(listOf(dadosBlock, operacoesBlock, arquivosBlock), modifier = Modifier.fillMaxWidth())
                } else if (useCustomBlocks) {
                    // Dados: igual pros 5 módulos -- gráfico, filtro, coluna,
                    // expandir/recolher (mesmo bloco Dados do padrão genérico).
                    val dadosBlock = ModuleBlockSpec("Dados", vertical = false) {
                        ModuleIconButton(
                            ModuleIconItem("charts", Icons.Filled.BarChart, "Gráficos", active = expandedBlocks["charts"] == true),
                        ) { expandedBlocks["charts"] = expandedBlocks["charts"] != true }
                        if (showFiltros) {
                            ModuleIconButton(
                                ModuleIconItem("filtros", Icons.Filled.FilterAlt, "Filtros", active = filtrosExpanded, badgeCount = activeFilterCountGeneric),
                            ) { filtrosOverride = !filtrosExpanded }
                        }
                        ColumnsPickerButton(
                            allColumns = cfg.columns.filter { !it.hideInTable },
                            visibleKeys = visibleKeys,
                            onChange = { customVisibleKeys = it },
                        )
                        if (filteredRecords.isNotEmpty()) {
                            LabeledIconButton(
                                icon = if (allExpanded) Icons.Filled.KeyboardDoubleArrowUp else Icons.Filled.KeyboardDoubleArrowDown,
                                label = if (allExpanded) "Recolher" else "Expandir",
                                onClick = { allExpanded = !allExpanded; cardOverrides.clear() },
                            )
                        }
                        // Transferências entra dentro de Dados (5º ícone) em
                        // vez de bloco individual -- pedido do usuário ("no
                        // módulo estoque em dados transfira o ícone
                        // transferência e expanda o bloco de 4 para 5
                        // ícones, e deixe nuvem na mesma linha").
                        if (domainId == "estoque") {
                            ModuleIconButton(
                                ModuleIconItem("estoque-fazenda", Icons.Filled.CompareArrows, "Transferências", active = expandedBlocks["estoque-fazenda"] == true),
                            ) { expandedBlocks["estoque-fazenda"] = expandedBlocks["estoque-fazenda"] != true }
                        }
                        // Previsão do tempo entra dentro de Dados também --
                        // era um bloco individual próprio (climaBlock),
                        // removido junto com os demais blocos de ícone único
                        // (pedido do usuário: "retire os blocos únicos dos
                        // blocos individuais").
                        if (domainId == "clima") {
                            ModuleIconButton(
                                ModuleIconItem("clima-weather", Icons.Filled.WbSunny, "Previsão", active = expandedBlocks["clima-weather"] == true),
                            ) { expandedBlocks["clima-weather"] = expandedBlocks["clima-weather"] != true }
                        }
                    }
                    // Operações: varia por módulo -- Safra (recalcular área +
                    // atualizar + período + calculadora), Clima/Planejamento
                    // Safra (só atualizar + período), Colheita (atualizar +
                    // período + calculadora), Frota (atualizar + período +
                    // recalcular área) -- personalização confirmada por
                    // imagem, módulo por módulo.
                    val operacoesBlock = ModuleBlockSpec("Operações", vertical = false) {
                        if (domainId == "safra") {
                            ModuleIconButton(
                                ModuleIconItem("recalcular-area", Icons.Filled.Autorenew, "Recalcular"),
                            ) { expandedBlocks["recalcular-area"] = expandedBlocks["recalcular-area"] != true }
                        }
                        LabeledIconButton(
                            icon = Icons.Filled.Refresh,
                            label = "Atualizar",
                            loading = refreshing,
                            onClick = { viewModel.refresh(domainId) },
                        )
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
                        if (domainId == "safra" || domainId == "colheita") {
                            ModuleIconButton(
                                ModuleIconItem("calculators", Icons.Filled.Calculate, "Calculadoras", active = expandedBlocks["calculators"] == true),
                            ) { expandedBlocks["calculators"] = expandedBlocks["calculators"] != true }
                        }
                        if (domainId == "frota") {
                            ModuleIconButton(
                                ModuleIconItem("recalcular-area", Icons.Filled.Autorenew, "Recalcular"),
                            ) { expandedBlocks["recalcular-area"] = expandedBlocks["recalcular-area"] != true }
                        }
                    }
                    // Mesmo ajuste de tamanho do bloco genérico acima --
                    // pedido do usuário ("coloque os blocos da linha 2
                    // todos na horizontal e com limite de altura").
                    val arquivosBlock = ModuleBlockSpec("Arquivos", vertical = false) {
                        if (filteredRecords.isNotEmpty()) {
                            LabeledIconButton(
                                icon = Icons.Filled.GridOn,
                                label = "Excel",
                                onClick = {
                                    exportXlsx(context, cfg.label, cfg.columns.filter { !it.hideInTable && visibleKeys.contains(it.key) }, filteredRecords)
                                },
                            )
                            LabeledIconButton(
                                icon = Icons.Filled.PictureAsPdf,
                                label = "PDF",
                                onClick = {
                                    HtmlPrinter.exportPdfDirect(context, cfg, filteredRecords, visibleKeys)
                                },
                            )
                        }
                    }
                    // Nuvem/Imprimir e todo o esqueleto de Row/weight
                    // específico por módulo (clima/estoque/safra/demais)
                    // saíram daqui -- pedido do usuário. Dados/Operações/
                    // Arquivos viraram a mesma barra oval alternando
                    // categorias usada no grupo de blocos genérico acima.
                    ModuleCategoryTabs(listOf(dadosBlock, operacoesBlock, arquivosBlock), modifier = Modifier.fillMaxWidth())
                } else if (linkedDomains != null) {
                    // Cobranças/NFS-e: linha 2 do módulo em blocos Dados/
                    // Operações/Arquivos (Faturamento+Nuvem+Imprimir já
                    // ficam na linha 1, ver item "linked-domain-switch"
                    // acima) -- pedido do usuário ("crie a categoria dados
                    // e coloque... gráfico, filtros, expandir e colunas...
                    // bloco operações... atualizar e período... bloco
                    // arquivos... csv e pdf").
                    val dadosBlockCobrancas = ModuleBlockSpec("Dados", vertical = false) {
                        ModuleIconButton(
                            ModuleIconItem("charts", Icons.Filled.BarChart, "Gráficos", active = expandedBlocks["charts"] == true),
                        ) { expandedBlocks["charts"] = expandedBlocks["charts"] != true }
                        if (showFiltros) {
                            ModuleIconButton(
                                ModuleIconItem("filtros", Icons.Filled.FilterAlt, "Filtros", active = filtrosExpanded, badgeCount = activeFilterCountGeneric),
                            ) { filtrosOverride = !filtrosExpanded }
                        }
                        if (filteredRecords.isNotEmpty()) {
                            LabeledIconButton(
                                icon = if (allExpanded) Icons.Filled.KeyboardDoubleArrowUp else Icons.Filled.KeyboardDoubleArrowDown,
                                label = if (allExpanded) "Recolher" else "Expandir",
                                onClick = { allExpanded = !allExpanded; cardOverrides.clear() },
                            )
                        }
                        ColumnsPickerButton(
                            allColumns = cfg.columns.filter { !it.hideInTable },
                            visibleKeys = visibleKeys,
                            onChange = { customVisibleKeys = it },
                        )
                    }
                    // vertical = true -- pedido do usuário ("cobranças
                    // coloque o bloco operações com 2 ícones na vertical").
                    val operacoesBlockCobrancas = ModuleBlockSpec("Operações", vertical = true) {
                        LabeledIconButton(
                            icon = Icons.Filled.Refresh,
                            label = "Atualizar",
                            loading = refreshing,
                            onClick = { viewModel.refresh(domainId) },
                        )
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
                    }
                    val arquivosBlockCobrancas = ModuleBlockSpec("Arquivos", vertical = false) {
                        if (filteredRecords.isNotEmpty()) {
                            LabeledIconButton(
                                icon = Icons.Filled.GridOn,
                                label = "Excel",
                                onClick = { exportXlsx(context, cfg.label, cfg.columns.filter { !it.hideInTable && visibleKeys.contains(it.key) }, filteredRecords) },
                            )
                            LabeledIconButton(
                                icon = Icons.Filled.PictureAsPdf,
                                label = "PDF",
                                onClick = { HtmlPrinter.exportPdfDirect(context, cfg, filteredRecords, visibleKeys) },
                            )
                        }
                    }
                    // Mesma barra oval alternando categorias -- pedido do
                    // usuário.
                    ModuleCategoryTabs(listOf(dadosBlockCobrancas, operacoesBlockCobrancas, arquivosBlockCobrancas), modifier = Modifier.fillMaxWidth())
                } else {
                // Sem Card "por fora" -- cada ícone já é seu próprio Card
                // (ModuleIconButton/LabeledIconButton), e SpaceEvenly
                // distribui pra preencher a linha inteira -- pedido do
                // usuário ("padronize o tamanho dos blocos dos ícones e que
                // preencha toda a linha"), mesmo padrão já usado nos blocos
                // Dados/Operações/Arquivos dos outros módulos.
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
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
                            ModuleIconItem("clima-weather", Icons.Filled.WbSunny, "Previsão", active = expandedBlocks["clima-weather"] == true),
                        ) { expandedBlocks["clima-weather"] = expandedBlocks["clima-weather"] != true }
                    }
                    if (showEstoqueFazenda) {
                        ModuleIconButton(
                            ModuleIconItem("estoque-fazenda", Icons.Filled.CompareArrows, "Transferências", active = expandedBlocks["estoque-fazenda"] == true),
                        ) { expandedBlocks["estoque-fazenda"] = expandedBlocks["estoque-fazenda"] != true }
                    }
                    if (showRecalcularArea) {
                        // Icone diferente do "Atualizar" da AppBar (Refresh)
                        // -- mesmo icone pra acoes diferentes na mesma tela
                        // confundia, pedido do usuario ("substitua icones
                        // que estejam iguais mas com funcoes diferentes").
                        ModuleIconButton(
                            ModuleIconItem("recalcular-area", Icons.Filled.Autorenew, "Recalcular"),
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
                    LabeledIconButton(
                        icon = Icons.Filled.Refresh,
                        label = "Atualizar",
                        loading = refreshing,
                        onClick = { viewModel.refresh(domainId) },
                    )
                    if (filteredRecords.isNotEmpty()) {
                        // Só mexe nos cards de lançamento -- não fecha Filtros
                        // nem os outros blocos (Gráficos/Calculadoras/etc.),
                        // pedido do usuário.
                        LabeledIconButton(
                            icon = if (allExpanded) Icons.Filled.KeyboardDoubleArrowUp else Icons.Filled.KeyboardDoubleArrowDown,
                            label = if (allExpanded) "Recolher" else "Expandir",
                            onClick = { allExpanded = !allExpanded; cardOverrides.clear() },
                        )
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
                        LabeledIconButton(
                            icon = Icons.Filled.GridOn,
                            label = "Excel",
                            onClick = { exportXlsx(context, cfg.label, cfg.columns.filter { !it.hideInTable && visibleKeys.contains(it.key) }, filteredRecords) },
                        )
                        LabeledIconButton(
                            icon = Icons.Filled.PictureAsPdf,
                            label = "PDF",
                            onClick = { HtmlPrinter.exportPdfDirect(context, cfg, filteredRecords, visibleKeys) },
                        )
                        // Ícone Compartilhar (txt) removido -- pedido do
                        // usuário ("exclua o ícone de compartilhar em txt...
                        // em todos os outros [módulos]").
                    }
                    // Nuvem e Imprimir saíram daqui -- agora só na TopAppBar
                    // (ver actions do Scaffold acima), pedido do usuário.
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
                        NetworkStatus.failureMessage(context),
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
                // Agrupamento visual de Safra (Safra+Cultura+Local+Operação
                // no mesmo bloco) foi implementado e DESFEITO a pedido do
                // usuário na mesma sessão -- volta a ser uma lista plana,
                // cada lançamento seu próprio card, igual aos demais módulos.
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
                    val allVisibleCols = cfg.columns.filter {
                        !it.hideInTable && visibleKeys.contains(it.key) && !record[it.key].isNullOrBlank()
                    }
                    val summaryCols = allVisibleCols.take(4)
                    val hasMore = allVisibleCols.size > summaryCols.size
                    // Nível de detalhe do card (resumo x completo) é só o
                    // controle individual dele -- sempre começa no resumo; o
                    // `allExpanded` do topo agora só mostra/esconde a lista
                    // inteira (ver `if (allExpanded)` que envolve este `items`).
                    val expanded = cardOverrides[recordId ?: ""] ?: false
                    val colsToShow = if (expanded) allVisibleCols else summaryCols
                    // Card não tem onClick pra editar -- pedido do usuário
                    // ("crie individualmente em cada bloco ícone ver, editar
                    // e excluir no lado direito, na vertical"). 4 ícones
                    // agora, cada um com sua própria ação -- pedido do
                    // usuário ("a setinha será individual pra cada bloco...
                    // setinha acima do ícone ver, depois editar e por último
                    // excluir"): setinha só expande/recolhe os campos AQUI
                    // dentro do card (resumo x completo); Ver abre um
                    // diálogo de leitura com TODOS os campos preenchidos,
                    // sem precisar expandir o card pra ver tudo. Esses 4
                    // ícones só existem aqui (Composable da tela) --
                    // HtmlPrinter/exportXlsx leem os registros direto do
                    // banco/lista, nunca essa árvore de UI, então nunca
                    // aparecem no Excel/PDF/impressão.
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .then(
                                if (isLastOfGroup) Modifier.padding(bottom = 8.dp) else Modifier
                            ),
                    ) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.weight(1f).padding(12.dp)) {
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
                                // "Editado por" + data/hora (pedido do
                                // usuário) -- só aparece quando já existe
                                // ALGUMA edição registrada (histórico de
                                // alterações); some sozinho depois de 7
                                // dias junto com o resto do audit_logs
                                // (purge-audit-logs, ver vercel.json).
                                auditInfo[recordId]?.let { entry ->
                                    Text(
                                        formatAuditEntry(entry),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 6.dp),
                                    )
                                }
                            }
                            Column(
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(0.dp),
                            ) {
                                if (hasMore) {
                                    IconButton(onClick = { cardOverrides[recordId ?: ""] = !expanded }, modifier = Modifier.size(28.dp)) {
                                        Icon(
                                            if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                            contentDescription = if (expanded) "Recolher lançamento" else "Expandir lançamento",
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                                IconButton(onClick = { recordBeingViewed = recordId }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Filled.Visibility, contentDescription = "Ver lançamento completo", modifier = Modifier.size(18.dp))
                                }
                                IconButton(onClick = { if (recordId != null) onEditRecord(recordId) }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Filled.Edit, contentDescription = "Editar lançamento", modifier = Modifier.size(18.dp))
                                }
                                IconButton(onClick = { recordPendingDelete = recordId }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Excluir lançamento", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Confirmação antes de excluir -- ação destrutiva, pedido implícito de
    // segurança (excluir não pode ser desfeito depois de sincronizar).
    if (recordPendingDelete != null) {
        AlertDialog(
            onDismissRequest = { recordPendingDelete = null },
            title = { Text("Excluir lançamento?") },
            text = { Text("Essa ação não pode ser desfeita.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteRecord(domainId, recordPendingDelete!!)
                    recordPendingDelete = null
                }) { Text("Excluir") }
            },
            dismissButton = {
                TextButton(onClick = { recordPendingDelete = null }) { Text("Cancelar") }
            },
        )
    }
    // Diálogo de leitura do ícone "Ver" -- pedido do usuário (ver ação
    // 22.: 4 ícones separados no card). Mostra TODOS os campos preenchidos
    // do lançamento, só leitura, sem precisar expandir o card na lista.
    if (recordBeingViewed != null) {
        // `cfg` (a val local não-nula) só existe dentro do lambda do
        // Scaffold, e este diálogo fica fora dele -- usa `config` (o State
        // nullable, visível na função inteira) direto, com fallback.
        val viewedConfig = config
        val viewedRecord = records.firstOrNull { it["id"] == recordBeingViewed }
        AlertDialog(
            onDismissRequest = { recordBeingViewed = null },
            title = { Text(viewedConfig?.label ?: domainId) },
            text = {
                if (viewedRecord == null || viewedConfig == null) {
                    Text("Lançamento não encontrado.")
                } else {
                    Column(
                        modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        viewedConfig.columns.filter { !it.hideInTable && !viewedRecord[it.key].isNullOrBlank() }.forEach { col ->
                            RecordFieldLine(col, viewedRecord[col.key]!!)
                        }
                        auditInfo[recordBeingViewed]?.let { entry ->
                            Text(
                                formatAuditEntry(entry),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { recordBeingViewed = null }) { Text("Fechar") }
            },
        )
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

// Módulos que usam o layout em blocos (Dados/Registros/Operações/Arquivos/
// Distribuição), igual ao Financeiro -- pedido do usuário. Os demais
// módulos continuam com a fileira única de ícones (ver useCategorizedBlocks
// acima).
private val CATEGORIZED_BLOCK_DOMAINS = setOf(
    "romaneios", "pragas", "receituarios", "pedidos", "contratos",
    "caixainterno", "inventario", "rh", "controleinterno",
)

// Safra/Clima/Planejamento Safra/Colheita/Frota: mesmo layout em blocos
// acima, mas com conjunto de ícones próprio por módulo (personalização
// confirmada por imagem, módulo por módulo -- ver ramo useCustomBlocks em
// "module-icon-row").
private val PER_MODULE_BLOCK_DOMAINS = setOf(
    "safra", "clima", "planejamentosafra", "colheita", "frota", "estoque",
)

// Espelho de FinBlockSpec/FinanceiroCategoryBlock (FinanceiroScreen.kt) --
// mesmo padrão de bloco com título + Card (fileira horizontal ou coluna
// vertical de ícones), reaproveitado aqui pros módulos genéricos listados
// acima. Duplicado (em vez de compartilhado) pra não arriscar mexer no
// Financeiro, que já está funcionando, só pra extrair código comum.
private data class ModuleBlockSpec(
    val title: String,
    val vertical: Boolean,
    val content: @Composable () -> Unit,
)

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ModuleCategoryBlock(spec: ModuleBlockSpec, modifier: Modifier = Modifier, fillHeight: Boolean = false) {
    Column(modifier = modifier) {
        // Título sempre existe (mesmo vazio) pra reservar a mesma altura em
        // todo bloco da linha -- ver comentário equivalente em
        // FinanceiroCategoryBlock (bug corrigido: esconder o Text por
        // completo quando vazio desalinhava os blocos de 1 ícone).
        Text(
            spec.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
        )
        if (spec.vertical) {
            // Sem Card "por fora" -- pedido do usuário ("retire as bordas
            // das categorias que tiverem um bloco dentro do outro, retire a
            // borda externa"): cada ícone dentro (ModuleIconButton/
            // LabeledIconButton) já é seu próprio Card com borda, então o
            // Card externo só duplicava a borda (bloco dentro de bloco).
            // Mesmo ajuste já feito no ramo `else` abaixo (não-vertical).
            Column(
                modifier = Modifier.fillMaxWidth().let { if (fillHeight) it.weight(1f) else it }.padding(8.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            ) { spec.content() }
        } else {
            // Sem Card "por fora" -- mesmo ajuste de FinanceiroCategoryBlock
            // (FinanceiroScreen.kt): pedido do usuário ("em todos os
            // módulos, por categorias: dados, operações, arquivos torne-os
            // blocos com ícones individuais e redistribua de forma que
            // preencha toda a linha"). Cada ícone já é seu próprio Card
            // (ModuleIconButton/LabeledIconButton, ModuleIconRow.kt) -- essa
            // troca vale pros 14 módulos que usam CATEGORIZED_BLOCK_DOMAINS/
            // PER_MODULE_BLOCK_DOMAINS de uma vez só.
            FlowRow(
                modifier = Modifier.fillMaxWidth().let { if (fillHeight) it.weight(1f) else it },
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) { spec.content() }
        }
    }
}

// Alternador de categorias em barra oval (pill), mostrando só os ícones da
// categoria selecionada -- pedido do usuário ("use esse padrão com essa
// barra oval alternando entre as categorias, mostre apenas os ícones de
// cada categoria"), a partir do print do módulo Safra onde as 3 categorias
// (Dados/Operações/Arquivos) apareciam empilhadas com todos os ícones
// juntos de uma vez. Substitui o Column de N ModuleCategoryBlock nos 3
// pontos que usam esse padrão (blocos genéricos, blocos por módulo em
// PER_MODULE_BLOCK_DOMAINS, e Cobranças/NFS-e).
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ModuleCategoryTabs(blocks: List<ModuleBlockSpec>, modifier: Modifier = Modifier) {
    var selected by remember { mutableStateOf(0) }
    // Trava o índice dentro dos limites -- essa mesma instância do
    // Composable é reaproveitada ao trocar de módulo (ex.: Cobranças <->
    // NFS-e), então o número de blocos pode variar entre recomposições.
    val safeSelected = selected.coerceIn(0, blocks.size - 1)
    Column(modifier = modifier) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            blocks.forEachIndexed { index, block ->
                SegmentedButton(
                    selected = safeSelected == index,
                    onClick = { selected = index },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = blocks.size),
                    label = { Text(block.title) },
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        val active = blocks[safeSelected]
        if (active.vertical) {
            // Mesmo ajuste do ModuleCategoryBlock acima -- sem Card externo.
            Column(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { active.content() }
        } else {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) { active.content() }
        }
    }
}

// "Editado por" + data/hora (pedido do usuário) -- sem `private` porque
// FinanceiroScreen.kt (mesmo pacote) tem seu próprio card de lançamento e
// reaproveita esta função em vez de duplicá-la. `createdAt` chega em ISO
// 8601 UTC (Prisma DateTime.toISOString()); exibido convertido pro fuso do
// aparelho, formato dd/MM HH:mm (mesmo padrão de formatUpdatedAt no Início).
fun formatAuditEntry(entry: com.bragro.mobile.data.model.AuditEntry): String {
    val parser = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
        timeZone = java.util.TimeZone.getTimeZone("UTC")
    }
    val display = java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale("pt", "BR"))
    val whenText = try { display.format(parser.parse(entry.createdAt)!!) } catch (e: Exception) { entry.createdAt }
    return "Editado por ${entry.userEmail} em $whenText"
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
 * FinanceiroScreen.kt), aplicado aos demais ~17 módulos: 9 categorias (inclui
 * "Diário") como janela de data PARA TRÁS a partir de hoje + intervalo
 * manual, sobre a 1ª coluna de data do domínio -- mesmo critério de
 * genericPeriodoRange (data-table.tsx). */
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

    // Ganhou rótulo "Período" -- varredura geral pedida pelo usuário
    // ("alguns ícones não receberam rótulos como colunas e períodos,
    // filtros"). Estado ativo continua visível pela cor preenchida.
    Box {
        LabeledIconButton(
            icon = Icons.Filled.CalendarMonth,
            label = "Período",
            tint = if (hasFilter) MaterialTheme.colorScheme.primary else LocalContentColor.current,
            onClick = { expanded = true },
        )
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
