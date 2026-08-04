package com.bragro.mobile.ui.home

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Card
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bragro.mobile.data.model.AlertData
import com.bragro.mobile.data.model.ActivityEventData
import com.bragro.mobile.data.model.HomeData
import com.bragro.mobile.data.model.NoticeData
import com.bragro.mobile.data.model.WeatherResponse
import com.bragro.mobile.data.repo.AuthRepository
import com.bragro.mobile.data.repo.HomeRepository
import com.bragro.mobile.data.repo.RecordRepository
import com.bragro.mobile.data.repo.WeatherRepository
import com.bragro.mobile.ui.theme.BrBlue
import com.bragro.mobile.ui.theme.BrGreen
import com.bragro.mobile.ui.theme.BrYellow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Réplica mobile da tela "Início" da plataforma web (pedido do usuário:
// "quero o mesmo padrão da plataforma... réplica completa de toda a
// plataforma só que com visual mobile") -- mesma ordem de blocos de
// src/app/(app)/dashboard/page.tsx: saudação, Mural de Avisos, Central de
// Alertas + Monitor de atividade, KPIs, Clima/Câmbio/Cotações e Destaques.
// Dados vêm de /api/mobile/home (ver HomeRepository) + /api/mobile/weather
// (já usada pela tela de Dashboard nativa). A antiga tela de módulos (grade
// com todos os domínios) virou ModulosScreen.kt, acessada pela barra
// inferior (ui/nav/BottomNavBar.kt) em vez de ser a Home.
class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val homeRepository = HomeRepository(app)
    private val weatherRepository = WeatherRepository()
    private val recordRepository = RecordRepository(app)
    private val authRepository = AuthRepository(app)

    var home = mutableStateOf<HomeData?>(null)
        private set
    var weather = mutableStateOf<WeatherResponse?>(null)
        private set
    var pendingCount = mutableStateOf(0)
        private set
    var loading = mutableStateOf(false)
        private set
    var syncing = mutableStateOf(false)
        private set

    init {
        viewModelScope.launch { recordRepository.observePendingCount().collectLatest { pendingCount.value = it } }
        refresh()
    }

    fun refresh() {
        if (loading.value) return
        loading.value = true
        viewModelScope.launch {
            home.value = homeRepository.fetch() ?: home.value
            loading.value = false
        }
        viewModelScope.launch { weather.value = weatherRepository.fetch() }
    }

    fun syncNow() {
        if (syncing.value) return
        syncing.value = true
        viewModelScope.launch {
            recordRepository.syncAll()
            syncing.value = false
        }
    }

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            authRepository.logout()
            onDone()
        }
    }
}

private fun formatMoneyBrl(value: Double): String =
    NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(value)

private fun todayLongBrazil(): String {
    val fmt = SimpleDateFormat("EEEE, dd 'de' MMMM 'de' yyyy", Locale("pt", "BR"))
    return fmt.format(Date()).replaceFirstChar { it.uppercase() }
}

// Mesmas cores suaves da bandeira do Brasil usadas no Mural de Avisos do
// site (NOTICE_TONE em bulletin-board-client.tsx), ciclando por aviso.
private val NOTICE_TONES = listOf(BrGreen, BrYellow, BrBlue)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenDomain: (String) -> Unit,
    onOpenDashboard: () -> Unit,
    onLoggedOut: () -> Unit,
    viewModel: HomeViewModel = viewModel(),
) {
    val home by viewModel.home
    val weather by viewModel.weather
    val pending by viewModel.pendingCount
    val loading by viewModel.loading
    val syncing by viewModel.syncing

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("BRAgro") },
                actions = {
                    IconButton(onClick = onOpenDashboard) {
                        Icon(Icons.Filled.Dashboard, contentDescription = "Atalhos (DRE, Análises, NF-e, Romaneio)")
                    }
                    IconButton(onClick = { viewModel.syncNow(); viewModel.refresh() }) {
                        if (syncing || loading) CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                        else Icon(Icons.Filled.CloudSync, contentDescription = "Sincronizar agora")
                    }
                    IconButton(onClick = { viewModel.logout(onLoggedOut) }) {
                        Icon(Icons.Filled.Logout, contentDescription = "Sair")
                    }
                },
            )
        },
    ) { padding ->
        val data = home
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "greeting") {
                Column {
                    Text("Olá, bem-vindo de volta", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "${data?.orgName ?: "BRAgro"} — ${todayLongBrazil()}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "Conectando a força da nossa terra, carregando o Brasil no coração.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        fontWeight = FontWeight.Medium,
                        color = BrGreen,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    if (pending > 0) {
                        Text(
                            "$pending lançamento(s) aguardando conexão para sincronizar.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }

            if (data == null) {
                item(key = "empty") {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            if (loading) "Carregando..." else "Sem dados ainda. Conecte-se à internet e atualize.",
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
                return@LazyColumn
            }

            item(key = "mural") { BulletinBoardCard(data.notices) }
            item(key = "alertas") { AlertsCard(data.alerts, onOpenDomain) }
            item(key = "monitor") { ActivityMonitorCard(data.recentActivity) }
            item(key = "kpis") { KpiGrid(data) }
            if (weather != null) {
                item(key = "clima") { WeatherCambioCard(weather!!) }
            }
            item(key = "destaques") { DestaquesCard(data) }
        }
    }
}

@Composable
private fun BulletinBoardCard(notices: List<NoticeData>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Mural de Avisos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (notices.isEmpty()) {
                Text("Nenhum aviso publicado.", style = MaterialTheme.typography.bodySmall)
            } else {
                notices.forEachIndexed { i, n ->
                    val tone = NOTICE_TONES[i % NOTICE_TONES.size]
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                    ) {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            if (n.fixado) {
                                Icon(Icons.Filled.PushPin, contentDescription = null, tint = BrYellow, modifier = Modifier.padding(end = 4.dp))
                            }
                            Text(n.titulo, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = tone)
                        }
                        Text(n.mensagem, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun AlertsCard(alerts: List<AlertData>, onOpenDomain: (String) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Central de Alertas (${alerts.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (alerts.isEmpty()) {
                Text("Nenhum alerta no momento. Tudo em dia.", style = MaterialTheme.typography.bodySmall)
            } else {
                alerts.forEach { a ->
                    val domainId = a.href.removePrefix("/m/")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .then(
                                if (domainId.isNotBlank()) Modifier.clickable { onOpenDomain(domainId) } else Modifier
                            ),
                        verticalAlignment = androidx.compose.ui.Alignment.Top,
                    ) {
                        Icon(
                            Icons.Filled.NotificationsActive,
                            contentDescription = null,
                            tint = if (a.severidade == "alta") MaterialTheme.colorScheme.error else BrYellow,
                            modifier = Modifier.padding(top = 2.dp, end = 8.dp),
                        )
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(a.titulo, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text(a.descricao, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityMonitorCard(events: List<ActivityEventData>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Monitor de atividade recente", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (events.isEmpty()) {
                Text("Nenhuma atividade recente.", style = MaterialTheme.typography.bodySmall)
            } else {
                events.forEach { e ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("${e.tableLabel} — ${e.type}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

private data class Kpi(val label: String, val value: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val color: Color)

@Composable
private fun KpiGrid(data: HomeData) {
    val kpis = listOf(
        Kpi("Em aberto (financeiro)", formatMoneyBrl(data.saldoFinanceiroAberto), Icons.Filled.AccountBalanceWallet, BrGreen),
        Kpi("Itens no estoque", data.itensEstoque.toString(), Icons.Filled.Inventory2, BrYellow),
        Kpi("Operações de safra em andamento", data.safrasAtivas.toString(), Icons.Filled.Eco, BrBlue),
        Kpi("Colaboradores ativos", data.colaboradoresAtivos.toString(), Icons.Filled.Groups, BrGreen),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        kpis.chunked(2).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { kpi ->
                    Card(modifier = Modifier.weight(1f)) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Icon(kpi.icon, contentDescription = null, tint = kpi.color, modifier = Modifier.padding(end = 8.dp))
                            Column {
                                Text(kpi.value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(kpi.label, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeatherCambioCard(w: WeatherResponse) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Clima, câmbio e cotações (agora)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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

@Composable
private fun DestaquesCard(data: HomeData) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Destaques", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row { Text("Cultura líder: "); Text(data.culturaLider ?: "—", fontWeight = FontWeight.Bold) }
            Row { Text("Pedidos em atraso: "); Text(data.pedidosAtrasados.toString(), fontWeight = FontWeight.Bold) }
        }
    }
}
