package com.bragro.mobile.ui.silos

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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bragro.mobile.data.model.FarmLookupData
import com.bragro.mobile.data.model.SiloData
import com.bragro.mobile.data.model.SiloEstoquePontoData
import com.bragro.mobile.data.repo.SilosRepository
import com.bragro.mobile.ui.domain.BarSeries
import com.bragro.mobile.ui.domain.ModuleProviderIntegrationCard
import com.bragro.mobile.ui.domain.SimpleBarChart
import com.bragro.mobile.ui.theme.Card
import com.bragro.mobile.ui.theme.SearchableDropdownField
import com.bragro.mobile.ui.theme.appFieldColors
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

// Silos cilíndricos (armazenagem de grãos) -- 15a exceção de schema.
// Cadastro de silos + leituras (nível/temperatura/umidade) lançadas
// manualmente; a credencial do provedor de sensores fica no card de
// integração (domainId "estoque", módulo SILO_ARMAZENAGEM). Só a "API
// genérica" tem teste real de conexão; demais provedores ficam Pendente.

private val PRODUTOS = listOf("Soja", "Milho", "Sorgo", "Trigo", "Feijão", "Outro")
private val FABRICANTES = listOf("Kepler Weber", "Silos Chapecó", "Pedrotti", "Fockink", "Cimbria", "Outro")

private fun statusLabel(status: String?): String = when (status) {
    "CRITICO" -> "Crítico"
    "ALERTA" -> "Atenção"
    "VAZIO" -> "Vazio"
    "OK" -> "Normal"
    else -> "Sem dados"
}

private fun todayBr(): String = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).format(java.util.Date())
private fun brDateToIsoOrNull(br: String): String? {
    val m = Regex("^(\\d{2})/(\\d{2})/(\\d{4})$").find(br.trim()) ?: return null
    val (d, mo, y) = m.destructured
    return "$y-$mo-$d"
}

class SilosViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = SilosRepository(app)

    var silos = mutableStateOf<List<SiloData>>(emptyList())
        private set
    var farms = mutableStateOf<List<FarmLookupData>>(emptyList())
        private set
    var estoque = mutableStateOf<List<SiloEstoquePontoData>>(emptyList())
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
            val r = repo.listar()
            silos.value = r.silos ?: emptyList()
            errorMessage.value = if (r.ok) null else r.error
            farms.value = repo.listarFazendas().farms ?: emptyList()
            estoque.value = repo.estoque().estoque ?: emptyList()
            loading.value = false
        }
    }

    private suspend fun recarregar() {
        silos.value = repo.listar().silos ?: silos.value
        estoque.value = repo.estoque().estoque ?: estoque.value
    }

    fun salvar(
        id: String?, nome: String, produto: String, fabricante: String, farmId: String,
        capacidade: String, diametro: String, altura: String, externalId: String,
        onDone: (Boolean) -> Unit,
    ) {
        busy.value = true
        errorMessage.value = null
        viewModelScope.launch {
            val r = repo.salvar(
                id, nome, produto.takeIf { it.isNotBlank() }, fabricante.takeIf { it.isNotBlank() },
                farmId.takeIf { it.isNotBlank() }, capacidade.replace(',', '.').toDoubleOrNull(),
                diametro.replace(',', '.').toDoubleOrNull(), altura.replace(',', '.').toDoubleOrNull(),
                externalId.takeIf { it.isNotBlank() },
            )
            busy.value = false
            if (r.ok) {
                recarregar()
                onDone(true)
            } else {
                errorMessage.value = r.error ?: "Falha ao salvar silo."
                onDone(false)
            }
        }
    }

    fun arquivar(id: String) {
        viewModelScope.launch {
            repo.arquivar(id)
            recarregar()
        }
    }

    fun lancarLeitura(siloId: String, nivel: String, temp: String, umid: String, dataBr: String, onDone: (Boolean) -> Unit) {
        val iso = brDateToIsoOrNull(dataBr)
        val n = nivel.replace(',', '.').toDoubleOrNull()
        if (iso == null || n == null || n < 0 || n > 100) {
            errorMessage.value = "Data ou nível inválido (0 a 100%)."
            onDone(false)
            return
        }
        busy.value = true
        errorMessage.value = null
        viewModelScope.launch {
            val r = repo.lancarLeitura(
                siloId, n, temp.replace(',', '.').toDoubleOrNull(), umid.replace(',', '.').toDoubleOrNull(),
                "${iso}T00:00:00.000Z",
            )
            busy.value = false
            if (r.ok) {
                recarregar()
                onDone(true)
            } else {
                errorMessage.value = r.error ?: "Falha ao lançar leitura."
                onDone(false)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SilosScreen(onBack: () -> Unit, viewModel: SilosViewModel = viewModel()) {
    LaunchedEffect(Unit) { viewModel.load() }
    val silos by viewModel.silos
    val farms by viewModel.farms
    val estoque by viewModel.estoque
    val loading by viewModel.loading
    val busy by viewModel.busy
    val error by viewModel.errorMessage
    var showForm by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<SiloData?>(null) }
    var leituraSilo by remember { mutableStateOf<SiloData?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Silos cilíndricos", color = MaterialTheme.colorScheme.primary)
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
                onClick = { editing = null; showForm = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(bottom = 48.dp),
            ) {
                Icon(Icons.Filled.Storage, contentDescription = "Novo silo")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text(
                "Cadastro de silos e leituras de nível, temperatura e umidade lançadas manualmente. Leitura automática depende de parceria com o provedor -- ver card abaixo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            ModuleProviderIntegrationCard(domainId = "estoque")
            Spacer(Modifier.height(8.dp))
            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Estoque armazenado (t, 30 dias)", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    if (estoque.isEmpty()) {
                        Text("Sem leituras com capacidade cadastrada no período.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        SimpleBarChart(
                            categories = estoque.map { it.data.takeLast(5) },
                            series = listOf(BarSeries("Armazenado (t)", estoque.map { it.quantidadeT }, MaterialTheme.colorScheme.primary)),
                            isMoney = false,
                        )
                    }
                }
            }
            if (error != null && !showForm && leituraSilo == null) {
                Text(error ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            when {
                loading -> Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(modifier = Modifier.padding(top = 24.dp))
                }
                silos.isEmpty() -> Text("Nenhum silo cadastrado ainda. Toque no botão para adicionar.", modifier = Modifier.padding(vertical = 24.dp))
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 80.dp)) {
                    items(silos, key = { it.id }) { silo ->
                        SiloCard(
                            silo = silo,
                            farmNome = farms.firstOrNull { it.id == silo.farmId }?.name,
                            onEdit = { editing = silo; showForm = true },
                            onLeitura = { leituraSilo = silo },
                            onArchive = { viewModel.arquivar(silo.id) },
                        )
                    }
                }
            }
        }
    }

    if (showForm) {
        SiloFormDialog(
            farms = farms, initial = editing, busy = busy, error = error,
            onDismiss = { showForm = false },
            onSubmit = { nome, produto, fab, farmId, cap, diam, alt, ext ->
                viewModel.salvar(editing?.id, nome, produto, fab, farmId, cap, diam, alt, ext) { ok -> if (ok) showForm = false }
            },
        )
    }

    leituraSilo?.let { silo ->
        LeituraDialog(
            siloNome = silo.nome, busy = busy, error = error,
            onDismiss = { leituraSilo = null },
            onSubmit = { nivel, temp, umid, dataBr ->
                viewModel.lancarLeitura(silo.id, nivel, temp, umid, dataBr) { ok -> if (ok) leituraSilo = null }
            },
        )
    }
}

@Composable
private fun SiloCard(
    silo: SiloData,
    farmNome: String?,
    onEdit: () -> Unit,
    onLeitura: () -> Unit,
    onArchive: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 8.dp))
                Text(silo.nome, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Badge { Text(statusLabel(silo.status)) }
            }
            Text(farmNome ?: "Sem fazenda vinculada", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                listOfNotNull(silo.produto, silo.fabricante, silo.capacidadeT?.let { "${it.toInt()} t" }).joinToString(" · ").ifBlank { "—" },
                style = MaterialTheme.typography.bodySmall,
            )
            LinearProgressIndicator(
                progress = { ((silo.nivelAtualPct ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            )
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Speed, contentDescription = null, modifier = Modifier.height(14.dp))
                    Text(" ${silo.nivelAtualPct?.let { "${it.toInt()}%" } ?: "—"}", style = MaterialTheme.typography.labelSmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Thermostat, contentDescription = null, modifier = Modifier.height(14.dp))
                    Text(" ${silo.temperaturaAtualC?.let { "$it °C" } ?: "—"}", style = MaterialTheme.typography.labelSmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Opacity, contentDescription = null, modifier = Modifier.height(14.dp))
                    Text(" ${silo.umidadeAtualPct?.let { "$it %" } ?: "—"}", style = MaterialTheme.typography.labelSmall)
                }
            }
            Text(
                silo.ultimaLeituraEm?.let { "Última leitura: ${it.take(10)}" } ?: "Sem leituras registradas.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onEdit) { Text("Editar") }
                OutlinedButton(onClick = onLeitura) { Text("Leitura") }
                IconButton(onClick = onArchive) { Icon(Icons.Filled.Delete, contentDescription = "Arquivar") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SiloFormDialog(
    farms: List<FarmLookupData>,
    initial: SiloData?,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (nome: String, produto: String, fabricante: String, farmId: String, capacidade: String, diametro: String, altura: String, externalId: String) -> Unit,
) {
    var nome by remember { mutableStateOf(initial?.nome ?: "") }
    var produto by remember { mutableStateOf(initial?.produto ?: PRODUTOS[0]) }
    var fabricante by remember { mutableStateOf(initial?.fabricante ?: "") }
    var farmId by remember { mutableStateOf(initial?.farmId ?: "") }
    var capacidade by remember { mutableStateOf(initial?.capacidadeT?.toString() ?: "") }
    var diametro by remember { mutableStateOf(initial?.diametroM?.toString() ?: "") }
    var altura by remember { mutableStateOf(initial?.alturaM?.toString() ?: "") }
    var externalId by remember { mutableStateOf(initial?.externalId ?: "") }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (initial != null) "Editar silo" else "Novo silo") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = nome, onValueChange = { nome = it }, label = { Text("Nome *") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = appFieldColors())
                SearchableDropdownField(value = produto, label = "Produto", options = PRODUTOS.map { it to it }, onSelect = { produto = it }, emptyOptionLabel = null)
                SearchableDropdownField(value = fabricante, label = "Fabricante", options = FABRICANTES.map { it to it }, onSelect = { fabricante = it }, emptyOptionLabel = "Não informado")
                SearchableDropdownField(value = farmId, label = "Fazenda", options = farms.map { it.id to it.name }, onSelect = { farmId = it }, emptyOptionLabel = "Sem fazenda")
                OutlinedTextField(
                    value = capacidade, onValueChange = { capacidade = it }, label = { Text("Capacidade (t)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true, modifier = Modifier.fillMaxWidth(), colors = appFieldColors(),
                )
                OutlinedTextField(
                    value = diametro, onValueChange = { diametro = it }, label = { Text("Diâmetro (m)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true, modifier = Modifier.fillMaxWidth(), colors = appFieldColors(),
                )
                OutlinedTextField(
                    value = altura, onValueChange = { altura = it }, label = { Text("Altura (m)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true, modifier = Modifier.fillMaxWidth(), colors = appFieldColors(),
                )
                OutlinedTextField(value = externalId, onValueChange = { externalId = it }, label = { Text("ID externo (sensor/provedor)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = appFieldColors())
                if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(enabled = !busy && nome.isNotBlank(), onClick = { onSubmit(nome, produto, fabricante, farmId, capacidade, diametro, altura, externalId) }) {
                if (busy) CircularProgressIndicator(modifier = Modifier.height(18.dp)) else Text("Salvar")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancelar") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LeituraDialog(
    siloNome: String,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (nivel: String, temp: String, umid: String, dataBr: String) -> Unit,
) {
    var nivel by remember { mutableStateOf("") }
    var temp by remember { mutableStateOf("") }
    var umid by remember { mutableStateOf("") }
    var dataBr by remember { mutableStateOf(todayBr()) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Leitura -- $siloNome") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = nivel, onValueChange = { nivel = it }, label = { Text("Nível (%) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true, modifier = Modifier.fillMaxWidth(), colors = appFieldColors(),
                )
                OutlinedTextField(
                    value = temp, onValueChange = { temp = it }, label = { Text("Temperatura (°C)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true, modifier = Modifier.fillMaxWidth(), colors = appFieldColors(),
                )
                OutlinedTextField(
                    value = umid, onValueChange = { umid = it }, label = { Text("Umidade (%)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
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
            Button(enabled = !busy && nivel.isNotBlank(), onClick = { onSubmit(nivel, temp, umid, dataBr) }) {
                if (busy) CircularProgressIndicator(modifier = Modifier.height(18.dp)) else Text("Lançar")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancelar") } },
    )
}
