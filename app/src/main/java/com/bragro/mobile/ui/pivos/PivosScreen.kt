package com.bragro.mobile.ui.pivos

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.WaterDrop
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
    val pivos by viewModel.pivos
    val farms by viewModel.farms
    val balanco by viewModel.balanco
    val loading by viewModel.loading
    val busy by viewModel.busy
    val error by viewModel.errorMessage
    var showNovo by remember { mutableStateOf(false) }
    var balancoOpen by remember { mutableStateOf(true) }
    var graficosOpen by remember { mutableStateOf(true) }
    var pivosOpen by remember { mutableStateOf(true) }
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
                title = {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Pivôs de Irrigação", color = MaterialTheme.colorScheme.primary)
                    }
                },
                navigationIcon = {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        com.bragro.mobile.ui.domain.HomeNavButton()
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { editing = null; showNovo = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Filled.WaterDrop, contentDescription = "Novo pivô")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
        ) {
            item(key = "intro") {
                Text(
                    "Cadastro, telemetria (lâmina + pluviômetro) e controle de pivôs lançados manualmente. Sincronização automática com o fabricante depende de parceria -- ver card abaixo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
            // Balanço Hídrico -- lâmina aplicada + chuva (pluviômetro) por dia.
            item(key = "balanco") {
                Card(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        SecaoCabecalho("Balanço Hídrico (30 dias)", balancoOpen) { balancoOpen = !balancoOpen }
                        if (balancoOpen) Spacer(Modifier.height(8.dp))
                        if (!balancoOpen) {
                        } else if (balanco.isEmpty()) {
                            Text("Sem lançamentos de telemetria no período.", style = MaterialTheme.typography.bodySmall)
                        } else {
                            SimpleBarChart(
                                categories = balanco.map { it.data.takeLast(5) },
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
            // Painel de gráficos do módulo (mesmo ModuleChartsPanel do site, aberto por padrão).
            item(key = "graficos") {
                Card(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        SecaoCabecalho("Gráficos", graficosOpen) { graficosOpen = !graficosOpen }
                        if (graficosOpen) {
                            Spacer(Modifier.height(8.dp))
                            ModuleChartsCard(domainId = "pivos", showHeader = false)
                        }
                    }
                }
            }
            item(key = "pivos_cab") {
                Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        SecaoCabecalho("Pivôs cadastrados (${pivos.size})", pivosOpen) { pivosOpen = !pivosOpen }
                    }
                }
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
            item(key = "integracao") {
                Spacer(Modifier.height(8.dp))
                ModuleProviderIntegrationCard(domainId = "pivos")
            }
        }
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
    val ligado = pivo.status == "RUNNING_WATER" || pivo.status == "RUNNING_DRY"
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.WaterDrop, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 8.dp))
                Text(pivo.nome, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Badge { Text(statusLabel(pivo.status)) }
            }
            Text(farmNome ?: "Sem fazenda vinculada", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(pivo.marca, style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.RotateRight, contentDescription = null, modifier = Modifier.height(14.dp))
                    Text(" ${pivo.anguloAtual?.let { "${it.toInt()}°" } ?: "—"}", style = MaterialTheme.typography.labelSmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Opacity, contentDescription = null, modifier = Modifier.height(14.dp))
                    Text(" ${pivo.laminaAtualMm?.let { "${it} mm" } ?: "—"}", style = MaterialTheme.typography.labelSmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Speed, contentDescription = null, modifier = Modifier.height(14.dp))
                    Text(" ${pivo.pressaoPsi?.let { "${it.toInt()} psi" } ?: "—"}", style = MaterialTheme.typography.labelSmall)
                }
            }
            Text(
                pivo.ultimaSincronizacaoEm?.let { "Última sincronização: $it" } ?: "Nunca sincronizado automaticamente -- dados lançados manualmente.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Button(
                onClick = if (ligado) onDesligar else onLigar,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) { Text(if (ligado) "Desligar agora" else "Ligar agora") }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                val pad = androidx.compose.foundation.layout.PaddingValues(horizontal = 2.dp, vertical = 8.dp)
                OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f), contentPadding = pad) { Text("Editar", maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelSmall) }
                OutlinedButton(onClick = onTelemetria, modifier = Modifier.weight(1.3f), contentPadding = pad) { Text("Telemetria", maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelSmall) }
                OutlinedButton(onClick = onControle, modifier = Modifier.weight(1.1f), contentPadding = pad) { Text("Controle", maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelSmall) }
                OutlinedButton(onClick = onHistorico, modifier = Modifier.weight(1.2f), contentPadding = pad) { Text("Histórico", maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelSmall) }
                IconButton(onClick = onArchive, modifier = Modifier.size(36.dp)) { Icon(Icons.Filled.Power, contentDescription = "Arquivar") }
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
        Icon(
            if (aberto) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = if (aberto) "Recolher" else "Expandir",
        )
    }
}
