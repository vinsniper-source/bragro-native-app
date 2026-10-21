package com.bragro.mobile.ui.pivos

import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.FilterAlt
import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.OfflineBolt
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Opacity
import androidx.compose.material.icons.outlined.Power
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.size
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import com.bragro.mobile.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bragro.mobile.data.model.BalancoHidricoPontoData
import com.bragro.mobile.data.model.FarmLookupData
import com.bragro.mobile.data.model.PivoData
import com.bragro.mobile.data.model.PivoTelemetriaLogData
import com.bragro.mobile.ui.domain.ModuleChartsCard
import com.bragro.mobile.data.repo.PivosRepository
import com.bragro.mobile.ui.domain.BarSeries
import com.bragro.mobile.ui.domain.ModuleProviderIntegrationCard
import com.bragro.mobile.ui.domain.SimpleBarChart
import com.bragro.mobile.ui.theme.Card
import com.bragro.mobile.ui.theme.SearchableDropdownField
import com.bragro.mobile.ui.theme.appFieldColors
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

// Pivôs de Irrigação (Lindsay FieldNET/Valley 365-AgSense/Reinke ReinCloud)
// -- pedido do usuário com documentação técnica completa de integração
// OAuth2. Mesmo scaffolding dos demais módulos com fabricante externo
// (Frota/Romaneio/Pecuária): cadastro, telemetria e "controle" 100%
// funcionais via lançamento manual; sincronização automática real fica
// stub (ver ModuleProviderIntegrationCard, domainId "pivos") até alguma
// marca aprovar parceria de desenvolvedor.

private val STATUS_OPTIONS = listOf("RUNNING_WATER" to "Irrigando", "RUNNING_DRY" to "Girando (sem água)", "STOPPED" to "Parado", "OFFLINE" to "Offline")
private val SENTIDO_OPTIONS = listOf("CLOCKWISE" to "Horário", "COUNTER_CLOCKWISE" to "Anti-horário")
private val MARCAS = listOf("Lindsay FieldNET", "Valley 365/AgSense", "Reinke ReinCloud", "Outro")

private fun statusLabel(status: String?): String = STATUS_OPTIONS.firstOrNull { it.first == status }?.second ?: "Offline"
private fun todayBr(): String = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).format(java.util.Date())
private fun brDateToIsoOrNull(br: String): String? {
    val m = Regex("^(\\d{2})/(\\d{2})/(\\d{4})$").find(br.trim()) ?: return null
    val (d, mo, y) = m.destructured
    return "$y-$mo-$d"
}

class PivosViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = PivosRepository(app)

    var pivos = mutableStateOf<List<PivoData>>(emptyList())
        private set
    var farms = mutableStateOf<List<FarmLookupData>>(emptyList())
        private set
    var balanco = mutableStateOf<List<BalancoHidricoPontoData>>(emptyList())
        private set
    var historico = mutableStateOf<List<PivoTelemetriaLogData>>(emptyList())
        private set
    var historicoLoading = mutableStateOf(false)
        private set
    var loading = mutableStateOf(false)
        private set
    var busy = mutableStateOf(false)
        private set
    var errorMessage = mutableStateOf<String?>(null)
        private set

    fun load() {
        loading.value = true
        viewModelScope.launch {
            pivos.value = repo.listar()?.pivos ?: emptyList()
            farms.value = repo.listarFazendas()
            balanco.value = repo.balancoHidrico()?.balanco ?: emptyList()
            loading.value = false
        }
    }

    /** Histórico de telemetria (últimos 60 dias) de um pivô. */
    fun carregarHistorico(pivoId: String) {
        historico.value = emptyList()
        historicoLoading.value = true
        viewModelScope.launch {
            historico.value = repo.listarTelemetria(pivoId, 60)?.logs ?: emptyList()
            historicoLoading.value = false
        }
    }

    fun refreshBalanco(pivoId: String?) {
        viewModelScope.launch { balanco.value = repo.balancoHidrico(pivoId)?.balanco ?: emptyList() }
    }

    fun salvar(id: String?, nome: String, marca: String, farmId: String?, raioM: String, latitude: String, longitude: String, onDone: (Boolean) -> Unit) {
        busy.value = true
        errorMessage.value = null
        viewModelScope.launch {
            val result = repo.salvar(id, nome, marca, farmId?.takeIf { it.isNotBlank() }, raioM.replace(',', '.').toDoubleOrNull(), latitude.replace(',', '.').toDoubleOrNull(), longitude.replace(',', '.').toDoubleOrNull())
            busy.value = false
            if (result?.ok == true) {
                pivos.value = repo.listar()?.pivos ?: pivos.value
                onDone(true)
            } else {
                errorMessage.value = result?.error ?: "Falha ao salvar pivô."
                onDone(false)
            }
        }
    }

    fun arquivar(id: String) {
        viewModelScope.launch {
            repo.arquivar(id)
            pivos.value = repo.listar()?.pivos ?: pivos.value
        }
    }

    /** Painel de "controle" -- SEM comando real pro equipamento (não há
     * parceria com nenhum fabricante ainda). Só registra o estado
     * desejado, igual a um lançamento manual. */
    fun registrarControle(id: String, status: String, lamina: String, sentido: String, onDone: (Boolean) -> Unit) {
        busy.value = true
        viewModelScope.launch {
            val result = repo.registrarControle(id, status, lamina.toDoubleOrNull(), sentido)
            busy.value = false
            if (result?.ok == true) {
                pivos.value = repo.listar()?.pivos ?: pivos.value
                onDone(true)
            } else {
                errorMessage.value = result?.error ?: "Falha ao registrar o comando."
                onDone(false)
            }
        }
    }

    /** Ligar com um toque (mesmo registro de estado desejado do painel Controle). */
    fun ligar(pivo: PivoData) {
        busy.value = true
        viewModelScope.launch {
            val result = repo.registrarControle(pivo.id, "RUNNING_WATER", null, null)
            busy.value = false
            if (result?.ok == true) pivos.value = repo.listar()?.pivos ?: pivos.value
            else errorMessage.value = result?.error ?: "Falha ao ligar o pivô."
        }
    }

    /** Desligar + lâmina aplicada (atalhos 5/10/15/20 mm) já lançada no balanço hídrico. */
    fun desligar(pivo: PivoData, laminaMm: Double, horas: Double?, onDone: (Boolean) -> Unit) {
        busy.value = true
        viewModelScope.launch {
            val result = repo.registrarControle(pivo.id, "STOPPED", null, null)
            if (result?.ok == true && laminaMm > 0) {
                val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
                fmt.timeZone = java.util.TimeZone.getTimeZone("UTC")
                repo.lancarTelemetria(pivo.id, laminaMm, 0.0, horas, fmt.format(java.util.Date()))
            }
            busy.value = false
            if (result?.ok == true) {
                pivos.value = repo.listar()?.pivos ?: pivos.value
                refreshBalanco(null)
                onDone(true)
            } else {
                errorMessage.value = result?.error ?: "Falha ao desligar o pivô."
                onDone(false)
            }
        }
    }

    fun lancarTelemetria(pivoId: String, waterAppliedMm: String, rainGaugeMm: String, dataBr: String, onDone: (Boolean) -> Unit) {
        val iso = brDateToIsoOrNull(dataBr)
        val lamina = waterAppliedMm.toDoubleOrNull()
        if (iso == null || lamina == null) {
            errorMessage.value = "Data ou lâmina inválida."
            onDone(false)
            return
        }
        busy.value = true
        viewModelScope.launch {
            val result = repo.lancarTelemetria(pivoId, lamina, rainGaugeMm.toDoubleOrNull() ?: 0.0, null, "${iso}T00:00:00.000Z")
            busy.value = false
            if (result?.ok == true) {
                pivos.value = repo.listar()?.pivos ?: pivos.value
                refreshBalanco(null)
                onDone(true)
            } else {
                errorMessage.value = result?.error ?: "Falha ao lançar telemetria."
                onDone(false)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PivosScreen(onBack: () -> Unit, viewModel: PivosViewModel = viewModel()) {
    LaunchedEffect(Unit) { viewModel.load() }
    val pivosTodos by viewModel.pivos
    val farmSel = com.bragro.mobile.ui.domain.FarmSelection.selected.value
    val farms by viewModel.farms
    // v14: só a fazenda escolhida no topo.
    val pivos = remember(pivosTodos, farms, farmSel) {
        if (farmSel == null) pivosTodos else pivosTodos.filter { x -> farms.firstOrNull { it.id == x.farmId }?.name?.let { com.bragro.mobile.ui.domain.normalizarTexto(it) } == com.bragro.mobile.ui.domain.normalizarTexto(farmSel) }
    }
    val balanco by viewModel.balanco
    val loading by viewModel.loading
    val busy by viewModel.busy
    val error by viewModel.errorMessage
    var showNovo by remember { mutableStateOf(false) }
    var balancoOpen by remember { mutableStateOf(false) }
    var graficosOpen by remember { mutableStateOf(false) }
    var pivosOpen by remember { mutableStateOf(false) }
    var prestadoraAberta by remember { mutableStateOf(false) }
    var filtroPivo by remember { mutableStateOf<String?>(null) }
    var filtroMenu by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<PivoData?>(null) }
    var telemetriaPivo by remember { mutableStateOf<PivoData?>(null) }
    var controlePivo by remember { mutableStateOf<PivoData?>(null) }
    var desligarPivo by remember { mutableStateOf<PivoData?>(null) }
    var historicoPivo by remember { mutableStateOf<PivoData?>(null) }
    val historico by viewModel.historico
    val historicoLoading by viewModel.historicoLoading

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        com.bragro.mobile.ui.domain.BackNavButton()
                    }
                },
                title = { androidx.compose.material3.ProvideTextStyle(androidx.compose.material3.LocalTextStyle.current.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)) {
                    // v21 -- ícone verde do módulo ao lado do título.
                    androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
                        com.bragro.mobile.ui.domain.IconeNegrito(androidx.compose.material.icons.Icons.Outlined.WaterDrop, contentDescription = null, tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurface, size = 22.dp, modifier = androidx.compose.ui.Modifier.padding(end = 8.dp, bottom = 3.dp))

                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Pivôs de Irrigação", color = MaterialTheme.colorScheme.onSurface)
                    }
                
                    }
                } },
                actions = { Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        com.bragro.mobile.ui.domain.HomeNavButton()
                    } },
            )
        },
        // Botão flutuante removido -- "+ Pivô" fica em Ações (padrão v7).
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "painel") {
                val ligados = pivos.count { it.status == "RUNNING_WATER" || it.status == "RUNNING_DRY" }
                val areaHa = pivos.sumOf { p -> p.raioM?.let { Math.PI * it * it / 10000 } ?: 0.0 }
                val lamina = balanco.sumOf { it.laminaMm }
                val chuva = balanco.sumOf { it.chuvaMm + it.chuvaClimaMm }
                val diasIrr = balanco.count { it.laminaMm > 0 }
                com.bragro.mobile.ui.domain.PainelMonitoramento(
                    com.bragro.mobile.ui.domain.PainelModulo(
                        titulo = "Pivôs · últimos 30 dias",
                        subtitulo = "${pivos.size} pivô(s) · ${pivos.mapNotNull { it.farmId }.toSet().size} fazenda(s)",
                        kpis = listOf(
                            com.bragro.mobile.ui.domain.PainelKpi("Pivôs", pivos.size.toDouble(), "numero", null, "$ligados ligado(s) agora", "Pivôs"),
                            com.bragro.mobile.ui.domain.PainelKpi("Área irrigada", Math.round(areaHa).toDouble(), "numero", "ha", "pelo raio", "Pivôs"),
                            com.bragro.mobile.ui.domain.PainelKpi("Lâmina", Math.round(lamina).toDouble(), "numero", "mm", "30 dias", "Balanço"),
                            com.bragro.mobile.ui.domain.PainelKpi("Chuva", Math.round(chuva).toDouble(), "numero", "mm", "pluviômetro + Clima", "Balanço"),
                        ),
                        barras = if (pivos.isEmpty()) emptyList() else listOf(
                            com.bragro.mobile.ui.domain.PainelBarra("Pivôs ligados", Math.round(ligados * 100.0 / pivos.size).toDouble(), "$ligados/${pivos.size}"),
                            com.bragro.mobile.ui.domain.PainelBarra("Dias c/ irrigação", if (balanco.isEmpty()) 0.0 else Math.round(diasIrr * 100.0 / balanco.size).toDouble(), "$diasIrr/${balanco.size}", neutra = true),
                        ),
                    ),
                    onKpi = { k -> if (k.destino == "Balanço") balancoOpen = true else pivosOpen = true },
                )
            }
            item(key = "acoes") {
                    // v14: abas Dados / Operações / Arquivos, iguais aos outros módulos.
                    com.bragro.mobile.ui.domain.AcoesEmAbas(
                        dados = {
                            Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
                                com.bragro.mobile.ui.domain.LabeledIconButton(
                                    icon = Icons.Outlined.FilterAlt,
                                    label = pivos.firstOrNull { it.id == filtroPivo }?.nome ?: "Pivô",
                                    active = filtroPivo != null,
                                    onClick = { filtroMenu = true },
                                )
                                androidx.compose.material3.DropdownMenu(expanded = filtroMenu, onDismissRequest = { filtroMenu = false }) {
                                    androidx.compose.material3.DropdownMenuItem(text = { Text("Todos os pivôs") }, onClick = { filtroPivo = null; filtroMenu = false; viewModel.refreshBalanco(null) })
                                    pivos.forEach { p ->
                                        androidx.compose.material3.DropdownMenuItem(text = { Text((if (p.id == filtroPivo) "✓ " else "") + p.nome) }, onClick = { filtroPivo = p.id; filtroMenu = false; balancoOpen = true; viewModel.refreshBalanco(p.id) })
                                    }
                                }
                            }
                            com.bragro.mobile.ui.domain.LabeledIconButton(icon = Icons.Outlined.Opacity, label = "Balanço", active = balancoOpen, onClick = { balancoOpen = !balancoOpen })
                            com.bragro.mobile.ui.domain.LabeledIconButton(icon = Icons.Outlined.BarChart, label = "Gráficos", onClick = { graficosOpen = true })
                        },
                        operacoes = {
                            com.bragro.mobile.ui.domain.LabeledIconButton(icon = Icons.Outlined.WaterDrop, label = "+ Pivô", onClick = { editing = null; showNovo = true })
                            com.bragro.mobile.ui.domain.LabeledIconButton(icon = Icons.Outlined.Refresh, label = "Atualizar", loading = loading, onClick = { viewModel.load() })
                            com.bragro.mobile.ui.domain.LabeledIconButton(icon = Icons.Outlined.OfflineBolt, label = "Fabricante", onClick = { prestadoraAberta = true })
                            com.bragro.mobile.ui.domain.NuvemButton()
                        },
                        arquivos = {
                            com.bragro.mobile.ui.domain.ArquivosTabela("Pivôs de Irrigação", listOf("Pivô", "Fazenda", "Marca", "Status", "Ângulo", "Lâmina (mm)", "Pressão (psi)", "Raio (m)")) {
                                pivos.map { p ->
                                    listOf(p.nome, farms.firstOrNull { it.id == p.farmId }?.name.orEmpty(), p.marca, statusLabel(p.status), p.anguloAtual?.toInt()?.toString().orEmpty(), p.laminaAtualMm?.toString().orEmpty(), p.pressaoPsi?.toInt()?.toString().orEmpty(), p.raioM?.toInt()?.toString().orEmpty())
                                }
                            }
                        },
                    )
            }
            // Balanço Hídrico -- lâmina aplicada + chuva (pluviômetro) por dia.
            item(key = "balanco-header") {
                com.bragro.mobile.ui.domain.CabecalhoLancamentos(total = 0, aberto = balancoOpen, titulo = "Balanço hídrico", rotulo = "30 dias") { balancoOpen = !balancoOpen }
            }
            if (balancoOpen) item(key = "balanco") {
                com.bragro.mobile.ui.domain.BlocoGraficoBarras(
                    titulo = "Balanço hídrico (30 dias)",
                    categorias = balanco.map { com.bragro.mobile.ui.domain.diaMesBr(it.data) },
                    series = listOf("Lâmina aplicada" to balanco.map { it.laminaMm }, "Chuva (pluviômetro)" to balanco.map { it.chuvaMm }, "Chuva (Clima, automática)" to balanco.map { it.chuvaClimaMm }),
                    isMoney = false,
                ) {
                    Column {
                        if (!balancoOpen) {
                        } else if (balanco.isEmpty()) {
                            Text("Sem lançamentos de telemetria no período.", style = MaterialTheme.typography.bodySmall)
                        } else {
                            SimpleBarChart(
                                categories = balanco.map { com.bragro.mobile.ui.domain.diaMesBr(it.data) },
                                series = listOf(
                                    BarSeries("Lâmina aplicada", balanco.map { it.laminaMm }, MaterialTheme.colorScheme.primary),
                                    BarSeries("Chuva (pluviômetro)", balanco.map { it.chuvaMm }, MaterialTheme.colorScheme.secondary),
                                    BarSeries("Chuva (Clima, automática)", balanco.map { it.chuvaClimaMm }, MaterialTheme.colorScheme.tertiary),
                                ),
                                isMoney = false,
                            )
                        }
                    }
                }
            }
            item(key = "pivos_cab") {
                com.bragro.mobile.ui.domain.CabecalhoLancamentos(total = pivos.size, aberto = pivosOpen, titulo = "Pivôs") { pivosOpen = !pivosOpen }
            }
            when {
                !pivosOpen -> {}
                loading -> item(key = "loading") {
                    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(modifier = Modifier.padding(top = 24.dp))
                    }
                }
                pivos.isEmpty() -> {
                    item(key = "vazio") {
                        Text("Nenhum pivô cadastrado ainda. Toque no botão para adicionar.", modifier = Modifier.padding(vertical = 12.dp))
                    }
                    item(key = "exemplo") {
                        // Card de exemplo (fictício, sem ação) -- mostra o layout antes do primeiro cadastro.
                        Column(Modifier.alpha(0.6f)) {
                            Text("EXEMPLO", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            PivoCard(
                                pivo = PivoData(
                                    id = "exemplo", nome = "Pivô 01 — Exemplo", marca = MARCAS[0], status = "RUNNING_WATER",
                                    anguloAtual = 142.0, laminaAtualMm = 8.0, pressaoPsi = 38.0,
                                ),
                                farmNome = "Fazenda exemplo",
                                onEdit = {}, onTelemetria = {}, onHistorico = {}, onControle = {}, onArchive = {},
                                onLigar = {}, onDesligar = {}, busy = true,
                            )
                        }
                    }
                }
                else -> items(pivos, key = { it.id }) { pivo ->
                    PivoCard(
                        pivo = pivo,
                        farmNome = farms.firstOrNull { it.id == pivo.farmId }?.name,
                        onEdit = { editing = pivo; showNovo = true },
                        onTelemetria = { telemetriaPivo = pivo },
                        onHistorico = { historicoPivo = pivo; viewModel.carregarHistorico(pivo.id) },
                        onControle = { controlePivo = pivo },
                        onArchive = { viewModel.arquivar(pivo.id) },
                        onLigar = { viewModel.ligar(pivo) },
                        onDesligar = { desligarPivo = pivo },
                        busy = busy,
                    )
                }
            }
        }
    }

    if (graficosOpen) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { graficosOpen = false },
            modifier = Modifier.fillMaxWidth(0.96f),
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            title = { Text("Gráficos") },
            text = { Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) { ModuleChartsCard(domainId = "pivos", showHeader = false) } },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { graficosOpen = false }) { Text("Fechar") } },
        )
    }
    if (prestadoraAberta) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { prestadoraAberta = false },
            modifier = Modifier.fillMaxWidth(0.96f),
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            title = { Text("Sincronização com o fabricante") },
            text = { Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) { ModuleProviderIntegrationCard(domainId = "pivos") } },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { prestadoraAberta = false }) { Text("Fechar") } },
        )
    }

    if (showNovo) {
        NovoPivoDialog(
            farms = farms,
            initial = editing,
            busy = busy,
            error = error,
            onDismiss = { showNovo = false },
            onSubmit = { nome, marca, farmId, raioM, lat, lon ->
                viewModel.salvar(editing?.id, nome, marca, farmId, raioM, lat, lon) { ok -> if (ok) showNovo = false }
            },
        )
    }

    telemetriaPivo?.let { pivo ->
        TelemetriaDialog(
            pivoNome = pivo.nome,
            busy = busy,
            error = error,
            onDismiss = { telemetriaPivo = null },
            onSubmit = { water, rain, dataBr ->
                viewModel.lancarTelemetria(pivo.id, water, rain, dataBr) { ok -> if (ok) telemetriaPivo = null }
            },
        )
    }

    desligarPivo?.let { pivo ->
        DesligarDialog(
            pivoNome = pivo.nome,
            busy = busy,
            onDismiss = { desligarPivo = null },
            onSubmit = { lamina, horas ->
                viewModel.desligar(pivo, lamina, horas) { ok -> if (ok) desligarPivo = null }
            },
        )
    }

    historicoPivo?.let { pivo ->
        HistoricoTelemetriaDialog(pivo.nome, historico, historicoLoading) { historicoPivo = null }
    }

    controlePivo?.let { pivo ->
        ControleDialog(
            pivo = pivo,
            busy = busy,
            error = error,
            onDismiss = { controlePivo = null },
            onSubmit = { status, lamina, sentido ->
                viewModel.registrarControle(pivo.id, status, lamina, sentido) { ok -> if (ok) controlePivo = null }
            },
        )
    }
}

@Composable
private fun HistoricoTelemetriaDialog(pivoNome: String, logs: List<PivoTelemetriaLogData>, loading: Boolean, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Histórico -- $pivoNome") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                when {
                    loading -> CircularProgressIndicator(modifier = Modifier.padding(12.dp))
                    logs.isEmpty() -> Text("Nenhuma telemetria nos últimos 60 dias.", style = MaterialTheme.typography.bodySmall)
                    else -> logs.forEach { l ->
                        Text(
                            "${l.recordedAt.take(10)} · lâmina ${l.waterAppliedMm} mm · chuva ${l.rainGaugeMm} mm" +
                                (l.operatingHours?.let { " · $it h" } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } },
    )
}

@Composable
private fun PivoCard(
    pivo: PivoData,
    farmNome: String?,
    onEdit: () -> Unit,
    onTelemetria: () -> Unit,
    onHistorico: () -> Unit,
    onControle: () -> Unit,
    onArchive: () -> Unit,
    onLigar: () -> Unit,
    onDesligar: () -> Unit,
    busy: Boolean,
) {
    // Mesmo cartão da plataforma (pivos-client.tsx): fazenda · marca, nome,
    // selo de status, anel com o ângulo, Lâmina / Pressão / Sentido e a
    // barra Ligar/Desligar · Telemetria · Controle · Editar · Arquivar.
    val cs = MaterialTheme.colorScheme
    val ligado = pivo.status == "RUNNING_WATER" || pivo.status == "RUNNING_DRY"
    val corStatus = if (ligado) com.bragro.mobile.ui.domain.corSucesso() else if (pivo.status == "STOPPED") cs.onSurfaceVariant else com.bragro.mobile.ui.domain.corAtencao()
    val ang = (pivo.anguloAtual ?: 0.0).coerceIn(0.0, 360.0).toFloat()
    val verde = com.bragro.mobile.ui.domain.corSucesso()
    val trilho = cs.onSurface.copy(alpha = 0.10f)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text("${farmNome ?: "Sem fazenda"} · ${pivo.marca}".uppercase(), fontSize = 10.sp, letterSpacing = 0.6.sp, color = cs.onSurfaceVariant)
                        Text(pivo.nome, fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    }
                    Text(
                        statusLabel(pivo.status).uppercase(), fontSize = 10.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold, color = corStatus,
                        modifier = Modifier.background(corStatus.copy(alpha = 0.15f), androidx.compose.foundation.shape.RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(60.dp).clickable { onHistorico() }, contentAlignment = Alignment.Center) {
                        androidx.compose.foundation.Canvas(Modifier.size(60.dp)) {
                            val st = androidx.compose.ui.graphics.drawscope.Stroke(width = 7.dp.toPx())
                            drawArc(trilho, 0f, 360f, false, style = st)
                            drawArc(verde, -90f, ang, false, style = st)
                        }
                        Text(pivo.anguloAtual?.let { "${it.toInt()}°" } ?: "—", fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    }
                    Row(Modifier.weight(1f)) {
                        listOf(
                            "Lâmina" to (pivo.laminaAtualMm?.let { "%.1f mm".format(Locale("pt", "BR"), it) } ?: "—"),
                            "Pressão" to (pivo.pressaoPsi?.let { "${it.toInt()} psi" } ?: "—"),
                            "Sentido" to (SENTIDO_OPTIONS.firstOrNull { it.first == pivo.sentido }?.second ?: "—"),
                        ).forEach { (l, v) ->
                            Column(Modifier.weight(1f)) {
                                Text(l, fontSize = 10.5.sp, color = cs.onSurfaceVariant)
                                Text(v, fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                            }
                        }
                    }
                }
                Text(
                    (pivo.raioM?.let { "Raio ${it.toInt()} m · ${"%.1f".format(Locale("pt", "BR"), Math.PI * it * it / 10000)} ha · " } ?: "") +
                        (pivo.ultimaSincronizacaoEm?.let { "Atualizado em ${dataHoraBr(it)}" } ?: "Lançado manualmente") + " · Toque no anel para ver o histórico",
                    fontSize = 10.5.sp, color = cs.onSurfaceVariant,
                )
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(cs.outlineVariant))
            Row(Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                @Composable
                fun botao(icone: androidx.compose.ui.graphics.vector.ImageVector, rotulo: String, fundo: androidx.compose.ui.graphics.Color, cor: androidx.compose.ui.graphics.Color, acao: () -> Unit, mod: Modifier) {
                    Column(
                        mod.clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)).background(fundo).clickable(enabled = !busy) { acao() }.padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(icone, contentDescription = null, tint = cor, modifier = Modifier.size(17.dp))
                        Text(rotulo, fontSize = 10.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = cor, maxLines = 1)
                    }
                }
                if (ligado) botao(Icons.Outlined.StopCircle, "Desligar", com.bragro.mobile.ui.domain.corAtencao().copy(alpha = 0.15f), com.bragro.mobile.ui.domain.corAtencao(), onDesligar, Modifier.weight(1f))
                else botao(Icons.Outlined.PlayCircle, "Ligar", cs.primary, cs.onPrimary, onLigar, Modifier.weight(1f))
                botao(Icons.Outlined.Opacity, "Telemetria", androidx.compose.ui.graphics.Color.Transparent, cs.onSurface, onTelemetria, Modifier.weight(1f))
                botao(Icons.Outlined.Speed, "Controle", androidx.compose.ui.graphics.Color.Transparent, cs.onSurface, onControle, Modifier.weight(1f))
                botao(Icons.Outlined.Edit, "Editar", androidx.compose.ui.graphics.Color.Transparent, cs.onSurface, onEdit, Modifier.weight(1f))
                botao(Icons.Outlined.Archive, "Arquivar", androidx.compose.ui.graphics.Color.Transparent, com.bragro.mobile.ui.domain.corAtencao(), onArchive, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DesligarDialog(
    pivoNome: String,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (laminaMm: Double, horas: Double?) -> Unit,
) {
    var lamina by remember { mutableStateOf("") }
    var horas by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Desligar -- $pivoNome") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Lâmina aplicada nesta irrigação (mm)", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("5", "10", "15", "20").forEach { v ->
                        OutlinedButton(onClick = { lamina = v }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)) {
                            Text("$v mm", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                OutlinedTextField(
                    value = lamina, onValueChange = { lamina = it }, label = { Text("Outro valor (mm)") },
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    singleLine = true, modifier = Modifier.fillMaxWidth(), colors = appFieldColors(),
                )
                OutlinedTextField(
                    value = horas, onValueChange = { horas = it }, label = { Text("Horas de operação (opcional)") },
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    singleLine = true, modifier = Modifier.fillMaxWidth(), colors = appFieldColors(),
                )
            }
        },
        confirmButton = {
            Button(
                enabled = !busy,
                onClick = { onSubmit(lamina.replace(',', '.').toDoubleOrNull() ?: 0.0, horas.replace(',', '.').toDoubleOrNull()) },
            ) { if (busy) CircularProgressIndicator(modifier = Modifier.height(18.dp)) else Text("Desligar") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancelar") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NovoPivoDialog(
    farms: List<FarmLookupData>,
    initial: PivoData?,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (nome: String, marca: String, farmId: String?, raioM: String, latitude: String, longitude: String) -> Unit,
) {
    var nome by remember { mutableStateOf(initial?.nome ?: "") }
    var marca by remember { mutableStateOf(initial?.marca ?: MARCAS[0]) }
    var farmId by remember { mutableStateOf(initial?.farmId ?: "") }
    var raioM by remember { mutableStateOf(initial?.raioM?.toString() ?: "") }
    var latitude by remember { mutableStateOf(initial?.latitude?.toString() ?: "") }
    var longitude by remember { mutableStateOf(initial?.longitude?.toString() ?: "") }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (initial != null) "Editar pivô" else "Novo pivô") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = nome, onValueChange = { nome = it }, label = { Text("Nome *") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = appFieldColors())
                SearchableDropdownField(value = marca, label = "Marca / fabricante", options = MARCAS.map { it to it }, onSelect = { marca = it }, emptyOptionLabel = null)
                SearchableDropdownField(
                    value = farmId, label = "Fazenda",
                    options = farms.map { it.id to it.name },
                    onSelect = { farmId = it },
                    emptyOptionLabel = "Sem fazenda",
                )
                OutlinedTextField(
                    value = raioM, onValueChange = { raioM = it }, label = { Text("Raio irrigado (m)") },
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    singleLine = true, modifier = Modifier.fillMaxWidth(), colors = appFieldColors(),
                )
                OutlinedTextField(
                    value = latitude, onValueChange = { latitude = it }, label = { Text("Latitude") },
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    singleLine = true, modifier = Modifier.fillMaxWidth(), colors = appFieldColors(),
                )
                OutlinedTextField(
                    value = longitude, onValueChange = { longitude = it }, label = { Text("Longitude") },
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    singleLine = true, modifier = Modifier.fillMaxWidth(), colors = appFieldColors(),
                )
                if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(enabled = !busy && nome.isNotBlank(), onClick = { onSubmit(nome, marca, farmId.takeIf { it.isNotBlank() }, raioM, latitude, longitude) }) {
                if (busy) CircularProgressIndicator(modifier = Modifier.height(18.dp)) else Text("Salvar")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancelar") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TelemetriaDialog(
    pivoNome: String,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (waterAppliedMm: String, rainGaugeMm: String, dataBr: String) -> Unit,
) {
    var water by remember { mutableStateOf("") }
    var rain by remember { mutableStateOf("0") }
    var dataBr by remember { mutableStateOf(todayBr()) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Telemetria -- $pivoNome") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = water, onValueChange = { water = it }, label = { Text("Lâmina aplicada (mm) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    singleLine = true, modifier = Modifier.fillMaxWidth(), colors = appFieldColors(),
                )
                OutlinedTextField(
                    value = rain, onValueChange = { rain = it }, label = { Text("Chuva no pluviômetro (mm)") },
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    singleLine = true, modifier = Modifier.fillMaxWidth(), colors = appFieldColors(),
                )
                OutlinedTextField(
                    value = dataBr, onValueChange = { dataBr = it }, label = { Text("Data *") }, placeholder = { Text("DD/MM/AAAA") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(), colors = appFieldColors(),
                )
                if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(enabled = !busy && water.isNotBlank(), onClick = { onSubmit(water, rain, dataBr) }) {
                if (busy) CircularProgressIndicator(modifier = Modifier.height(18.dp)) else Text("Lançar")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancelar") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ControleDialog(
    pivo: PivoData,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (status: String, laminaAtualMm: String, sentido: String) -> Unit,
) {
    var status by remember { mutableStateOf(pivo.status ?: "STOPPED") }
    var lamina by remember { mutableStateOf(pivo.laminaAtualMm?.toString() ?: "") }
    var sentido by remember { mutableStateOf(pivo.sentido ?: SENTIDO_OPTIONS[0].first) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Controle -- ${pivo.nome}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Sem comando real pro equipamento -- não há parceria com nenhum fabricante ainda. O que você escolher aqui fica registrado como o estado desejado do pivô.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SearchableDropdownField(value = status, label = "Status (ligar/desligar)", options = STATUS_OPTIONS, onSelect = { status = it }, emptyOptionLabel = null)
                OutlinedTextField(
                    value = lamina, onValueChange = { lamina = it }, label = { Text("Lâmina desejada (mm)") },
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    singleLine = true, modifier = Modifier.fillMaxWidth(), colors = appFieldColors(),
                )
                SearchableDropdownField(value = sentido, label = "Sentido de rotação", options = SENTIDO_OPTIONS, onSelect = { sentido = it }, emptyOptionLabel = null)
                if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(enabled = !busy, onClick = { onSubmit(status, lamina, sentido) }) {
                if (busy) CircularProgressIndicator(modifier = Modifier.height(18.dp)) else Text("Registrar")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancelar") } },
    )
}

@Composable
private fun SecaoCabecalho(titulo: String, aberto: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onToggle() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(titulo, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        com.bragro.mobile.ui.domain.CantoL(aberto)
    }
}

/** ISO (2026-10-16T18:42:00Z) -> 16/10/2026 15:42 (horário de Brasília) -- v17. */
private fun dataHoraBr(iso: String): String = runCatching {
    val inst = java.time.Instant.parse(iso)
    java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(java.time.ZoneId.of("America/Sao_Paulo")).format(inst)
}.getOrElse {
    val d = iso.take(10).split("-")
    if (d.size == 3) "${d[2]}/${d[1]}/${d[0]}" + (iso.drop(11).take(5).takeIf { it.length == 5 }?.let { " $it" } ?: "") else iso
}
