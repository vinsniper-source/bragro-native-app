package com.bragro.mobile.ui.home

import android.app.Application
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.bragro.mobile.R
import com.bragro.mobile.data.local.AppDatabase
import com.bragro.mobile.data.local.SessionEntity
import com.bragro.mobile.data.model.AlertData
import com.bragro.mobile.data.model.ActivityEventData
import com.bragro.mobile.data.model.HomeData
import com.bragro.mobile.data.model.NoticeData
import com.bragro.mobile.data.model.NotificationItemData
import com.bragro.mobile.data.model.WeatherResponse
import com.bragro.mobile.data.repo.AuthRepository
import com.bragro.mobile.data.repo.BackupRepository
import com.bragro.mobile.data.repo.HomeRepository
import com.bragro.mobile.data.repo.NoticesRepository
import com.bragro.mobile.data.repo.NotificationsRepository
import com.bragro.mobile.data.repo.RecordRepository
import com.bragro.mobile.data.repo.WeatherRepository
import com.bragro.mobile.ui.theme.BrBlue
import com.bragro.mobile.ui.theme.BrGreen
import com.bragro.mobile.ui.theme.BrYellow
import com.bragro.mobile.ui.theme.ThemeToggle
import com.bragro.mobile.ui.util.shareTextFile
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// Réplica mobile da tela "Início" da plataforma web (pedido do usuário:
// "quero o mesmo padrão da plataforma... réplica completa de toda a
// plataforma só que com visual mobile") -- mesma ordem de blocos de
// src/app/(app)/dashboard/page.tsx: saudação, Mural de Avisos, Central de
// Alertas + Monitor de atividade, KPIs, Clima/Câmbio/Cotações (3 cards
// separados) e Destaques. Dados vêm de /api/mobile/home (ver HomeRepository)
// + /api/mobile/weather. A extinta tela de atalhos "Dashboard" (DRE/
// Análises/NF-e/Romaneio Rápido) foi removida -- esse conteúdo agora mora
// nos dropdowns de setor da barra inferior (ver ui/nav/BottomNavBar.kt).
class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val homeRepository = HomeRepository(app)
    private val weatherRepository = WeatherRepository()
    private val recordRepository = RecordRepository(app)
    private val authRepository = AuthRepository(app)
    private val notificationsRepository = NotificationsRepository(app)
    private val backupRepository = BackupRepository(app)
    private val noticesRepository = NoticesRepository(app)
    private val db = AppDatabase.get(app)

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
    var session = mutableStateOf<SessionEntity?>(null)
        private set
    var notifications = mutableStateOf<List<NotificationItemData>>(emptyList())
        private set
    var unreadCount = mutableStateOf(0)
        private set

    init {
        viewModelScope.launch { recordRepository.observePendingCount().collectLatest { pendingCount.value = it } }
        viewModelScope.launch { db.sessionDao().observe().collectLatest { session.value = it } }
        // Mostra o último retrato salvo assim que a tela abre (mesmo antes
        // da 1ª resposta de rede) -- é o que faz o Início aparecer offline
        // em vez de "Sem dados ainda" (pedido do usuário: "quanto ao
        // dashboard é possível colocá-lo para aparecer offline?"). Quando a
        // rede responde, refresh() abaixo sobrescreve com o dado ao vivo.
        viewModelScope.launch { homeRepository.observeCached().collectLatest { cached -> if (cached != null) home.value = cached } }
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

    fun loadNotifications() {
        viewModelScope.launch {
            val result = notificationsRepository.run("list")?.jsonObject ?: return@launch
            val items = result["items"]?.jsonArray.orEmpty().mapNotNull { it as? JsonObject }.map { o ->
                NotificationItemData(
                    id = o["id"]?.jsonPrimitive?.contentOrNull ?: "",
                    tipo = o["tipo"]?.jsonPrimitive?.contentOrNull ?: "info",
                    titulo = o["titulo"]?.jsonPrimitive?.contentOrNull ?: "",
                    mensagem = o["mensagem"]?.jsonPrimitive?.contentOrNull ?: "",
                    link = o["link"]?.jsonPrimitive?.contentOrNull,
                    lida = o["lida"]?.jsonPrimitive?.booleanOrNull ?: false,
                    criadaEm = o["criadaEm"]?.jsonPrimitive?.contentOrNull ?: "",
                )
            }
            notifications.value = items
            unreadCount.value = result["naoLidas"]?.jsonPrimitive?.intOrNull ?: 0
        }
    }

    fun markAllNotificationsRead() {
        viewModelScope.launch {
            notificationsRepository.run("mark-all-read")
            loadNotifications()
        }
    }

    fun downloadBackup(onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val backup = backupRepository.fetch()
            if (backup == null) {
                onResult(false)
                return@launch
            }
            val fileName = "sistema-agro-backup-${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}.json"
            shareTextFile(getApplication(), fileName, "application/json", backup.toString())
            onResult(true)
        }
    }

    fun addNotice(titulo: String, mensagem: String, expiraEm: String?, fixado: Boolean, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = noticesRepository.create(titulo, mensagem, expiraEm, fixado)
            if (ok) refresh()
            onDone(ok)
        }
    }

    fun deleteNotice(id: String) {
        viewModelScope.launch {
            noticesRepository.delete(id)
            refresh()
        }
    }
}

private fun formatMoneyBrl(value: Double): String =
    NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(value)

private fun todayLongBrazil(): String {
    val fmt = SimpleDateFormat("EEEE, dd 'de' MMMM 'de' yyyy", Locale("pt", "BR"))
    return fmt.format(Date()).replaceFirstChar { it.uppercase() }
}

// Converte o "at"/"criadaEm" ISO (formato de Date.toISOString() do
// site/Prisma, sempre UTC) pra HH:mm no fuso do aparelho -- mesmo efeito de
// toLocaleTimeString("pt-BR") usado em realtime-monitor.tsx.
private fun formatEventTime(iso: String): String = try {
    val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
    parser.timeZone = TimeZone.getTimeZone("UTC")
    val date = parser.parse(iso)
    if (date == null) "" else SimpleDateFormat("HH:mm", Locale("pt", "BR")).format(date)
} catch (e: Exception) {
    ""
}

private fun isAdminRole(role: String?): Boolean = role == "OWNER" || role == "ADMIN"

// Mesmas cores suaves da bandeira do Brasil usadas no Mural de Avisos do
// site (NOTICE_TONE em bulletin-board-client.tsx), ciclando por aviso.
private val NOTICE_TONES = listOf(BrGreen, BrYellow, BrBlue)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenDomain: (String) -> Unit,
    onLoggedOut: () -> Unit,
    viewModel: HomeViewModel = viewModel(),
) {
    val home by viewModel.home
    val weather by viewModel.weather
    val pending by viewModel.pendingCount
    val loading by viewModel.loading
    val session by viewModel.session
    val notifications by viewModel.notifications
    val unreadCount by viewModel.unreadCount

    val canManage = isAdminRole(session?.role)
    var userMenuOpen by remember { mutableStateOf(false) }
    var notificationsOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.drawable.logo_oficial_header),
                            contentDescription = "BRAgro",
                            modifier = Modifier.height(32.dp),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { /* já estamos no Início */ }) {
                        Icon(Icons.Filled.Home, contentDescription = "Início")
                    }
                    if (canManage) {
                        IconButton(onClick = { viewModel.downloadBackup { } }) {
                            Icon(Icons.Filled.Backup, contentDescription = "Backup completo")
                        }
                    }
                    IconButton(onClick = { notificationsOpen = true; viewModel.loadNotifications() }) {
                        BadgedBox(badge = { if (unreadCount > 0) Badge { Text(unreadCount.toString()) } }) {
                            Icon(
                                if (unreadCount > 0) Icons.Filled.NotificationsActive else Icons.Filled.NotificationsNone,
                                contentDescription = "Notificações",
                            )
                        }
                    }
                    ThemeToggle()
                    Box {
                        IconButton(onClick = { userMenuOpen = true }) {
                            val avatarUrl = session?.avatarUrl
                            if (!avatarUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = avatarUrl,
                                    contentDescription = "Conta",
                                    modifier = Modifier.size(28.dp).clip(CircleShape),
                                )
                            } else {
                                Icon(Icons.Filled.AccountCircle, contentDescription = "Conta")
                            }
                        }
                        DropdownMenu(expanded = userMenuOpen, onDismissRequest = { userMenuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Sincronizar agora") },
                                leadingIcon = { Icon(Icons.Filled.CloudSync, contentDescription = null) },
                                onClick = { userMenuOpen = false; viewModel.syncNow(); viewModel.refresh() },
                            )
                            DropdownMenuItem(
                                text = { Text("Sair") },
                                leadingIcon = { Icon(Icons.Filled.Logout, contentDescription = null) },
                                onClick = { userMenuOpen = false; viewModel.logout(onLoggedOut) },
                            )
                        }
                    }
                    // Lugar reservado pra logo da organização (canto superior
                    // direito) -- pedido do usuário. Cada organização pode ter
                    // uma (orgLogoUrl, cadastrada no site). Sem logo cadastrada,
                    // o site mostra um botão "+" tracejado só pra quem é
                    // OWNER/ADMIN (isAdmin, ver topbar.tsx) convidando a
                    // cadastrar uma -- réplica aqui: ícone visível (antes era
                    // uma Box vazia, sem nenhuma pista de que dava pra
                    // adicionar logo). Upload em si continua feito pelo site
                    // (Configurações > Identidade), então o toque aqui só
                    // orienta pra lá por enquanto.
                    val orgLogoUrl = session?.orgLogoUrl
                    val logoContext = LocalContext.current
                    if (!orgLogoUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = orgLogoUrl,
                            contentDescription = "Logo da organização",
                            modifier = Modifier.padding(end = 8.dp).size(32.dp).clip(RoundedCornerShape(4.dp)),
                        )
                    } else if (canManage) {
                        // Sem círculo/borda -- pedido do usuário ("retire o
                        // círculo em volta do ícone da logo do cliente"). Tom
                        // de verde da marca (BrGreen) em vez do outline
                        // neutro de antes, mesma cor da barra inferior.
                        IconButton(
                            onClick = {
                                Toast.makeText(
                                    logoContext,
                                    "Envie a logo da empresa pelo site, em Configurações > Identidade",
                                    Toast.LENGTH_LONG,
                                ).show()
                            },
                            modifier = Modifier.padding(end = 8.dp).size(32.dp),
                        ) {
                            Icon(
                                Icons.Filled.AddPhotoAlternate,
                                contentDescription = "Adicionar logo da empresa",
                                modifier = Modifier.size(20.dp),
                                tint = BrGreen,
                            )
                        }
                    } else {
                        Box(modifier = Modifier.padding(end = 8.dp).size(32.dp))
                    }
                },
            )
        },
    ) { padding ->
        val data = home

        if (notificationsOpen) {
            NotificationsDialog(
                items = notifications,
                onMarkAllRead = { viewModel.markAllNotificationsRead() },
                onDismiss = { notificationsOpen = false },
            )
        }

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

            item(key = "mural") { BulletinBoardCard(data.notices, canManage, viewModel) }
            item(key = "alertas") { AlertsCard(data.alerts, onOpenDomain) }
            item(key = "monitor") { ActivityMonitorCard(data.recentActivity) }
            item(key = "kpis") { KpiGrid(data) }
            // Clima ao lado de Câmbio, Cotações ao lado de Destaques -- cada
            // par em blocos separados (Card) lado a lado, pedido do usuário
            // ("coloque câmbio ao lado de clima separados por blocos").
            val clima = weather?.weather
            val fx = weather?.fx
            if (clima != null || fx != null) {
                item(key = "clima-cambio") {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (clima != null) ClimaCard(clima, modifier = Modifier.weight(1f).fillMaxHeight())
                        if (fx != null) CambioCard(fx, modifier = Modifier.weight(1f).fillMaxHeight())
                    }
                }
            }
            item(key = "cotacoes-destaques") {
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    weather?.commodities?.let { CotacoesCard(it, modifier = Modifier.weight(1f).fillMaxHeight()) }
                    DestaquesCard(data, modifier = Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
    }
}

@Composable
private fun NotificationsDialog(items: List<NotificationItemData>, onMarkAllRead: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Notificações") },
        text = {
            if (items.isEmpty()) {
                Text("Nenhuma notificação ainda.", style = MaterialTheme.typography.bodySmall)
            } else {
                Column(
                    modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items.forEach { n ->
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(n.titulo, style = MaterialTheme.typography.bodyMedium, fontWeight = if (!n.lida) FontWeight.Bold else FontWeight.Normal)
                                Spacer(Modifier.width(4.dp))
                                Text("· ${n.tipo}", style = MaterialTheme.typography.labelSmall)
                            }
                            Text(n.mensagem, style = MaterialTheme.typography.bodySmall)
                            Text(formatEventTime(n.criadaEm), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onMarkAllRead() }) {
                Icon(Icons.Filled.DoneAll, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(" Marcar todas como lidas")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Fechar") } },
    )
}

@Composable
private fun BulletinBoardCard(notices: List<NoticeData>, canManage: Boolean, viewModel: HomeViewModel) {
    var showAddDialog by remember { mutableStateOf(false) }

    if (showAddDialog) {
        AddNoticeDialog(
            onDismiss = { showAddDialog = false },
            onSave = { titulo, mensagem, expiraEm, fixado ->
                viewModel.addNotice(titulo, mensagem, expiraEm, fixado) { showAddDialog = false }
            },
        )
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Mural de Avisos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                // Botão de adicionar aviso -- o app antes não tinha NENHUM
                // jeito de publicar um aviso (só o site tinha), pedido
                // explícito do usuário. Só OWNER/ADMIN vê, mesma regra do site.
                if (canManage) {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "Adicionar aviso", tint = BrGreen)
                    }
                }
            }
            if (notices.isEmpty()) {
                Text("Nenhum aviso publicado.", style = MaterialTheme.typography.bodySmall)
            } else {
                notices.forEachIndexed { i, n ->
                    val tone = NOTICE_TONES[i % NOTICE_TONES.size]
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (n.fixado) {
                                    Icon(Icons.Filled.PushPin, contentDescription = null, tint = BrYellow, modifier = Modifier.padding(end = 4.dp))
                                }
                                Text(n.titulo, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = tone)
                            }
                            Text(n.mensagem, style = MaterialTheme.typography.bodySmall)
                        }
                        if (canManage) {
                            IconButton(onClick = { viewModel.deleteNotice(n.id) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Filled.Close, contentDescription = "Excluir aviso", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddNoticeDialog(onDismiss: () -> Unit, onSave: (String, String, String?, Boolean) -> Unit) {
    var titulo by remember { mutableStateOf("") }
    var mensagem by remember { mutableStateOf("") }
    var expiraEm by remember { mutableStateOf("") }
    var fixado by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Novo aviso") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = titulo, onValueChange = { titulo = it }, label = { Text("Título") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = mensagem, onValueChange = { mensagem = it }, label = { Text("Mensagem") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = expiraEm,
                    onValueChange = { expiraEm = it },
                    label = { Text("Expira em (AAAA-MM-DD, opcional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = fixado, onCheckedChange = { fixado = it })
                    Text("Fixar no topo")
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = titulo.isNotBlank() && mensagem.isNotBlank(),
                onClick = { onSave(titulo.trim(), mensagem.trim(), expiraEm.trim().ifBlank { null }, fixado) },
            ) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun CollapsibleHeader(title: String, expanded: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onToggle() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = if (expanded) "Recolher" else "Expandir")
    }
}

@Composable
private fun AlertsCard(alerts: List<AlertData>, onOpenDomain: (String) -> Unit) {
    var expanded by remember { mutableStateOf(true) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CollapsibleHeader("Central de Alertas (${alerts.size})", expanded) { expanded = !expanded }
            if (expanded) {
                if (alerts.isEmpty()) {
                    Text("Nenhum alerta no momento. Tudo em dia.", style = MaterialTheme.typography.bodySmall)
                } else {
                    alerts.forEach { a ->
                        val domainId = a.href.removePrefix("/m/")
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .then(if (domainId.isNotBlank()) Modifier.clickable { onOpenDomain(domainId) } else Modifier),
                            verticalAlignment = Alignment.Top,
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
}

// Réplica da RealtimeMonitor do site (realtime-monitor.tsx): lá cada evento
// é UMA linha só ("tabela — tipo" à esquerda, hora à direita, sem quebrar).
// Aqui juntamos "detail" (operação/item) e "type" (criado/atualizado) na
// MESMA linha em vez de empilhar em 2 -- pedido do usuário ("aplique a
// mesma lógica no monitor do app mobile e coloque toda descrição de um item
// em uma linha só").
@Composable
private fun ActivityMonitorCard(events: List<ActivityEventData>) {
    var expanded by remember { mutableStateOf(true) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Bolt, contentDescription = null, tint = BrGreen, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Box(modifier = Modifier.weight(1f)) {
                    // Contagem no título -- mesmo padrão da Central de
                    // Alertas ("(N)"), pedido do usuário ("coloque a
                    // quantidade do item").
                    CollapsibleHeader("Monitor em tempo real (${events.size})", expanded) { expanded = !expanded }
                }
            }
            if (expanded) {
                if (events.isEmpty()) {
                    Text("Aguardando atividade da equipe...", style = MaterialTheme.typography.bodySmall)
                } else {
                    events.forEach { e ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // "detail" é a operação/item do evento (ex.:
                            // "Adubação", "Diesel S10") -- pedido do
                            // usuário ("tem que especificar qual é a
                            // operação assim como a plataforma").
                            Text(
                                buildString {
                                    append(if (e.detail != null) "${e.tableLabel} — ${e.detail}" else e.tableLabel)
                                    append(" · ${e.type}")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f).padding(end = 8.dp),
                            )
                            Text(formatEventTime(e.at), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

// Distingue "quantidade" (contagem simples: itens, operações,
// colaboradores) de "valor" (quantia em R$) -- pedido do usuário ("nos kpis
// coloque os valores a direita, as quantidades no meio e os textos nomes a
// esquerda"), mesmo padrão de alinhamento adotado em todo o app (ver
// DomainListScreen.kt).
private enum class KpiKind { QUANTIDADE, VALOR }
private data class Kpi(
    val label: String,
    val value: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val color: Color,
    val kind: KpiKind,
)

// Redesenho pedido pelo usuário ("melhore o visual dos kpis, não precisa
// ser padronizado a largura apenas altura, deixe mais homogêneo... deixe o
// visual mais limpo... esquerda texto, centro quantidades e direita
// valores... deixe espaço entre as linhas"): antes a altura só era igualada
// DENTRO da mesma fileira (IntrinsicSize.Min por Row), então a 1ª fileira
// podia ficar com altura diferente da 2ª -- agora `minLines = 2` no rótulo
// reserva o mesmo espaço em TODOS os cards, de qualquer fileira. O rótulo
// (texto) fica ancorado à esquerda, a coluna do meio mostra a quantidade (se
// aplicável) e a da direita mostra o valor em R$ (se aplicável) -- cada KPI
// só preenche a coluna que faz sentido pra ele, mas as 3 zonas ficam sempre
// na mesma posição em todo card.
@Composable
private fun KpiGrid(data: HomeData) {
    val kpis = listOf(
        Kpi("Em aberto (financeiro)", formatMoneyBrl(data.saldoFinanceiroAberto), Icons.Filled.AccountBalanceWallet, BrGreen, KpiKind.VALOR),
        Kpi("Itens no estoque", data.itensEstoque.toString(), Icons.Filled.Inventory2, BrYellow, KpiKind.QUANTIDADE),
        Kpi("Operações de safra em andamento", data.safrasAtivas.toString(), Icons.Filled.Eco, BrBlue, KpiKind.QUANTIDADE),
        Kpi("Colaboradores ativos", data.colaboradoresAtivos.toString(), Icons.Filled.Groups, BrGreen, KpiKind.QUANTIDADE),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        kpis.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { kpi ->
                    Card(modifier = Modifier.weight(1f)) {
                        Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(kpi.color.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(kpi.icon, contentDescription = null, tint = kpi.color, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            // Esquerda: nome do KPI.
                            Text(
                                kpi.label,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                minLines = 2,
                                lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.3f,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(6.dp))
                            // Centro: quantidade (contagem simples).
                            Box(modifier = Modifier.width(36.dp), contentAlignment = Alignment.Center) {
                                if (kpi.kind == KpiKind.QUANTIDADE) {
                                    Text(kpi.value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                }
                            }
                            // Direita: valor em R$.
                            Box(modifier = Modifier.widthIn(min = 4.dp), contentAlignment = Alignment.CenterEnd) {
                                if (kpi.kind == KpiKind.VALOR) {
                                    Text(
                                        kpi.value,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.End,
                                    )
                                }
                            }
                        }
                    }
                }
                // Fileira ímpar (última sobra 1 KPI): Spacer no lugar do 2º
                // card mantém a largura igual à fileira de cima, sem
                // "padronizar" a largura de propósito -- só evita o card
                // sozinho esticar até o fim.
                if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

// Clima, Câmbio e Cotações viviam num único card (WeatherCambioCard) --
// pedido do usuário ("clima câmbio e cotações estão dentro do mesmo bloco,
// desmembre-os"), agora 3 cards separados, mesma divisão de
// src/app/(app)/dashboard/page.tsx no site (Clima / Câmbio / Cotações
// agrícolas / Destaques, cada um seu próprio <Card>).
@Composable
private fun ClimaCard(clima: com.bragro.mobile.data.model.WeatherData, modifier: Modifier = Modifier.fillMaxWidth()) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Clima (agora)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row {
                Text("${clima.currentIcon} ${clima.currentTempC.toInt()}°C -- ")
                Text("máx ${clima.todayMaxC.toInt()}° / mín ${clima.todayMinC.toInt()}°")
            }
        }
    }
}

@Composable
private fun CambioCard(fx: com.bragro.mobile.data.model.FxRatesData, modifier: Modifier = Modifier.fillMaxWidth()) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Câmbio (agora)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row { Text("Dólar: "); Text(fx.usdBrl?.let { formatMoneyBrl(it) } ?: "—", fontWeight = FontWeight.Bold) }
            Row { Text("Euro: "); Text(fx.eurBrl?.let { formatMoneyBrl(it) } ?: "—", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun CotacoesCard(com: com.bragro.mobile.data.model.CommodityQuotesData, modifier: Modifier = Modifier.fillMaxWidth()) {
    // padding/spacedBy menores + bodySmall + maxLines=1 -- cada linha
    // ("Milho: R$ 60,40/sc 60kg") quebrava em 2 linhas na metade da largura
    // da tela e deixava o card muito alto (pedido do usuário: "coloque a
    // descrição de cada item em uma só linha" + "diminua a altura").
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Cotações agrícolas", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            listOfNotNull(com.soja, com.milho, com.sorgo).forEach { q ->
                Text(
                    "${q.nome}: ${formatMoneyBrl(q.valor)}/${q.unidade}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun DestaquesCard(data: HomeData, modifier: Modifier = Modifier.fillMaxWidth()) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Destaques", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "Cultura líder: ${data.culturaLider ?: "—"}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "Pedidos em atraso: ${data.pedidosAtrasados}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
