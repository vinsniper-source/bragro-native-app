package com.bragro.mobile.ui.pivos

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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

    fun refreshBalanco(pivoId: String?) {
        viewModelScope.launch { balanco.value = repo.balancoHidrico(pivoId)?.balanco ?: emptyList() }
    }

    fun salvar(id: String?, nome: String, marca: String, farmId: String?, raioM: String, onDone: (Boolean) -> Unit) {
        busy.value = true
        errorMessage.value = null
        viewModelScope.launch {
            val result = repo.salvar(id, nome, marca, farmId?.takeIf { it.isNotBlank() }, raioM.toDoubleOrNull(), null, null)
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
    var editing by remember { mutableStateOf<PivoData?>(null) }
    var telemetriaPivo by remember { mutableStateOf<PivoData?>(null) }
    var controlePivo by remember { mutableStateOf<PivoData?>(null) }

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
                        IconButton(onClick = onBack) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { editing = null; showNovo = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(bottom = 48.dp),
            ) {
                Icon(Icons.Filled.WaterDrop, contentDescription = "Novo pivô")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text(
                "Cadastro, telemetria (lâmina + pluviômetro) e controle de pivôs lançados manualmente. Sincronização automática com o fabricante depende de parceria -- ver card abaixo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            ModuleProviderIntegrationCard(domainId = "pivos")
            Spacer(Modifier.height(8.dp))
            // Balanço Hídrico -- lâmina aplicada + chuva (pluviômetro) por dia.
            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Balanço Hídrico (30 dias)", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    if (balanco.isEmpty()) {
                        Text("Sem lançamentos de telemetria no período.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        SimpleBarChart(
                            categories = balanco.map { it.data.takeLast(5) },
                            series = listOf(
                                BarSeries("Lâmina aplicada", balanco.map { it.laminaMm }, MaterialTheme.colorScheme.primary),
                                BarSeries("Chuva", balanco.map { it.chuvaMm }, MaterialTheme.colorScheme.secondary),
                            ),
                            isMoney = false,
                        )
                    }
                }
            }
            when {
                loading -> Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(modifier = Modifier.padding(top = 24.dp))
                }
                pivos.isEmpty() -> Text("Nenhum pivô cadastrado ainda. Toque no botão para adicionar.", modifier = Modifier.padding(vertical = 24.dp))
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 80.dp)) {
                    items(pivos, key = { it.id }) { pivo ->
                        PivoCard(
                            pivo = pivo,
                            farmNome = farms.firstOrNull { it.id == pivo.farmId }?.name,
                            onEdit = { editing = pivo; showNovo = true },
                            onTelemetria = { telemetriaPivo = pivo },
                            onControle = { controlePivo = pivo },
                            onArchive = { viewModel.arquivar(pivo.id) },
                        )
                    }
                }
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
            onSubmit = { nome, marca, farmId, raioM ->
                viewModel.salvar(editing?.id, nome, marca, farmId, raioM) { ok -> if (ok) showNovo = false }
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
private fun PivoCard(
    pivo: PivoData,
    farmNome: String?,
    onEdit: () -> Unit,
    onTelemetria: () -> Unit,
    onControle: () -> Unit,
    onArchive: () -> Unit,
) {
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
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onEdit) { Text("Editar") }
                OutlinedButton(onClick = onTelemetria) { Text("Telemetria") }
                OutlinedButton(onClick = onControle) { Text("Controle") }
                IconButton(onClick = onArchive) { Icon(Icons.Filled.Power, contentDescription = "Arquivar") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NovoPivoDialog(
    farms: List<FarmLookupData>,
    initial: PivoData?,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (nome: String, marca: String, farmId: String?, raioM: String) -> Unit,
) {
    var nome by remember { mutableStateOf(initial?.nome ?: "") }
    var marca by remember { mutableStateOf(initial?.marca ?: MARCAS[0]) }
    var farmId by remember { mutableStateOf(initial?.farmId ?: "") }
    var raioM by remember { mutableStateOf(initial?.raioM?.toString() ?: "") }

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
                if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(enabled = !busy && nome.isNotBlank(), onClick = { onSubmit(nome, marca, farmId.takeIf { it.isNotBlank() }, raioM) }) {
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
