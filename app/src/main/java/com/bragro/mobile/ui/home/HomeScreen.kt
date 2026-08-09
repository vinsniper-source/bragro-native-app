package com.bragro.mobile.ui.home

import android.app.Application
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material.icons.filled.Agriculture
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import com.bragro.mobile.ui.theme.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import com.bragro.mobile.data.repo.AvatarUploadRepository
import com.bragro.mobile.data.repo.BackupRepository
import com.bragro.mobile.data.repo.LogoUploadRepository
import com.bragro.mobile.data.repo.HomeRepository
import com.bragro.mobile.data.repo.NoticesRepository
import com.bragro.mobile.data.repo.NotificationsRepository
import com.bragro.mobile.data.repo.RecordRepository
import com.bragro.mobile.data.repo.WeatherRepository
import com.bragro.mobile.ui.theme.BrBlue
import com.bragro.mobile.ui.theme.BrGreen
import com.bragro.mobile.ui.theme.BrOrange
import com.bragro.mobile.ui.theme.BrYellow
import com.bragro.mobile.ui.theme.ThemeToggle
import com.bragro.mobile.ui.util.saveToDownloads
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
    private val logoUploadRepository = LogoUploadRepository(app)
    private val avatarUploadRepository = AvatarUploadRepository(app)
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
    var uploadingLogo = mutableStateOf(false)
        private set
    var uploadingAvatar = mutableStateOf(false)
        private set
    // Timestamp do último fetch AO VIVO (não do cache offline) -- pedido do
    // usuário ("implemente também em destaques atualização: data e hora").
    var lastUpdatedAt = mutableStateOf<Long?>(null)
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
            val fetched = homeRepository.fetch()
            if (fetched != null) {
                home.value = fetched
                lastUpdatedAt.value = System.currentTimeMillis()
            }
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

    // Antes só abria o menu "Compartilhar" -- se o usuário fechasse sem
    // escolher nada, nada era realmente salvo no aparelho (pedido do
    // usuário: "o ícone nuvem... não está registando armazenamento").
    // Agora GRAVA de verdade na pasta Downloads primeiro (saveToDownloads),
    // e só then abre o compartilhar como opção extra -- onResult reporta se
    // a gravação em Downloads deu certo, pra tela mostrar um aviso.
    fun downloadBackup(onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val backup = backupRepository.fetch()
            if (backup == null) {
                onResult(false)
                return@launch
            }
            val fileName = "sistema-agro-backup-${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}.json"
            val app = getApplication<Application>()
            val saved = saveToDownloads(app, fileName, "application/json", backup.toString())
            shareTextFile(app, fileName, "application/json", backup.toString())
            onResult(saved)
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

    // Upload nativo da logo da organização direto pelo app -- pedido do
    // usuário ("quando clicar em adicionar a logo, coloque quando clicar a
    // adicionar a logo por lá mesmo"), substituindo o Toast que só
    // orientava a ir pro site. MESMO limite de 2MB do site
    // (topbar.tsx/LOGO_MAX_SIZE).
    fun uploadLogo(context: android.content.Context, uri: android.net.Uri, onResult: (String?) -> Unit) {
        if (uploadingLogo.value) return
        uploadingLogo.value = true
        viewModelScope.launch {
            val result = runCatching {
                val resolver = context.contentResolver
                val mimeType = resolver.getType(uri) ?: "image/jpeg"
                val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IllegalStateException("Não foi possível ler a imagem selecionada.")
                if (bytes.size > 2 * 1024 * 1024) throw IllegalStateException("Imagem maior que 2MB.")
                val ext = when (mimeType) {
                    "image/png" -> "png"
                    "image/svg+xml" -> "svg"
                    "image/webp" -> "webp"
                    else -> "jpg"
                }
                logoUploadRepository.uploadLogo(bytes, mimeType, ext).getOrThrow()
            }
            uploadingLogo.value = false
            onResult(result.getOrNull())
            if (result.isFailure) {
                val msg = result.exceptionOrNull()?.message ?: "Falha ao enviar a logo."
                android.widget.Toast.makeText(getApplication(), msg, android.widget.Toast.LENGTH_LONG).show()
            } else {
                android.widget.Toast.makeText(getApplication(), "Logo atualizada.", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Upload nativo da foto de perfil -- pedido do usuário ("coloque também
    // no ícone usuário a opção de inserir foto"), mesma mecânica do upload
    // de logo (mesmo limite de 2MB), só que qualquer usuário pode trocar a
    // própria foto (sem checagem de OWNER/ADMIN).
    fun uploadAvatar(context: android.content.Context, uri: android.net.Uri, onResult: (String?) -> Unit) {
        if (uploadingAvatar.value) return
        uploadingAvatar.value = true
        viewModelScope.launch {
            val result = runCatching {
                val resolver = context.contentResolver
                val mimeType = resolver.getType(uri) ?: "image/jpeg"
                val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IllegalStateException("Não foi possível ler a imagem selecionada.")
                if (bytes.size > 2 * 1024 * 1024) throw IllegalStateException("Imagem maior que 2MB.")
                val ext = when (mimeType) {
                    "image/png" -> "png"
                    "image/webp" -> "webp"
                    else -> "jpg"
                }
                avatarUploadRepository.uploadAvatar(bytes, mimeType, ext).getOrThrow()
            }
            uploadingAvatar.value = false
            onResult(result.getOrNull())
            if (result.isFailure) {
                val msg = result.exceptionOrNull()?.message ?: "Falha ao enviar a foto."
                android.widget.Toast.makeText(getApplication(), msg, android.widget.Toast.LENGTH_LONG).show()
            } else {
                android.widget.Toast.makeText(getApplication(), "Foto de perfil atualizada.", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
}

// minimum/maximumFractionDigits explícitos -- pedido do usuário ("no bloco
// kpi financeiro tem que aparecer... o duas casas ,00 depois da vírgula"),
// garante ",00" mesmo em valores redondos independente do locale/ICU do
// aparelho (antes dependia só do padrão do NumberFormat de moeda).
private fun formatMoneyBrl(value: Double): String =
    NumberFormat.getCurrencyInstance(Locale("pt", "BR")).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }.format(value)

private fun todayLongBrazil(): String {
    val fmt = SimpleDateFormat("EEEE, dd 'de' MMMM 'de' yyyy", Locale("pt", "BR"))
    return fmt.format(Date()).replaceFirstChar { it.uppercase() }
}

// Converte o "at"/"criadaEm" ISO (formato de Date.toISOString() do
// site/Prisma, sempre UTC) pra HH:mm no fuso do aparelho -- mesmo efeito de
// toLocaleTimeString("pt-BR") usado em realtime-monitor.tsx.
// Acrescenta dia/mês, não só hora -- pedido do usuário ("acrescente no
// monitor também dia/mês").
private fun formatEventTime(iso: String): String = try {
    val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
    parser.timeZone = TimeZone.getTimeZone("UTC")
    val date = parser.parse(iso)
    if (date == null) "" else SimpleDateFormat("dd/MM HH:mm", Locale("pt", "BR")).format(date)
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
    val uploadingLogo by viewModel.uploadingLogo
    val uploadingAvatar by viewModel.uploadingAvatar
    val logoScreenContext = LocalContext.current

    // Upload nativo da logo: abre o seletor de imagens do aparelho direto
    // (sem passar pelo site) -- pedido do usuário ("coloque quando clicar
    // adicionar a logo por lá mesmo").
    val logoPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) viewModel.uploadLogo(logoScreenContext, uri) { }
    }
    // Upload nativo da foto de perfil -- pedido do usuário ("coloque também
    // no ícone usuário a opção de inserir foto").
    val avatarPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) viewModel.uploadAvatar(logoScreenContext, uri) { }
    }

    Scaffold(
        topBar = {
            // Início tem regra diferente do resto do app: aqui a logo (e não
            // um título em texto) que salta uma linha, e os ícones saltam
            // junto, ficando todos na MESMA linha da logo -- pedido do
            // usuário ("a logo também deverá saltar uma linha para colocá-
            // la, os ícones saltam uma linha e ficam na mesma linha da
            // logo"). Sem TopAppBar vazia acima (ocupava uma linha em
            // branco) e sem bloco/preenchimento em volta -- pedido do
            // usuário ("suba uma linha junto com a logo e os ícones...
            // retire o bloco e o preenchimento e coloque um traço da mesma
            // tonalidade do modo claro/escuro embaixo e acima"); statusBar
            // ainda respeitada via statusBarsPadding, só sem a altura extra
            // do Material3 TopAppBar vazio.
            Column(modifier = Modifier.statusBarsPadding()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Logo nova, ainda maior que antes -- pedido do usuário
                    // repetiu ("aumente mais o tamanho da logo, login e
                    // início"). 96dp -> 120dp -> 100dp: 120dp deixava a logo
                    // larga demais (sem largura fixa, escala pela proporção
                    // da imagem) e empurrava o ícone da logo do cliente pra
                    // fora da tela, à direita -- pedido do usuário
                    // ("desloque a logo mais pra esquerda pro ícone da logo
                    // do cliente voltar a aparecer").
                    Image(
                        painter = painterResource(R.drawable.logo_bragro),
                        contentDescription = "BRAgro",
                        modifier = Modifier.height(100.dp),
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    // Ícone "Início" removido -- pedido do usuário ("retire a
                    // casinha"), era decorativo (onClick vazio, já estamos
                    // nesta tela).
                    if (canManage) {
                        IconButton(onClick = {
                            viewModel.downloadBackup { saved ->
                                val msg = if (saved) "Backup salvo em Downloads" else "Não foi possível salvar o backup"
                                android.widget.Toast.makeText(logoScreenContext, msg, android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }) {
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
                        IconButton(onClick = { userMenuOpen = true }, enabled = !uploadingAvatar) {
                            val avatarUrl = session?.avatarUrl
                            if (uploadingAvatar) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = BrGreen)
                            } else if (!avatarUrl.isNullOrBlank()) {
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
                            // Trocar foto direto pelo app -- pedido do
                            // usuário ("coloque também no ícone usuário a
                            // opção de inserir foto").
                            DropdownMenuItem(
                                text = { Text("Alterar foto") },
                                leadingIcon = { Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null) },
                                onClick = { userMenuOpen = false; avatarPickerLauncher.launch("image/*") },
                            )
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
                    // uma (orgLogoUrl). Sem logo cadastrada, quem é
                    // OWNER/ADMIN (isAdmin, ver topbar.tsx) vê um botão "+"
                    // convidando a cadastrar uma -- upload é feito DIRETO
                    // aqui pelo app agora (pedido do usuário: "quando clicar
                    // em adicionar a logo, coloque quando clicar a adicionar
                    // a logo por lá mesmo"), sem redirecionar pro site. Com
                    // logo já cadastrada, tocar nela também permite trocar
                    // (só pra quem pode gerenciar).
                    val orgLogoUrl = session?.orgLogoUrl
                    if (!orgLogoUrl.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .size(32.dp)
                                .then(if (canManage) Modifier.clickable(enabled = !uploadingLogo) { logoPickerLauncher.launch("image/*") } else Modifier),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (uploadingLogo) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = BrGreen)
                            } else {
                                AsyncImage(
                                    model = orgLogoUrl,
                                    contentDescription = "Logo da organização",
                                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp)),
                                )
                            }
                        }
                    } else if (canManage) {
                        // Sem círculo/borda -- pedido do usuário ("retire o
                        // círculo em volta do ícone da logo do cliente").
                        // Sem tint próprio -- pedido do usuário ("logo
                        // cliente acompanhar as cores dos outros ícones do
                        // cabeçalho"): antes usava BrYellow pra se destacar,
                        // agora usa a mesma cor padrão (LocalContentColor)
                        // dos demais ícones do TopAppBar (Backup,
                        // notificações, tema, conta).
                        IconButton(
                            enabled = !uploadingLogo,
                            onClick = { logoPickerLauncher.launch("image/*") },
                            modifier = Modifier.padding(end = 8.dp).size(32.dp),
                        ) {
                            if (uploadingLogo) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(
                                    Icons.Filled.AddPhotoAlternate,
                                    contentDescription = "Adicionar logo da empresa",
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        } else {
                            Box(modifier = Modifier.padding(end = 8.dp).size(32.dp))
                        }
                    }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
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
                        // Virou um banner destacado (ícone + fundo tonal em
                        // âmbar) em vez de texto simples -- pedido do
                        // usuário (apontou esse aviso numa captura de tela
                        // como algo que precisa chamar mais atenção).
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .background(BrOrange.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.CloudSync, contentDescription = null, tint = BrOrange, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "$pending lançamento(s) aguardando conexão para sincronizar.",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = BrOrange,
                            )
                        }
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
            // Revertido -- pedido do usuário ("os kpis estão dentro do
            // monitor em tempo real, volte eles para a posição original"):
            // dentro do Monitor os cards ficavam espremidos (Card dentro de
            // Card, menos largura disponível) e os rótulos cortavam
            // ("Itens no e...", "Operaç...", "Colabor..."). Volta a ser
            // seção própria, largura cheia.
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
                        if (clima != null) ClimaCard(clima, onRefresh = { viewModel.refresh() }, modifier = Modifier.weight(1f).fillMaxHeight())
                        if (fx != null) CambioCard(fx, onRefresh = { viewModel.refresh() }, modifier = Modifier.weight(1f).fillMaxHeight())
                    }
                }
            }
            item(key = "cotacoes-destaques") {
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    weather?.commodities?.let { CotacoesCard(it, onRefresh = { viewModel.refresh() }, modifier = Modifier.weight(1f).fillMaxHeight()) }
                    DestaquesCard(data, viewModel.lastUpdatedAt.value, modifier = Modifier.weight(1f).fillMaxHeight())
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
    // Fechado por padrão -- pedido do usuário ("os blocos mural, alertas e
    // monitor tem que aparecer fechados"), expande só ao tocar na setinha.
    var expanded by remember { mutableStateOf(false) }

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
                // Ícone estilo KPI (badge circular colorido) no cabeçalho --
                // pedido do usuário ("coloque um ícone como nos kpis abaixo
                // nos blocos mural de avisos central de alertas").
                SectionBadgeIcon(Icons.Filled.Campaign, BrBlue)
                Spacer(Modifier.width(8.dp))
                Box(modifier = Modifier.weight(1f)) {
                    CollapsibleHeader("Mural de Avisos (${notices.size})", expanded) { expanded = !expanded }
                }
                // Botão de adicionar aviso -- o app antes não tinha NENHUM
                // jeito de publicar um aviso (só o site tinha), pedido
                // explícito do usuário. Só OWNER/ADMIN vê, mesma regra do site.
                if (canManage) {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "Adicionar aviso", tint = BrGreen)
                    }
                }
            }
            if (expanded) {
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

// Badge circular colorido -- pedido do usuário ("retire o preenchimento
// verde e deixe apenas a borda em verde, em todo app"): era um círculo com
// fundo colorido (alpha 0.14), agora é só o contorno fino, sem
// preenchimento, mesmo critério da borda dos Cards (AppCard.kt).
@Composable
private fun SectionBadgeIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, size: androidx.compose.ui.unit.Dp = 28.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .border(1.dp, color, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(size * 0.55f))
    }
}

@Composable
private fun AlertsCard(alerts: List<AlertData>, onOpenDomain: (String) -> Unit) {
    // Fechado por padrão -- pedido do usuário ("os blocos mural, alertas e
    // monitor tem que aparecer fechados").
    var expanded by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                SectionBadgeIcon(Icons.Filled.WarningAmber, BrOrange)
                Spacer(Modifier.width(8.dp))
                Box(modifier = Modifier.weight(1f)) {
                    CollapsibleHeader("Central de Alertas (${alerts.size})", expanded) { expanded = !expanded }
                }
            }
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
    // Fechado por padrão -- pedido do usuário ("os blocos mural, alertas e
    // monitor tem que aparecer fechados").
    var expanded by remember { mutableStateOf(false) }
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
                            // Sequência pedida pelo usuário: setor, operação,
                            // tipo, item, quantidade, status, horas (horas
                            // fica na coluna da direita, ver Text abaixo).
                            // "setor" é o próprio nome do módulo (tableLabel)
                            // -- não há uma coluna "setor" comum às 6
                            // tabelas. Cada pedaço só entra se a tabela
                            // daquele evento realmente tiver o campo.
                            Text(
                                buildString {
                                    append(e.tableLabel)
                                    if (!e.operacao.isNullOrBlank()) append(" · ${e.operacao}")
                                    append(" · ${e.type}")
                                    if (!e.item.isNullOrBlank()) append(" · ${e.item}")
                                    if (!e.qtde.isNullOrBlank()) append(" · ${e.qtde}")
                                    if (!e.status.isNullOrBlank()) append(" · ${e.status}")
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
    // Legenda menor, numa 2ª linha, abaixo do nome -- pedido do usuário
    // ("ainda não há a descrição dentro do kpi"): antes só existia o
    // "label" (nome do KPI), sem nenhum texto explicativo separado.
    val description: String? = null,
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
        // Cores dos ícones Safra/Financeiro invertidas -- pedido do usuário
        // ("inverta as cores dos ícones dos kpis safra e financeiro").
        // Descrição mais explícita -- pedido do usuário ("no bloco kpi
        // financeiro tem que aparecer a descrição").
        Kpi(
            "Financeiro",
            formatMoneyBrl(data.saldoFinanceiroAberto),
            Icons.Filled.AccountBalanceWallet,
            BrBlue,
            KpiKind.VALOR,
            description = "Saldo em aberto (a receber − a pagar)",
        ),
        // Descrições adicionadas -- pedido do usuário ("melhore as
        // informações dentro do kpi"), mesmo padrão que o Financeiro já
        // tinha (nome + legenda curta explicando o número).
        Kpi(
            "Itens no estoque",
            data.itensEstoque.toString(),
            Icons.Filled.Inventory2,
            BrYellow,
            KpiKind.QUANTIDADE,
            description = "Total de itens cadastrados no estoque",
        ),
        Kpi(
            "Operações de safra",
            data.safrasAtivas.toString(),
            Icons.Filled.Eco,
            BrGreen,
            KpiKind.QUANTIDADE,
            description = "Lançamentos de safra em andamento",
        ),
        Kpi(
            "Colaboradores ativos",
            data.colaboradoresAtivos.toString(),
            Icons.Filled.Groups,
            BrGreen,
            KpiKind.QUANTIDADE,
            description = "Total cadastrado no módulo RH",
        ),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        kpis.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { kpi ->
                    Card(modifier = Modifier.weight(1f)) {
                        // Alignment.Top (não CenterVertically) -- pedido do
                        // usuário ("no kpi financeiro coloque a descrição e
                        // na mesma linha acrescente as duas casas depois da
                        // vírgula"): com o rótulo forçado em 2 linhas
                        // (minLines abaixo, pra manter os cards com a mesma
                        // altura), centralizar verticalmente jogava o valor
                        // pra entre as 2 linhas do rótulo -- alinhado no topo,
                        // o valor sempre fica ao lado da 1ª linha do rótulo.
                        Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.Top) {
                            // Só contorno, sem preenchimento -- pedido do
                            // usuário ("retire o preenchimento verde e deixe
                            // apenas a borda em verde, em todo app").
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .border(1.dp, kpi.color, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(kpi.icon, contentDescription = null, tint = kpi.color, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            // Esquerda: nome do KPI (linha 1) + descrição
                            // (linha 2, sempre presente -- mesmo espaço
                            // reservado em todo card, com ou sem descrição,
                            // pra manter a altura homogênea entre eles).
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    kpi.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    kpi.description ?: " ",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Spacer(Modifier.width(6.dp))
                            // Centro: quantidade (contagem simples).
                            Box(modifier = Modifier.width(36.dp), contentAlignment = Alignment.Center) {
                                if (kpi.kind == KpiKind.QUANTIDADE) {
                                    Text(kpi.value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                }
                            }
                            // Direita: valor em R$ -- sempre em UMA linha só,
                            // não importa o número de caracteres (pedido do
                            // usuário: "coloque o valor total em uma linha só
                            // independente do número de caracteres").
                            Box(modifier = Modifier.widthIn(min = 4.dp), contentAlignment = Alignment.CenterEnd) {
                                if (kpi.kind == KpiKind.VALOR) {
                                    Text(
                                        kpi.value,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.End,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Visible,
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
// Cabeçalho com badge de ícone + título, mesmo padrão em Clima/Câmbio/
// Cotações/Destaques -- pedido do usuário ("coloque ícones em clima, câmbio,
// cotações agrícolas e destaques").
@Composable
private fun MiniCardHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, titleStyle: androidx.compose.ui.text.TextStyle) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        SectionBadgeIcon(icon, color, size = 22.dp)
        Spacer(Modifier.width(6.dp))
        Text(title, style = titleStyle, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// Ícone de atualizar removido -- pedido do usuário ("não vejo ele girar
// quando aperto, se não girar, pode retirar o ícone e a palavra agora dos
// kpis"): o ícone nunca teve nenhuma animação de rotação (só disparava
// viewModel.refresh() em silêncio, sem feedback visual nenhum), então some
// daqui (e "(agora)"/"(ago...)" sai do título de Clima/Câmbio abaixo).
// `onRefresh` fica sem uso aqui de propósito -- as 3 chamadas (ClimaCard/
// CambioCard/CotacoesCard) continuam recebendo o parâmetro sem precisar
// mudar a assinatura delas nem os pontos onde são chamadas.
@Composable
private fun MiniCardHeaderWithRefresh(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    titleStyle: androidx.compose.ui.text.TextStyle,
    onRefresh: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SectionBadgeIcon(icon, color, size = 22.dp)
        Spacer(Modifier.width(6.dp))
        Text(title, style = titleStyle, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ClimaCard(clima: com.bragro.mobile.data.model.WeatherData, onRefresh: () -> Unit = {}, modifier: Modifier = Modifier.fillMaxWidth()) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            MiniCardHeaderWithRefresh("Clima", Icons.Filled.WbSunny, BrYellow, MaterialTheme.typography.titleMedium, onRefresh)
            // Temperatura atual centralizada, máx/mín centralizado na linha
            // debaixo -- pedido do usuário ("kpi clima colocar °C
            // centralizado e max °C/ min ºC na linha debaixo centralizado").
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "${clima.currentIcon} ${clima.currentTempC.toInt()}°C",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "máx ${clima.todayMaxC.toInt()}° / mín ${clima.todayMinC.toInt()}°",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CambioCard(fx: com.bragro.mobile.data.model.FxRatesData, onRefresh: () -> Unit = {}, modifier: Modifier = Modifier.fillMaxWidth()) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            MiniCardHeaderWithRefresh("Câmbio", Icons.Filled.CurrencyExchange, BrGreen, MaterialTheme.typography.titleMedium, onRefresh)
            Row { Text("Dólar: "); Text(fx.usdBrl?.let { formatMoneyBrl(it) } ?: "—", fontWeight = FontWeight.Bold) }
            Row { Text("Euro: "); Text(fx.eurBrl?.let { formatMoneyBrl(it) } ?: "—", fontWeight = FontWeight.Bold) }
        }
    }
}

// Cotações e Destaques agora replicam o MESMO layout/tamanho de fonte do
// Câmbio -- pedido do usuário ("use o layout de câmbio para aplicar nos kpis
// cotações e destaques pulando uma linha, alternando com negrito e sem
// negrito"): título titleMedium, padding 16dp, uma Row por item com o rótulo
// sem negrito seguido do valor em negrito, cada um na sua própria linha.
@Composable
private fun CotacoesCard(com: com.bragro.mobile.data.model.CommodityQuotesData, onRefresh: () -> Unit = {}, modifier: Modifier = Modifier.fillMaxWidth()) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // Renomeado -- pedido do usuário ("altere o kpi cotações
            // agrícolas para cotações grãos").
            MiniCardHeaderWithRefresh("Cotações Grãos", Icons.Filled.Agriculture, BrGreen, MaterialTheme.typography.titleMedium, onRefresh)
            listOfNotNull(com.soja, com.milho, com.sorgo).forEach { q ->
                Row {
                    Text("${q.nome}: ")
                    Text(
                        "${formatMoneyBrl(q.valor)}/${q.unidade}",
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun DestaquesCard(data: HomeData, updatedAtMillis: Long?, modifier: Modifier = Modifier.fillMaxWidth()) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            MiniCardHeader("Destaques", Icons.Filled.Star, BrYellow, MaterialTheme.typography.titleMedium)
            Row {
                Text("Cultura líder: ")
                Text(data.culturaLider ?: "—", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row {
                Text("Pedidos em atraso: ")
                Text(data.pedidosAtrasados.toString(), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            // Data/hora da última busca ao vivo -- pedido do usuário
            // ("implemente também em destaques atualização: data e hora").
            if (updatedAtMillis != null) {
                Text(
                    "Atualizado em ${formatUpdatedAt(updatedAtMillis)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

private fun formatUpdatedAt(millis: Long): String =
    SimpleDateFormat("dd/MM HH:mm", Locale("pt", "BR")).format(Date(millis))
