package com.bragro.mobile.ui.silos

import androidx.compose.material.icons.filled.Refresh
import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bolt
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
import com.bragro.mobile.data.model.SiloLeituraData
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
    var filtroSiloId = mutableStateOf("")
        private set
    var leituras = mutableStateOf<List<SiloLeituraData>>(emptyList())
        private set
    var leiturasLoading = mutableStateOf(false)
        private set
    var loading = mutableStateOf(false)
        private set
    var busy = mutableStateOf(false)
        private set
    var errorMessage = mutableStateOf<String?>(null)
        private set

    /** Filtro do gráfico de estoque por silo ("" = todos). */
    fun setFiltro(siloId: String) {
        filtroSiloId.value = siloId
        viewModelScope.launch { estoque.value = repo.estoque(siloId.takeIf { it.isNotBlank() }).estoque ?: emptyList() }
    }

    /** Histórico de leituras (últimos 60 dias) de um silo. */
    fun carregarLeituras(siloId: String) {
        leituras.value = emptyList()
        leiturasLoading.value = true
        viewModelScope.launch {
            leituras.value = repo.listarLeituras(siloId).leituras ?: emptyList()
            leiturasLoading.value = false
        }
    }

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
        estoque.value = repo.estoque(filtroSiloId.value.takeIf { it.isNotBlank() }).estoque ?: estoque.value
    }

    /** Leitura rápida direto do card (controle deslizante/atalhos): mantém a
     * última temperatura/umidade conhecidas e usa o instante atual. */
    fun leituraRapida(silo: SiloData, nivelPct: Double) {
        busy.value = true
        errorMessage.value = null
        viewModelScope.launch {
            val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            fmt.timeZone = java.util.TimeZone.getTimeZone("UTC")
            val r = repo.lancarLeitura(silo.id, nivelPct, silo.temperaturaAtualC, silo.umidadeAtualPct, fmt.format(java.util.Date()))
            busy.value = false
            if (r.ok) recarregar() else errorMessage.value = r.error ?: "Falha ao salvar leitura."
        }
    }

    fun salvar(
        id: String?, nome: String, produto: String, fabricante: String, farmId: String,
        capacidade: String, diametro: String, altura: String, externalId: String,
        nivelCritico: String, lembreteDias: String,
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
                nivelCritico.replace(',', '.').toDoubleOrNull(), lembreteDias.trim().toIntOrNull(),
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
    val silosTodos by viewModel.silos
    val farmSel = com.bragro.mobile.ui.domain.FarmSelection.selected.value
    val farms by viewModel.farms
    // v14: só a fazenda escolhida no topo.
    val silos = remember(silosTodos, farms, farmSel) {
        if (farmSel == null) silosTodos else silosTodos.filter { x -> farms.firstOrNull { it.id == x.farmId }?.name?.let { com.bragro.mobile.ui.domain.normalizarTexto(it) } == com.bragro.mobile.ui.domain.normalizarTexto(farmSel) }
    }
    val estoque by viewModel.estoque
    val loading by viewModel.loading
    val busy by viewModel.busy
    val error by viewModel.errorMessage
    var showForm by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<SiloData?>(null) }
    var leituraSilo by remember { mutableStateOf<SiloData?>(null) }
    var historicoSilo by remember { mutableStateOf<SiloData?>(null) }
    var estoqueAberto by remember { mutableStateOf(false) }
    var silosAbertos by remember { mutableStateOf(false) }
    var prestadoraAberta by remember { mutableStateOf(false) }
    val filtroSilo by viewModel.filtroSiloId
    val leituras by viewModel.leituras
    val leiturasLoading by viewModel.leiturasLoading

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
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Silos", color = MaterialTheme.colorScheme.onSurface)
                    }
                } },
                actions = { Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        com.bragro.mobile.ui.domain.HomeNavButton()
                    } },
            )
        },
        // Botão flutuante removido -- "+ Silo" fica em Ações (padrão v7).
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (error != null && !showForm && leituraSilo == null) {
                item(key = "erro") {
                    Text(error ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
            item(key = "painel") {
                val capTotal = silos.sumOf { it.capacidadeT ?: 0.0 }
                val armazenado = silos.sumOf { (it.capacidadeT ?: 0.0) * ((it.estimadoPct ?: it.nivelAtualPct ?: 0.0) / 100.0) }
                val emAlerta = silos.count { it.status == "ALERTA" || it.status == "CRITICO" }
                val criticos = silos.count { it.nivelCritico }
                val pendentes = silos.count { it.leituraAtrasada }
                val produtos = silos.mapNotNull { it.produto?.lowercase()?.takeIf { p -> p.isNotBlank() } }.toSet().joinToString(", ").ifBlank { "—" }
                com.bragro.mobile.ui.domain.PainelMonitoramento(
                    com.bragro.mobile.ui.domain.PainelModulo(
                        titulo = "Silos · armazenagem",
                        subtitulo = "${silos.size} silo(s) · ${silos.mapNotNull { it.farmId }.toSet().size} fazenda(s)",
                        kpis = listOf(
                            com.bragro.mobile.ui.domain.PainelKpi("Silos ativos", silos.size.toDouble(), "numero", null, produtos, "Silos"),
                            com.bragro.mobile.ui.domain.PainelKpi("Armazenado", Math.round(armazenado).toDouble(), "numero", "t", "de ${Math.round(capTotal)} t", "Estoque"),
                            com.bragro.mobile.ui.domain.PainelKpi("Atenção/crítico", emAlerta.toDouble(), "numero", null, if (criticos > 0) "$criticos abaixo do limite" else "nenhum crítico", "Silos", tom = if (emAlerta > 0) "acima" else null),
                            com.bragro.mobile.ui.domain.PainelKpi("Leitura pendente", pendentes.toDouble(), "numero", null, "lembrete", "Silos", tom = if (pendentes > 0) "acima" else null),
                        ),
                        barras = if (capTotal > 0) listOf(com.bragro.mobile.ui.domain.PainelBarra("Ocupação", Math.round(armazenado / capTotal * 100).toDouble(), "${Math.round(armazenado)}/${Math.round(capTotal)} t", neutra = true)) else emptyList(),
                    ),
                    onKpi = { k -> if (k.destino == "Estoque") estoqueAberto = true else silosAbertos = true },
                )
            }
            item(key = "acoes") {
                com.bragro.mobile.ui.domain.AcoesEmAbas(
                    dados = {
                        com.bragro.mobile.ui.domain.LabeledIconButton(icon = Icons.Filled.BarChart, label = "Estoque", active = estoqueAberto, onClick = { estoqueAberto = !estoqueAberto })
                        com.bragro.mobile.ui.domain.LabeledIconButton(icon = Icons.Filled.ViewList, label = "Silos", active = silosAbertos, onClick = { silosAbertos = !silosAbertos })
                    },
                    operacoes = {
                        com.bragro.mobile.ui.domain.LabeledIconButton(icon = Icons.Filled.Storage, label = "+ Silo", onClick = { editing = null; showForm = true })
                        com.bragro.mobile.ui.domain.LabeledIconButton(icon = Icons.Filled.Refresh, label = "Atualizar", loading = loading, onClick = { viewModel.load() })
                        com.bragro.mobile.ui.domain.LabeledIconButton(icon = Icons.Filled.Bolt, label = "Prestadora", onClick = { prestadoraAberta = true })
                    },
                    arquivos = {
                        com.bragro.mobile.ui.domain.ArquivosTabela("Silos", listOf("Silo", "Fazenda", "Produto", "Capacidade (t)", "Nível (%)", "Temperatura (°C)", "Umidade (%)", "Status"), nuvem = true) {
                            silos.map { x -> listOf(x.nome, farms.firstOrNull { it.id == x.farmId }?.name.orEmpty(), x.produto.orEmpty(), x.capacidadeT?.toString().orEmpty(), (x.estimadoPct ?: x.nivelAtualPct)?.toInt()?.toString().orEmpty(), x.temperaturaAtualC?.toString().orEmpty(), x.umidadeAtualPct?.toString().orEmpty(), statusLabel(x.status)) }
                        }
                    },
                )
            }
            item(key = "estoque-header") {
                com.bragro.mobile.ui.domain.CabecalhoLancamentos(total = 0, aberto = estoqueAberto, titulo = "Estoque por silo", rotulo = "30 dias") { estoqueAberto = !estoqueAberto }
            }
            if (estoqueAberto) item(key = "estoque") {
                com.bragro.mobile.ui.domain.BlocoGraficoBarras(
                    titulo = "Estoque armazenado (t, 30 dias)",
                    categorias = estoque.map { com.bragro.mobile.ui.domain.diaMesBr(it.data) },
                    series = listOf("Armazenado (t)" to estoque.map { it.quantidadeT }),
                    isMoney = false,
                    modifier = Modifier.padding(bottom = 12.dp),
                ) {
                    Column {
                        Text("Estoque armazenado (t, 30 dias)", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        SearchableDropdownField(
                            value = filtroSilo,
                            label = "Silo",
                            options = silos.map { it.id to it.nome },
                            onSelect = { viewModel.setFiltro(it) },
                            emptyOptionLabel = "Todos os silos",
                        )
                        Spacer(Modifier.height(8.dp))
                        if (estoque.isEmpty()) {
                            Text("Sem leituras com capacidade cadastrada no período.", style = MaterialTheme.typography.bodySmall)
                        } else {
                            SimpleBarChart(
                                categories = estoque.map { com.bragro.mobile.ui.domain.diaMesBr(it.data) },
                                series = listOf(BarSeries("Armazenado (t)", estoque.map { it.quantidadeT }, MaterialTheme.colorScheme.primary)),
                                isMoney = false,
                            )
                        }
                    }
                }
            }
            item(key = "silos-header") {
                com.bragro.mobile.ui.domain.CabecalhoLancamentos(total = silos.size, aberto = silosAbertos, titulo = "Silos") { silosAbertos = !silosAbertos }
            }
            if (silosAbertos) {
                items(silos.filter { it.nivelCritico }, key = { "c-" + it.id }) { s ->
                    AlertaBanner(
                        "Nível crítico: ${s.nome} está com ${(s.estimadoPct ?: s.nivelAtualPct ?: 0.0).toInt()}% (limite ${s.nivelCriticoLimitePct.toInt()}%).",
                        critico = true,
                    )
                }
                items(silos.filter { it.leituraAtrasada }, key = { "a-" + it.id }) { s ->
                    AlertaBanner(
                        "Leitura pendente: ${s.nome} " +
                            (s.diasSemLeitura?.let { "sem registro há $it dias" } ?: "ainda não tem leitura registrada") +
                            " (lembrete a cada ${s.lembreteDias}).",
                        critico = false,
                    )
                }
            }
            if (silosAbertos) when {
                loading -> item(key = "loading") {
                    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(modifier = Modifier.padding(top = 24.dp))
                    }
                }
                silos.isEmpty() -> {
                    item(key = "vazio") {
                        Text("Nenhum silo cadastrado ainda. Toque no botão para adicionar.", modifier = Modifier.padding(vertical = 12.dp))
                    }
                    item(key = "exemplo") {
                        // Card de exemplo (fictício, sem ação) -- mostra o layout antes do primeiro cadastro.
                        Column(Modifier.alpha(0.6f)) {
                            Text("EXEMPLO", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            SiloCard(
                                silo = SiloData(
                                    id = "exemplo", nome = "Silo 01 — Exemplo", produto = "Soja", capacidadeT = 1000.0,
                                    nivelAtualPct = 62.0, temperaturaAtualC = 24.5, umidadeAtualPct = 13.2,
                                    status = "OK",
                                ),
                                farmNome = "Fazenda exemplo",
                                onEdit = {}, onLeitura = {}, onHistorico = {}, onArchive = {}, onSalvarRapido = {}, busy = true,
                            )
                        }
                    }
                }
                else -> items(silos, key = { it.id }) { silo ->
                    SiloCard(
                        silo = silo,
                        farmNome = farms.firstOrNull { it.id == silo.farmId }?.name,
                        onEdit = { editing = silo; showForm = true },
                        onLeitura = { leituraSilo = silo },
                        onHistorico = { historicoSilo = silo; viewModel.carregarLeituras(silo.id) },
                        onArchive = { viewModel.arquivar(silo.id) },
                        onSalvarRapido = { pct -> viewModel.leituraRapida(silo, pct) },
                        busy = busy,
                    )
                }
            }
        }
    }

    if (prestadoraAberta) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { prestadoraAberta = false },
            modifier = Modifier.fillMaxWidth(0.96f),
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            title = { Text("Leitura automática (prestadora)") },
            text = { Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) { ModuleProviderIntegrationCard(domainId = "estoque") } },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { prestadoraAberta = false }) { Text("Fechar") } },
        )
    }

    if (showForm) {
        SiloFormDialog(
            farms = farms, initial = editing, busy = busy, error = error,
            onDismiss = { showForm = false },
            onSubmit = { nome, produto, fab, farmId, cap, diam, alt, ext, crit, lemb ->
                viewModel.salvar(editing?.id, nome, produto, fab, farmId, cap, diam, alt, ext, crit, lemb) { ok -> if (ok) showForm = false }
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

    historicoSilo?.let { silo ->
        HistoricoLeiturasDialog(silo.nome, leituras, leiturasLoading) { historicoSilo = null }
    }
}

@Composable
private fun KpiBox(titulo: String, valor: String, modifier: Modifier = Modifier, destaque: Boolean = false) {
    Card(modifier = modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(titulo, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                valor,
                style = MaterialTheme.typography.titleMedium,
                color = if (destaque) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun HistoricoLeiturasDialog(siloNome: String, leituras: List<SiloLeituraData>, loading: Boolean, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Histórico -- $siloNome") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                when {
                    loading -> CircularProgressIndicator(modifier = Modifier.padding(12.dp))
                    leituras.isEmpty() -> Text("Nenhuma leitura nos últimos 60 dias.", style = MaterialTheme.typography.bodySmall)
                    else -> leituras.forEach { l ->
                        Text(
                            "${l.recordedAt.take(10)} · ${l.nivelPct.toInt()}%" +
                                (l.quantidadeT?.let { " · ${"%.1f".format(Locale("pt", "BR"), it)} t" } ?: "") +
                                (l.temperaturaC?.let { " · $it °C" } ?: "") +
                                (l.umidadePct?.let { " · $it %" } ?: ""),
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
private fun AlertaBanner(texto: String, critico: Boolean) {
    val bg = if (critico) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer
    val fg = if (critico) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onTertiaryContainer
    Text(
        texto,
        style = MaterialTheme.typography.bodySmall,
        color = fg,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .background(bg, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
private fun SiloCard(
    silo: SiloData,
    farmNome: String?,
    onEdit: () -> Unit,
    onLeitura: () -> Unit,
    onHistorico: () -> Unit,
    onArchive: () -> Unit,
    onSalvarRapido: (Double) -> Unit,
    busy: Boolean,
) {
    val base = (silo.estimadoPct ?: silo.nivelAtualPct ?: 0.0).toInt().toFloat()
    var pct by remember(base) { mutableStateOf(base) }
    val dirty = pct != base
    val estimado = silo.estimadoT != null
    val critico = pct > 0f && pct <= silo.nivelCriticoLimitePct
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 8.dp))
                Text(silo.nome.uppercase(), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (estimado) Badge(modifier = Modifier.padding(end = 4.dp)) { Text("Estimado") }
                Badge { Text(statusLabel(silo.status)) }
            }
            Text(farmNome?.uppercase() ?: "Sem fazenda vinculada", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                listOfNotNull(silo.produto, silo.fabricante, silo.capacidadeT?.let { "${it.toInt()} t" }).joinToString(" · ").ifBlank { "—" },
                style = MaterialTheme.typography.bodySmall,
            )
            val toneladas = silo.capacidadeT?.let { it * pct / 100.0 }
            Text(
                (toneladas?.let { "%.1f de %d t · ".format(Locale("pt", "BR"), it, silo.capacidadeT!!.toInt()) } ?: "") + "${pct.toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = if (critico) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 6.dp),
            )
            Slider(
                value = pct,
                onValueChange = { pct = it.toInt().toFloat() },
                valueRange = 0f..100f,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf(25f to "25%", 50f to "50%", 75f to "75%", 100f to "Cheio").forEach { (v, label) ->
                    OutlinedButton(onClick = { pct = v }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                        Text(label, style = MaterialTheme.typography.labelSmall)
                    }
                }
                Spacer(Modifier.weight(1f))
                Button(
                    enabled = !busy && (dirty || silo.leituraAtrasada || estimado),
                    onClick = { onSalvarRapido(pct.toDouble()) },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                ) { Text(if (estimado && !dirty) "Confirmar" else "Salvar", style = MaterialTheme.typography.labelMedium) }
            }
            if (estimado && silo.movimentos.isNotEmpty()) {
                Text(
                    "Leitura real + ${"%.1f".format(Locale("pt", "BR"), silo.movimentos.sumOf { it.toneladas })} t em romaneios desde a última leitura:",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 6.dp),
                )
                silo.movimentos.forEach { m ->
                    Text(
                        "Romaneio ${m.noRomaneio} · +${"%.1f".format(Locale("pt", "BR"), m.toneladas)} t · ${m.data.take(10)}" +
                            (if (m.editadoPor != null && m.editadoEm != null) " · por ${m.editadoPor} em ${m.editadoEm.take(16).replace('T', ' ')}" else ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Thermostat, contentDescription = null, modifier = Modifier.height(14.dp))
                    Text(" ${silo.temperaturaAtualC?.let { "${"%,.1f".format(java.util.Locale("pt", "BR"), it)} °C" } ?: "—"}", style = MaterialTheme.typography.labelSmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Opacity, contentDescription = null, modifier = Modifier.height(14.dp))
                    Text(" ${silo.umidadeAtualPct?.let { "${"%,.1f".format(java.util.Locale("pt", "BR"), it)}%" } ?: "—"}", style = MaterialTheme.typography.labelSmall)
                }
            }
            Text(
                silo.ultimaLeituraEm?.let { "Última leitura: ${it.take(10)}" } ?: "Sem leituras registradas.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                val pad = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f), contentPadding = pad) { Text("Editar", maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelMedium) }
                OutlinedButton(onClick = onLeitura, modifier = Modifier.weight(1.5f), contentPadding = pad) { Text("Temp./Umid.", maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelMedium) }
                OutlinedButton(onClick = onHistorico, modifier = Modifier.weight(1.2f), contentPadding = pad) { Text("Histórico", maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelMedium) }
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
    onSubmit: (nome: String, produto: String, fabricante: String, farmId: String, capacidade: String, diametro: String, altura: String, externalId: String, nivelCritico: String, lembreteDias: String) -> Unit,
) {
    var nivelCritico by remember { mutableStateOf(initial?.nivelCriticoPct?.toString() ?: "") }
    var lembreteDias by remember { mutableStateOf(initial?.lembreteLeituraDias?.toString() ?: "") }
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
                OutlinedTextField(
                    value = nivelCritico, onValueChange = { nivelCritico = it }, label = { Text("Alerta crítico abaixo de (%) — padrão 15") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true, modifier = Modifier.fillMaxWidth(), colors = appFieldColors(),
                )
                OutlinedTextField(
                    value = lembreteDias, onValueChange = { lembreteDias = it }, label = { Text("Lembrar leitura a cada (dias) — padrão 7") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, modifier = Modifier.fillMaxWidth(), colors = appFieldColors(),
                )
                OutlinedTextField(value = externalId, onValueChange = { externalId = it }, label = { Text("ID externo (sensor/provedor)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = appFieldColors())
                if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(enabled = !busy && nome.isNotBlank(), onClick = { onSubmit(nome, produto, fabricante, farmId, capacidade, diametro, altura, externalId, nivelCritico, lembreteDias) }) {
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
