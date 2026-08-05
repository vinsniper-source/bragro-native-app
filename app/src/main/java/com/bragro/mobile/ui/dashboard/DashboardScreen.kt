package com.bragro.mobile.ui.dashboard

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import com.bragro.mobile.ui.theme.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bragro.mobile.data.local.DashboardEntity
import com.bragro.mobile.data.model.WeatherResponse
import com.bragro.mobile.data.repo.DashboardRepository
import com.bragro.mobile.data.repo.WeatherRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

// Fase 2 do app nativo (Task #31/#35): tela de Dashboard, espelhando os
// KPIs de src/app/(app)/dashboard/page.tsx no site (Em aberto no
// financeiro, itens no estoque, safras em andamento, colaboradores
// ativos, cultura lider, pedidos em atraso) -- via /api/mobile/dashboard,
// que reaproveita a MESMA getDashboardStats() do site -- + um card de
// Clima/Cambio/Cotacoes ao vivo (via /api/mobile/weather, rota publica),
// buscado a parte e SEM cache offline (mesmo criterio ja usado por
// CachedDashboard no site: "Dolar agora"/"clima agora" desatualizado
// seria mais confuso que util, entao some da tela quando offline em vez
// de mostrar um valor velho).
class DashboardViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = DashboardRepository(app)
    private val weatherRepository = WeatherRepository()

    var dashboard = mutableStateOf<DashboardEntity?>(null)
        private set
    var loading = mutableStateOf(false)
        private set
    var offline = mutableStateOf(false)
        private set
    var weather = mutableStateOf<WeatherResponse?>(null)
        private set

    init {
        viewModelScope.launch { repository.observeCached().collectLatest { dashboard.value = it } }
        refresh()
    }

    fun refresh() {
        if (loading.value) return
        loading.value = true
        viewModelScope.launch {
            val ok = repository.refresh()
            offline.value = !ok
            loading.value = false
        }
        viewModelScope.launch { weather.value = weatherRepository.fetch() }
    }
}

private fun formatMoneyBrl(value: Double): String =
    NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(value)

private data class DashboardKpi(val label: String, val value: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(onBack: () -> Unit, onOpenDre: () -> Unit, onOpenAnalises: () -> Unit, onOpenNfeImport: () -> Unit, onOpenRomaneioQuick: () -> Unit, viewModel: DashboardViewModel = viewModel()) {
    val dashboard by viewModel.dashboard
    val loading by viewModel.loading
    val offline by viewModel.offline
    val weather by viewModel.weather

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Início") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        if (loading) CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                        else Icon(Icons.Filled.Refresh, contentDescription = "Atualizar")
                    }
                },
            )
        },
    ) { padding ->
        val data = dashboard
        if (data == null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    if (loading) "Carregando..." else "Sem dados ainda. Conecte-se à internet e atualize.",
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                )
            }
            return@Scaffold
        }

        val kpis = listOf(
            DashboardKpi("Em aberto (financeiro)", formatMoneyBrl(data.saldoFinanceiroAberto)),
            DashboardKpi("Itens no estoque", data.itensEstoque.toString()),
            DashboardKpi("Safras em andamento", data.safrasAtivas.toString()),
            DashboardKpi("Colaboradores ativos", data.colaboradoresAtivos.toString()),
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Column {
                    Text(data.orgName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (offline) {
                        Text(
                            "Sem conexão -- mostrando o último retrato salvo neste aparelho.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            // Grade 2x2 manual (Row+Row) em vez de LazyVerticalGrid: um grid
            // "lazy" dentro de um LazyColumn (tambem scrollavel) sem altura
            // fixa quebra em tempo de execucao (altura vertical ilimitada) --
            // com so 4 KPIs fixos, um layout manual e mais simples e seguro.
            kpis.chunked(2).forEach { row ->
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { kpi ->
                            Card(modifier = Modifier.weight(1f)) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(kpi.value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                    Text(kpi.label, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
            // Fase 2 (Task #35): so aparece quando a busca ao vivo deu certo
            // (sem cache offline de proposito, ver comentario no ViewModel) --
            // fica simplesmente ausente da tela em vez de mostrar um valor
            // antigo de Clima/Cambio/Cotacoes.
            val w = weather
            if (w != null) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Clima e câmbio (agora)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            w.weather?.let { clima ->
                                Row {
                                    Text("${clima.currentIcon} ${clima.currentTempC.toInt()}°C -- ")
                                    Text("máx ${clima.todayMaxC.toInt()}° / mín ${clima.todayMinC.toInt()}°")
                                }
                            }
                            w.fx?.let { fx ->
                                Row {
                                    Text("Dólar: "); Text(fx.usdBrl?.let { formatMoneyBrl(it) } ?: "—", fontWeight = FontWeight.Bold)
                                    Text("   Euro: "); Text(fx.eurBrl?.let { formatMoneyBrl(it) } ?: "—", fontWeight = FontWeight.Bold)
                                }
                            }
                            w.commodities?.let { com ->
                                listOfNotNull(com.soja, com.milho, com.sorgo).forEach { q ->
                                    Row { Text("${q.nome}: "); Text("${formatMoneyBrl(q.valor)}/${q.unidade}", fontWeight = FontWeight.Bold) }
                                }
                            }
                        }
                    }
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Destaques", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Row { Text("Cultura líder: "); Text(data.culturaLider ?: "—", fontWeight = FontWeight.Bold) }
                        Row { Text("Pedidos em atraso: "); Text(data.pedidosAtrasados.toString(), fontWeight = FontWeight.Bold) }
                    }
                }
            }
            // Fase 2 (Task #32): atalho pro DRE consolidado, mesmo criterio
            // de "incremento pratico" usado pra escolher a proxima tela --
            // reaproveita o mesmo padrao de tela/rota/cache offline do
            // Dashboard acima.
            item {
                Card(onClick = onOpenDre, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Ver DRE (custo por fazenda)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Custo total, custo/ha, custo/sc, receita e margem por fazenda", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            // Fase 2 (Task #36): mesmo criterio de atalho usado pro DRE acima.
            item {
                Card(onClick = onOpenAnalises, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Ver Análises cruzadas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Planejado x Realizado, Custo/ha por fonte, Clima x Produtividade e mais", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            // Fase 2 (Task #40): atalho pra importar XML de NF-e -- mesmo
            // criterio de atalho usado pro DRE/Analises acima.
            item {
                Card(onClick = onOpenNfeImport, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Importar NF-e (XML)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Escolha o arquivo XML da nota e gere Estoque + Financeiro automaticamente", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            // Fase 2 (Task #42): atalho pro Romaneio rapido (balanca) --
            // mesmo criterio de atalho usado pros cards acima.
            item {
                Card(onClick = onOpenRomaneioQuick, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Romaneio rápido (balança)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Foto do ticket com leitura automática (OCR no aparelho) + peso bruto e tara", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
