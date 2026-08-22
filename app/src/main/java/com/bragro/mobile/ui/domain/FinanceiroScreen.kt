package com.bragro.mobile.ui.domain

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import android.app.Application
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.KeyboardDoubleArrowDown
import androidx.compose.material.icons.filled.KeyboardDoubleArrowUp
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import com.bragro.mobile.ui.theme.Card
import com.bragro.mobile.ui.theme.appFieldColors
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bragro.mobile.data.NetworkStatus
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
    // "Nota com itens" (Task #156) saiu do Financeiro -- agora é um ícone
    // dentro do formulário "Novo Lançamento" (ver DomainFormScreen.kt/
    // onOpenNotaMultiItem), pedido do usuário ("em novo lançamento coloque
    // nota com itens e exclua de arquivos").
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
    val auditInfo by viewModel.auditInfo
    val context = LocalContext.current
    val bancoOptions by filtersViewModel.bancos

    // "Editado por" (ver DomainListScreen.kt) -- mesmo critério de lote por
    // lista de ids, reaproveitando o mesmo ViewModel/endpoint do módulo
    // genérico (Financeiro usa "domainId" = "financeiro" em audit_logs
    // também, já que passa pelo MESMO motor de CRUD em actions.ts).
    val recordIdsForAudit = remember(allRecords) { allRecords.mapNotNull { it["id"] } }
    LaunchedEffect(recordIdsForAudit) { viewModel.loadAuditInfo("financeiro", recordIdsForAudit) }

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

    // Recolher/expandir todos os lançamentos de uma vez -- pedido do usuário
    // ("os cards não ocultam"). ANTES esse estado só trocava o card entre
    // "resumo" (6 campos) e "completo" (todos os campos) -- ele nunca
    // escondia o card de fato, por isso parecia não funcionar. Agora
    // `allExpanded = false` esconde a lista inteira (tela limpa) e
    // `allExpanded = true` mostra todos os cards de novo, cada um começando
    // no modo resumo (o nível de detalhe de cada card continua sendo
    // controlado card a card por `cardOverrides`, sem depender mais deste
    // estado). Começa fechado -- pedido do usuário ("ao clicar no módulo a
    // tela deverá estar vazia").
    var allExpanded by remember { mutableStateOf(false) }
    val cardOverrides = remember { mutableStateMapOf<String, Boolean>() }
    // Confirmação antes de excluir -- mesmo padrão do módulo genérico
    // (DomainListScreen.kt), pedido do usuário ("implemente em todos os
    // blocos de lançamentos os ícones ver, editar e excluir... como foi
    // aplicado em safra").
    var recordPendingDelete by remember { mutableStateOf<String?>(null) }
    // "Ver" com diálogo de leitura próprio, separado da setinha de
    // expandir -- mesmo ajuste do módulo genérico (DomainListScreen.kt).
    var recordBeingViewed by remember { mutableStateOf<String?>(null) }

    // Blocos "compatíveis com ícone" (Gráficos, Calculadoras, Recalcular
    // Vencimentos, Filtros) consolidados numa fileira só, mesmo padrão do
    // módulo genérico (DomainListScreen.kt) -- pedido do usuário ("reduza os
    // blocos que são compatíveis a ícones, distribua numa linha só").
    val expandedBlocks = remember { mutableStateMapOf<String, Boolean>() }
    // Título FIXO (nome do setor) -- pedido do usuário ("agora que os
    // ícones estão tendo rótulos, pode deixar fixo o nome do setor ao
    // invés de alterar para parecer o nome do ícone no título"): removido
    // o subtítulo que ecoava o nome do bloco aberto (ex.: "Gráficos"),
    // já que cada ícone agora mostra seu próprio rótulo embaixo.

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
                    // "Financeiro" só aparece com Todos selecionado; ao
                    // clicar em outro ícone da Gestão Financeira, o nome da
                    // visão substitui a palavra "Financeiro" por completo
                    // (não mais "Financeiro — X") -- pedido do usuário.
                    // Título desce uma linha de fato -- mesmo padrão do
                    // Início: 1ª linha em branco, texto na linha de baixo.
                    // Setinha de recolher/expandir removida daqui -- pedido do
                    // usuário ("retire essa seta de recolher ao lado do
                    // título"); ela mora só dentro do bloco Dados agora.
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        // maxLines/ellipsis defensivo -- pedido do usuário
                        // ("adapte ao tamanho da fonte sem cortes e dentro
                        // do limite da tela").
                        Text(if (isQuickView) view.label else "Financeiro", maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.primary)
                    }
                },
                // A seta de voltar ganha o mesmo espaçador de cima do título
                // -- pedido do usuário ("a seta [voltar] tinha que ficar na
                // mesma altura do título"): sem isso ela fica centralizada
                // na altura ORIGINAL da AppBar, mais alta que o título, que
                // desceu uma linha.
                navigationIcon = {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar", tint = MaterialTheme.colorScheme.primary) }
                    }
                },
                // Nuvem/Imprimir promovidos pro canto superior direito, na
                // mesma linha do título -- pedido do usuário ("coloque em
                // todos os módulos os ícones imprimir e nuvem do lado
                // direito canto superior na mesma linha do título do
                // topo"). Saem dos blocos Registros/Distribuição (que agora
                // desaparecem -- ver Dados/Operações abaixo, que passam a
                // ocupar a linha inteira sozinhos). Mesmo Spacer de 16dp do
                // título/seta de voltar, pra ficarem alinhados na mesma
                // altura.
                actions = {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row {
                            // Ícone fazenda removido -- pedido do usuário
                            // ("exclua o ícone fazenda de dentro de todos os
                            // módulos"). Imprimir antes da Nuvem agora --
                            // pedido do usuário ("insira o ícone imprimir...
                            // deixe no lado superior direito na borda a
                            // primeira").
                            // Sem mais "&& filtered.isNotEmpty()" -- pedido
                            // do usuário ("force todos os ícones
                            // aparecerem"): ícone Imprimir sempre visível.
                            if (config != null) {
                                IconButton(onClick = { HtmlPrinter.printList(context, config!!, filtered, effectiveColumns.map { it.key }.toSet()) }) {
                                    Icon(Icons.Filled.Print, contentDescription = "Imprimir", tint = MaterialTheme.colorScheme.primary)
                                }
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
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            // Mesmo critério do site (isQuickView): visões rápidas são só
            // leitura, sem "Novo Lançamento".
            if (!isQuickView) {
                FloatingActionButton(
                    onClick = onNewRecord,
                    // Cores invertidas -- pedido do usuário ("inverta também
                    // as cores dos botões +, tendo como exemplo a cor do
                    // ícone fazenda").
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
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
            // fina, título "Gestão Financeira" acima -- pedido do usuário
            // ("no bloco 1 acima dele crie o título gestão financeira e
            // distribua os ícones dentro do bloco que chegue até o final do
            // bloco"). Sem scroll horizontal -- SpaceEvenly espalha os 7
            // ícones pela largura toda do bloco; toque no ícone já alterna o
            // título pro nome da visão (ver título acima), dispensando o
            // rótulo escrito.
            Text(
                "Gestão Financeira",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 2.dp),
            )
            // Cada visão (Todos/Contas a Pagar/.../Rateio Indireto) agora é
            // seu PRÓPRIO bloco (Card individual), em vez de todos os 7
            // ícones dividindo um único Card por baixo do título "Gestão
            // Financeira" -- pedido do usuário ("coloque cada ícone em um
            // bloco individual, retire o bloco único"). FlowRow (não Row
            // fixo) deixa cada bloco quebrar pra próxima linha sozinho
            // conforme a largura da tela, sem espremer.
            // SpaceEvenly (era spacedBy) -- pedido do usuário ("reposicione
            // os blocos individuais de forma que preencha toda a linha"),
            // mesmo padrão já usado nos blocos Dados/Operações/Arquivos
            // (ModuleCategoryBlock/FinanceiroCategoryBlock).
            // Card externo DE VOLTA -- pedido do usuário ("em gestão
            // financeira coloque um bloco externo envolvendo todos os
            // blocos individuais e coloque a cor de fundo do bloco externo
            // de verde, apenas o bloco externo"): reversão intencional do
            // ajuste anterior (que tinha removido esse Card por duplicar
            // borda -- ver histórico logo acima). Dessa vez é só ESTE bloco
            // (Gestão Financeira) que ganha o Card externo com fundo verde
            // translúcido -- os ModuleIconButton individuais dentro dele
            // continuam com o próprio fundo/borda de sempre, sem mudança.
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
            ) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FinanceiroView.values().forEach { v ->
                        ModuleIconButton(
                            ModuleIconItem(v.name, financeiroViewIcon(v), v.label, active = v == view),
                        ) { view = v }
                    }
                }
            }

            Spacer(modifier = Modifier.size(8.dp))

            // Bloco 2 desmembrado em 5 categorias -- pedido do usuário
            // ("desmembre todo bloco 2 segmentando esses blocos"). Recolher
            // tudo agora só fecha os blocos de Gráficos/Calculadoras/
            // Recalcular Vencimentos que estiverem abertos -- pedido do
            // usuário ("a única função será recolher todos os blocos
            // abertos completamente"), não afeta mais os cards de
            // lançamento (cada um já tem sua própria setinha).
            // Dados virou o bloco largo do topo (horizontal) -- pedido do
            // usuário no desenho mais recente -- e ganhou o filtro (Banco)
            // que estava em Operações e tinha ficado faltando aqui.
            val dadosBlock =
                FinBlockSpec("Dados", MaterialTheme.typography.titleSmall, vertical = false) {
                    if (!isQuickView) {
                        ModuleIconButton(
                            ModuleIconItem("charts", Icons.Filled.BarChart, "Gráficos", active = expandedBlocks["charts"] == true),
                        ) { expandedBlocks["charts"] = expandedBlocks["charts"] != true }
                    }
                    // Sem guard de view -- pedido do usuário ("quando o
                    // ícone todos tiver selecionado, tem que aparecer todos
                    // os ícones do bloco"): o Filtro (Banco) agora sempre
                    // aparece em Dados, não só na visão Conciliado.
                    BancoDropdown(banco = banco, options = bancoOptions, onSelect = { banco = it })
                    if (cfg != null) {
                        ColumnsPickerButton(
                            allColumns = viewColumns,
                            visibleKeys = customColumnKeys ?: viewColumns.map { it.key }.toSet(),
                            onChange = { customColumnKeys = it },
                        )
                    }
                    // Função única, esclarecida pelo usuário: só expande/
                    // recolhe os CARDS DE LANÇAMENTO (não Gráficos/
                    // Calculadoras/Recalcular Vencimentos/Período/nenhum
                    // outro ícone). Mesma ação da setinha ao lado do título
                    // (ver TopAppBar acima) -- pedido do usuário, os dois
                    // controlam o mesmo estado.
                    // Sempre visível -- pedido do usuário ("force todos os
                    // ícones aparecerem").
                    LabeledIconButton(
                        icon = if (allExpanded) Icons.Filled.KeyboardDoubleArrowUp else Icons.Filled.KeyboardDoubleArrowDown,
                        label = if (allExpanded) "Recolher" else "Expandir",
                        onClick = { allExpanded = !allExpanded; cardOverrides.clear() },
                    )
                }
            val operacoesBlock =
                FinBlockSpec("Operações", MaterialTheme.typography.titleSmall, vertical = false) {
                    if (!isQuickView) {
                        ModuleIconButton(
                            ModuleIconItem("calculators", Icons.Filled.Calculate, "Calculadoras", active = expandedBlocks["calculators"] == true),
                        ) { expandedBlocks["calculators"] = expandedBlocks["calculators"] != true }
                        // Icone diferente do "Atualizar" -- mesmo icone pra
                        // acoes diferentes na mesma tela confundia, pedido
                        // do usuario ("substitua icones que estejam iguais
                        // mas com funcoes diferentes").
                        ModuleIconButton(
                            ModuleIconItem("recalcular-vencimentos", Icons.Filled.Autorenew, "Recalcular", active = expandedBlocks["recalcular-vencimentos"] == true),
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
                    LabeledIconButton(
                        icon = Icons.Filled.Refresh,
                        label = "Atualizar",
                        loading = refreshing,
                        onClick = { viewModel.refresh("financeiro") },
                    )
                }
            val arquivosBlock =
                FinBlockSpec("Arquivos", MaterialTheme.typography.titleSmall, vertical = false) {
                    if (!isQuickView) {
                        LabeledIconButton(icon = Icons.Filled.Upload, label = "Extrato", onClick = onOpenBankImport)
                        LabeledIconButton(icon = Icons.Filled.Description, label = "XML", onClick = onOpenNfeImport)
                        // "Nota c/ itens" saiu daqui -- pedido do usuário
                        // ("em novo lançamento coloque nota com itens e
                        // exclua de arquivos"): agora é um ícone no topo do
                        // formulário "Novo Lançamento" (DomainFormScreen.kt),
                        // ao lado do ícone "Copiar último lançamento".
                    }
                    // Sem mais "&& filtered.isNotEmpty()" -- pedido do
                    // usuário ("force todos os ícones aparecerem").
                    if (cfg != null) {
                        // Ícone trocado pra planilha/tabela -- pedido do
                        // usuário ("use os ícones da demonstração...
                        // ficaram mais intuitivos"), em vez de um download
                        // genérico.
                        LabeledIconButton(
                            icon = Icons.Filled.GridOn,
                            label = "Excel",
                            onClick = { exportXlsx(context, "financeiro-${view.name.lowercase()}", effectiveColumns, filtered) },
                        )
                        // Ícone próprio pra PDF -- antes chamava a mesma
                        // função do "Imprimir" (HtmlPrinter.printList, abre o
                        // diálogo de impressão do sistema), então os dois
                        // ícones faziam exatamente a mesma coisa. Agora gera
                        // o PDF direto e abre no leitor instalado (Adobe ou
                        // similar) -- pedido do usuário.
                        LabeledIconButton(
                            icon = Icons.Filled.PictureAsPdf,
                            label = "PDF",
                            onClick = { HtmlPrinter.exportPdfDirect(context, cfg, filtered, effectiveColumns.map { it.key }.toSet()) },
                        )
                    }
                }
            // Nuvem e Imprimir saíram daqui -- foram promovidos pro canto
            // superior direito da TopAppBar, ao lado do título (ver Scaffold
            // acima) -- pedido do usuário. Dados/Operações/Arquivos viraram
            // uma barra oval (pill) que alterna entre categorias, mostrando
            // só os ícones da categoria selecionada -- pedido do usuário
            // ("use esse padrão com essa barra oval alternando entre as
            // categorias, mostre apenas os ícones de cada categoria"), mesmo
            // padrão aplicado nos demais módulos (DomainListScreen.kt/
            // ModuleCategoryTabs).
            FinanceiroCategoryTabs(
                listOf(dadosBlock, operacoesBlock, arquivosBlock),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            )

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
                            NetworkStatus.failureMessage(context),
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
                            if (allExpanded) {
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
                                // Nível de detalhe do card (resumo x completo) é
                                // só o controle individual do próprio card agora --
                                // sempre começa no resumo, independente do estado
                                // de recolher/expandir tudo (que só mostra/esconde
                                // a lista inteira, ver comentário acima).
                                val expanded = cardOverrides[recordId ?: ""] ?: false
                                val colsToShow = if (expanded) allCols else summaryCols
                                // Sem onClick no Card -- Ver/Editar/Excluir
                                // substituem o toque no bloco inteiro, mesmo
                                // padrão aplicado no módulo genérico.
                                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth()) {
                                        Column(modifier = Modifier.weight(1f).padding(12.dp)) {
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
                                            // Setinha (expandir/recolher aqui no card) separada de Ver
                                            // (diálogo de leitura completo) -- pedido do usuário, mesmo
                                            // ajuste do módulo genérico (DomainListScreen.kt).
                                            if (hasMore) {
                                                IconButton(onClick = { cardOverrides[recordId ?: ""] = !expanded }, modifier = Modifier.size(28.dp)) {
                                                    Icon(
                                                        if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                                        contentDescription = if (expanded) "Recolher lançamento" else "Expandir lançamento",
                                                        modifier = Modifier.size(18.dp),
                                                        tint = MaterialTheme.colorScheme.primary,
                                                    )
                                                }
                                            }
                                            IconButton(onClick = { recordBeingViewed = recordId }, modifier = Modifier.size(28.dp)) {
                                                Icon(Icons.Filled.Visibility, contentDescription = "Ver lançamento completo", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                            }
                                            IconButton(onClick = { if (recordId != null) onEditRecord(recordId) }, modifier = Modifier.size(28.dp)) {
                                                Icon(Icons.Filled.Edit, contentDescription = "Editar lançamento", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
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
                }
            }
        }
    }

    if (recordPendingDelete != null) {
        AlertDialog(
            onDismissRequest = { recordPendingDelete = null },
            title = { Text("Excluir lançamento?") },
            text = { Text("Essa ação não pode ser desfeita.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteRecord("financeiro", recordPendingDelete!!)
                    recordPendingDelete = null
                }) { Text("Excluir") }
            },
            dismissButton = {
                TextButton(onClick = { recordPendingDelete = null }) { Text("Cancelar") }
            },
        )
    }
    // Diálogo de leitura do ícone "Ver" -- mesmo padrão do módulo genérico
    // (DomainListScreen.kt): mostra TODOS os campos preenchidos do
    // lançamento, só leitura.
    if (recordBeingViewed != null) {
        val viewedRecord = filtered.firstOrNull { it["id"] == recordBeingViewed }
        AlertDialog(
            onDismissRequest = { recordBeingViewed = null },
            title = { Text(if (isQuickView) view.label else "Financeiro") },
            text = {
                if (viewedRecord == null) {
                    Text("Lançamento não encontrado.")
                } else {
                    Column(
                        modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        effectiveColumns.filter { it.key != "conciliar" && !viewedRecord[it.key].isNullOrBlank() }.forEach { col ->
                            FinanceiroFieldLine(col, viewedRecord[col.key])
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

// Bloco 2 desmembrado em 5 categorias -- pedido do usuário ("desmembre todo
// bloco 2 segmentando esses blocos"): cada categoria vira um Card próprio
// com título acima (proporcional ao tamanho/quantidade de ícones do bloco)
// e o conteúdo em fileira (horizontal) ou coluna (vertical, só o bloco
// "Dados"). Posições seguem o esboço do usuário (ver dadosBlock/
// operacoesBlock/arquivosBlock/armazenamentoBlock/distribuicaoBlock, mais
// abaixo, dentro de FinanceiroScreen).
private data class FinBlockSpec(
    val title: String,
    val titleStyle: androidx.compose.ui.text.TextStyle,
    val vertical: Boolean,
    val content: @Composable () -> Unit,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FinanceiroCategoryBlock(spec: FinBlockSpec, modifier: Modifier = Modifier, fillHeight: Boolean = false) {
    Column(modifier = modifier) {
        // BUG corrigido: quando o título ficava vazio, eu escondia o Text
        // por completo (if isNotEmpty) -- só que isso também removia o
        // ESPAÇO que o título ocupava, fazendo os blocos de 1 ícone
        // (Registros/Distribuição, sem título) ficarem mais altos que os
        // vizinhos com título (Dados/Operações), já que sobrava toda a
        // altura da linha pro Card sem nada "comendo" espaço em cima. Agora
        // o Text sempre existe (reserva a mesma altura em todo bloco da
        // linha) -- só o CONTEÚDO fica vazio quando spec.title é "".
        Text(
            spec.title,
            style = spec.titleStyle,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
        )
        if (spec.vertical) {
            // Centralizado (horizontal e vertical) -- pedido do usuário
            // ("centralize o ícone da impressora"); fillMaxHeight aqui
            // pra sobrar espaço de verdade pro Arrangement.Center atuar.
            // BUG corrigido: antes o Card sempre forçava fillMaxHeight(), o
            // que quebra a medição quando dois blocos ficam EMPILHADOS dentro
            // de uma Column sem altura própria (caso de Operações+Arquivos),
            // dentro de uma Row medida por IntrinsicSize.Min -- um bloco
            // "roubava" a altura do outro. Agora só usa `weight(1f)` quando o
            // chamador pede explicitamente via `fillHeight`.
            // Sem Card "por fora" -- pedido do usuário ("retire as bordas
            // das categorias que tiverem um bloco dentro do outro, retire a
            // borda externa"): cada ícone dentro já é seu próprio Card com
            // borda (ModuleIconButton/LabeledIconButton), então o Card
            // externo só duplicava a borda.
            Column(
                modifier = Modifier.fillMaxWidth().let { if (fillHeight) it.weight(1f) else it }.padding(8.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            ) { spec.content() }
        } else {
            // Sem Card "por fora" envolvendo tudo -- pedido do usuário ("em
            // todos os módulos, por categorias: dados, operações, arquivos
            // torne-os blocos com ícones individuais e redistribua de forma
            // que preencha toda a linha"): cada ícone já é seu próprio Card
            // (ver ModuleIconButton/LabeledIconButton em ModuleIconRow.kt),
            // então envolver tudo de novo aqui só duplicava a borda (card
            // dentro de card). SpaceEvenly distribui os cards individuais
            // pra ocupar a largura inteira da linha, mesmo padrão já usado
            // no seletor de visão "Gestão Financeira".
            // weight(1f) condicional preservado aqui (migrado do Card antigo)
            // -- é o que faz Dados/Operações combinarem em altura com
            // Nuvem/Imprimir ao lado, dentro da Row com IntrinsicSize.Min.
            FlowRow(
                modifier = Modifier.fillMaxWidth().let { if (fillHeight) it.weight(1f) else it },
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) { spec.content() }
        }
    }
}

// Barra oval (pill) alternando entre categorias (Dados/Operações/Arquivos),
// mostrando só os ícones da categoria selecionada -- pedido do usuário. Ver
// ModuleCategoryTabs (DomainListScreen.kt) pro mesmo padrão nos demais
// módulos; duplicado aqui (em vez de compartilhado) pelo mesmo motivo de
// FinanceiroCategoryBlock/ModuleCategoryBlock -- evitar mexer em código
// compartilhado entre os dois arquivos.
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FinanceiroCategoryTabs(blocks: List<FinBlockSpec>, modifier: Modifier = Modifier) {
    var selected by remember { mutableStateOf(0) }
    val safeSelected = selected.coerceIn(0, blocks.size - 1)
    Column(modifier = modifier) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            blocks.forEachIndexed { index, block ->
                SegmentedButton(
                    selected = safeSelected == index,
                    onClick = { selected = index },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = blocks.size),
                    // Fundo verde (mesma cor do ícone fazenda/primary) --
                    // pedido do usuário ("coloque também a cor de fundo dos
                    // blocos das categorias a mesma cor do ícone fazenda").
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = MaterialTheme.colorScheme.primary,
                        activeContentColor = MaterialTheme.colorScheme.onPrimary,
                        inactiveContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    ),
                    label = { Text(block.title) },
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        val active = blocks[safeSelected]
        if (active.vertical) {
            // Sem Card "por fora" -- pedido do usuário ("retire as bordas
            // das categorias que tiverem um bloco dentro do outro").
            Column(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
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

// shareFinanceiroResumo (ícone Compartilhar em txt) removido -- pedido do
// usuário ("exclua o ícone de compartilhar em txt, não só nesse módulo mas
// como em todos os outros").

/** Botão-dropdown "Período" -- 9 categorias de recorrência/vencimento
 * (inclui "Diário", adicionado em paralelo no site) + um intervalo de datas
 * manual (De/Até), espelho do dropdown Período do site (data-table.tsx). Em
 * Contas a Pagar/Receber as categorias viram janela de Vencimento; nas
 * demais visões casam pelo campo "Periodo" do lançamento. */
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

    // Ganhou rótulo "Período" -- varredura geral pedida pelo usuário
    // ("alguns ícones não receberam rótulos como colunas e períodos,
    // filtros").
    Box {
        LabeledIconButton(
            icon = Icons.Filled.CalendarMonth,
            label = "Período",
            // Sem tint proprio -- herda onSurface (preto/branco), pedido do
            // usuario ("tire o fundo verde de todos os blocos individuais").
            onClick = { expanded = true },
        )
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
                    colors = appFieldColors(),
                )
                OutlinedTextField(
                    value = toText,
                    onValueChange = { toText = it },
                    label = { Text("Até") },
                    placeholder = { Text("AAAA-MM-DD") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = appFieldColors(),
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
        LabeledIconButton(
            icon = Icons.Filled.FilterAlt,
            label = "Filtros",
            // Sem tint proprio -- herda onSurface (preto/branco), pedido do
            // usuario ("tire o fundo verde de todos os blocos individuais").
            onClick = { expanded = true },
        )
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
// Pagar/Receber trocados de ArrowUpward/ArrowDownward pra CallMade/
// CallReceived -- pedido do usuário ("substitua os ícones de a pagar e
// receber, não está intuitivo"): setas simples de cima/baixo confundiam
// (pareciam ordenação, não fluxo de caixa); CallMade/CallReceived (seta
// saindo/entrando de um canto) é o par clássico de "saída"/"entrada" de
// dinheiro, bem mais claro.
private fun financeiroViewIcon(v: FinanceiroView) = when (v) {
    FinanceiroView.TODOS -> Icons.Filled.ViewList
    FinanceiroView.PAGAR -> Icons.Filled.CallMade
    FinanceiroView.RECEBER -> Icons.Filled.CallReceived
    FinanceiroView.CONCILIADO -> Icons.Filled.CheckCircle
    FinanceiroView.FLUXO -> Icons.Filled.TrendingUp
    FinanceiroView.RATEIO_DIRETO -> Icons.Filled.CallSplit
    FinanceiroView.RATEIO_INDIRETO -> Icons.Filled.AccountTree
}

@Composable
private fun FluxoCard(row: FluxoRow, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "${row.original["entidade"] ?: "—"} — ${row.original["categoria"] ?: "—"}",
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text("Data movimento: ${row.dataMovimento?.let { displayValueFor("data", it, "date") } ?: "—"}", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Color(0xFF2F6F4F) (fixo) -> colorScheme.primary (adapta por
                // tema) -- pedido do usuário ("coloque as cores das fontes
                // preto/branco modo claro/escuro"): "Saída" já usava
                // colorScheme.error corretamente, "Entrada" era o único fora
                // do padrão.
                if (row.entrada > 0) Text("Entrada: ${formatMoneyValue(row.entrada.toString())}", color = MaterialTheme.colorScheme.primary)
                if (row.saida > 0) Text("Saída: ${formatMoneyValue(row.saida.toString())}", color = MaterialTheme.colorScheme.error)
            }
            Text("Saldo acumulado: ${formatMoneyValue(row.saldoAcumulado.toString())}", fontWeight = FontWeight.Bold)
        }
    }
}
