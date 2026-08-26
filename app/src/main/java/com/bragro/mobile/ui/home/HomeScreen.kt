package com.bragro.mobile.ui.home

import android.app.Application
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import com.bragro.mobile.ui.theme.Card
import com.bragro.mobile.ui.theme.appFieldColors
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
import androidx.compose.runtime.LaunchedEffect
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
import com.bragro.mobile.data.local.PendingSyncEntity
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
import com.bragro.mobile.data.repo.ConfigRepository
import com.bragro.mobile.data.repo.LogoUploadRepository
import com.bragro.mobile.data.repo.HomeRepository
import com.bragro.mobile.data.repo.NoticesRepository
import com.bragro.mobile.data.repo.NotificationsRepository
import com.bragro.mobile.data.repo.RecordRepository
import com.bragro.mobile.data.repo.WeatherRepository
import com.bragro.mobile.ui.domain.CulturaSelection
import com.bragro.mobile.ui.domain.CulturaSelectorButton
import com.bragro.mobile.ui.domain.FarmSelection
import com.bragro.mobile.ui.domain.FarmSelectorButton
import com.bragro.mobile.ui.domain.SafraSelection
import com.bragro.mobile.ui.domain.SafraSelectorButton
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
    private val configRepository = ConfigRepository(app)
    private val db = AppDatabase.get(app)

    var home = mutableStateOf<HomeData?>(null)
        private set
    // Janela do Canvas (30/60/90/180 dias) -- pedido do usuário ("implemente
    // nessa sequência no app nativo"), mesmo seletor do dashboard web. Fica
    // aqui (não é um filtro global persistido tipo Fazenda/Safra/Cultura)
    // porque só afeta o Canvas desta tela, igual ao site (query param
    // "janela", não cookie).
    var janela = mutableStateOf(60)
        private set
    var weather = mutableStateOf<WeatherResponse?>(null)
        private set
    var pendingCount = mutableStateOf(0)
        private set
    // Lista completa (não só a contagem) -- pedido do usuário ("poderia
    // saber ao clicar na frase quais são os lançamentos"), pro banner do
    // Início abrir um diálogo com o detalhe de cada pendência.
    var pendingItems = mutableStateOf<List<PendingSyncEntity>>(emptyList())
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
        viewModelScope.launch { recordRepository.observePending().collectLatest { pendingItems.value = it } }
        viewModelScope.launch { db.sessionDao().observe().collectLatest { session.value = it } }
        // Mostra o último retrato salvo assim que a tela abre (mesmo antes
        // da 1ª resposta de rede) -- é o que faz o Início aparecer offline
        // em vez de "Sem dados ainda" (pedido do usuário: "quanto ao
        // dashboard é possível colocá-lo para aparecer offline?"). Quando a
        // rede responde, refresh() abaixo sobrescreve com o dado ao vivo.
        viewModelScope.launch { homeRepository.observeCached().collectLatest { cached -> if (cached != null) home.value = cached } }
        refresh()
    }

    // context: precisa carregar FarmSelection/SafraSelection/CulturaSelection
    // (SharedPreferences) antes de ler `.selected.value` -- mesmo padrão já
    // usado em DomainListScreen.kt (LaunchedEffect(Unit) { FarmSelection.load(context) }).
    fun refresh(context: android.content.Context? = null) {
        if (loading.value) return
        loading.value = true
        if (context != null) {
            FarmSelection.load(context)
            SafraSelection.load(context)
            CulturaSelection.load(context)
        }
        viewModelScope.launch {
            val fetched = homeRepository.fetch(
                janela = janela.value,
                safra = SafraSelection.selected.value,
                cultura = CulturaSelection.selected.value,
                fazendaSelecionada = FarmSelection.selected.value,
            )
            if (fetched != null) {
                home.value = fetched
                lastUpdatedAt.value = System.currentTimeMillis()
            }
            loading.value = false
        }
        viewModelScope.launch { weather.value = weatherRepository.fetch() }
        // Listas suspensas/módulos/fazendas -- pedido do usuário ("atualiza
        // todas as listas suspensas de todos os módulos... faça para que
        // quando apagar desapareça também das listas suspensas
        // automaticamente"): antes só recarregava esse cache no login ou
        // tocando manualmente em "Sincronizar agora" (ver syncNow acima).
        // Agora acontece sozinho toda vez que o Início carrega (silencioso,
        // em paralelo, sem travar a tela -- os dados em cache continuam
        // aparecendo na hora, offline-first) -- assim uma exclusão feita em
        // Base de Dados (site ou app) some dos dropdowns na próxima vez que
        // o usuário abrir o app, sem precisar de nenhum passo manual.
        viewModelScope.launch {
            val s = db.sessionDao().get()
            if (s != null) configRepository.bootstrapAndCacheConfig(s.accessToken, s.refreshToken)
        }
    }

    /** Troca a janela do Canvas (30/60/90/180d) e refaz o fetch já com o
     * novo período -- mesmo efeito do clique nos links "30d/60d/90d/180d" do
     * site (dashboard/page.tsx), só que sem navegação (o app não tem URL). */
    fun setJanela(context: android.content.Context, dias: Int) {
        janela.value = dias
        refresh(context)
    }

    /** Chamado pelos seletores de fazenda/safra/cultura (FarmSelection/
     * SafraSelection/CulturaSelection) ao trocar a escolha -- refaz o fetch
     * do Canvas já filtrado pela nova seleção, mesmo efeito de trocar o
     * FarmSelector no cabeçalho do site (router.refresh()). */
    fun onFiltroGlobalChanged(context: android.content.Context) {
        refresh(context)
    }

    // BUG real corrigido -- pedido do usuário ("listas suspensas
    // desatualizadas... tem valores que apaguei na base de dados mas
    // continua aparecendo"; "o modulo cotações não apareceu"; "a logo do
    // cliente não aparece"): "Sincronizar agora" só chamava
    // recordRepository.syncAll() (fila de LANÇAMENTOS offline pendentes) --
    // NUNCA reexecutava o bootstrap (configRepository.bootstrapAndCacheConfig),
    // que é quem baixa e recacheia no Room a lista de módulos, as listas
    // suspensas e as fazendas. bootstrapAndCacheConfig só rodava mesmo no
    // LOGIN (ver AuthRepository.login) -- ou seja, não existia NENHUM jeito
    // de atualizar esse cache sem sair e entrar de novo na conta. Agora
    // "Sincronizar agora" recarrega os dois: os lançamentos pendentes E a
    // configuração/listas suspensas/fazendas, usando o token já salvo na
    // sessão (sem pedir senha de novo).
    fun syncNow() {
        if (syncing.value) return
        syncing.value = true
        viewModelScope.launch {
            recordRepository.syncAll()
            val s = db.sessionDao().get()
            if (s != null) configRepository.bootstrapAndCacheConfig(s.accessToken, s.refreshToken)
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

// Controle fino dos blocos da Início por membro -- pedido do usuário ("crie
// em acessos uma categoria da aba início para escolher o que cada
// responsável terá acesso no app... site e app nativo juntos"). allowedWidgets
// vem de /api/mobile/home (ver HomeData em Models.kt); null = backend antigo
// ou ninguém restringiu nada (mostra tudo, mesmo critério do backend).
private fun HomeData?.hasWidget(id: String): Boolean {
    val allowed = this?.allowedWidgets
    return allowed == null || allowed.contains(id)
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

// So o numero, sem o prefixo "R$" -- usado no KPI Cotações Grãos (ver
// CotacoesCard) pra separar o "R$" num Text de largura fixa proprio,
// alinhado igual em Soja/Milho/Sorgo. Antes o "R$" fazia parte da MESMA
// string formatada (formatMoneyBrl) right-aligned num box só -- como Soja
// tem 3 digitos inteiros (137) e Milho/Sorgo só 2 (57/46), o texto de
// Milho/Sorgo era mais curto e, alinhado à direita, o "R$" delas acabava
// mais deslocado que o de Soja. Sem mexer no valor/formatação em si, só
// isolando o prefixo numa coluna própria.
private fun formatMoneyNumberOnly(value: Double): String =
    NumberFormat.getNumberInstance(Locale("pt", "BR")).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }.format(value)

// Subtítulo do KPI "Fazendas cadastradas" -- soma da área (ha) de todas as
// fazendas do cadastro (mesmo campo areaTotalHa retornado por
// /api/mobile/home e /api/mobile/dashboard). Sem casas decimais forçadas
// (diferente de formatMoneyBrl) porque hectare não é moeda -- "1.234,5 ha"
// e "1.234 ha" são igualmente válidos, então usa no máximo 1 casa decimal.
private fun formatAreaHa(value: Double): String =
    NumberFormat.getNumberInstance(Locale("pt", "BR")).apply {
        maximumFractionDigits = 1
    }.format(value) + " ha"

// Variação percentual do câmbio (Dólar/Euro) -- pedido do usuário ("kpi
// câmbio, na linha dólar e na linha euro adicione ícone de variação e
// porcentagem"), mesmo formato usado no card de Cotações Grãos do site
// (TrendingUp/TrendingDown + "x,xx%").
private fun formatVariacaoPct(value: Double): String {
    val sign = if (value >= 0) "+" else ""
    return "$sign${String.format(Locale("pt", "BR"), "%.2f", value)}%"
}

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
// Antes uma lista fixa (BrGreen, BrYellow, BrBlue) calculada fora de
// contexto @Composable -- pedido do usuário ("coloque as cores das fontes
// preto/branco modo claro/escuro"): BrGreen e BrBlue crus não mudavam entre
// temas e ficavam com contraste baixo no Escuro (ver Theme.kt). Virou
// função @Composable pra poder usar MaterialTheme.colorScheme.primary/
// tertiary (que JÁ resolvem certo pros dois temas), mantendo só o amarelo
// (BrYellow) fixo -- ele tem contraste bom nos dois casos.
@Composable
private fun noticeTones() = listOf(MaterialTheme.colorScheme.primary, BrYellow, MaterialTheme.colorScheme.tertiary)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenDomain: (String) -> Unit,
    onLoggedOut: () -> Unit,
    // "Importar KML desta fazenda" -- FieldView tem tela própria (fora do
    // mecanismo genérico onOpenDomain/domainList), ver BRAgroNavHost.kt.
    onOpenFieldview: () -> Unit = {},
    viewModel: HomeViewModel = viewModel(),
) {
    val home by viewModel.home
    val weather by viewModel.weather
    val pending by viewModel.pendingCount
    val pendingItems by viewModel.pendingItems
    var pendingDialogOpen by remember { mutableStateOf(false) }
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
    val janelaCanvas by viewModel.janela

    // Fazenda selecionada no Canvas (círculos) -- réplica do
    // useState(fazendas[0]?.id ?? null) do site (canvas-view.tsx). Chave no
    // remember = a própria lista de fazendas do Canvas: assim que os dados
    // (re)carregam (troca de filtro global, refresh etc.) e a fazenda
    // selecionada não existe mais na nova lista, cai de volta pra primeira
    // -- evita ficar "presa" numa seleção que sumiu do resultado filtrado.
    val canvasFazendas = home?.canvas?.fazendas
    var selecionadaFazendaId by remember(canvasFazendas) {
        mutableStateOf(canvasFazendas?.firstOrNull()?.id)
    }

    // Refaz o fetch já com os filtros globais (Fazenda/Safra/Cultura)
    // carregados do SharedPreferences -- o refresh() do init{} do ViewModel
    // roda antes da Compose ter um Context pra ler essas seleções, então
    // esse primeiro carregamento pode vir sem filtro; este LaunchedEffect
    // corrige na sequência, assim que a tela monta (mesmo efeito de
    // FarmSelection.load(context) já usado em DomainListScreen.kt).
    LaunchedEffect(Unit) { viewModel.refresh(logoScreenContext) }

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
                    // Padding assimétrico -- pedido do usuário ("mova a logo
                    // bem no limite para o lado esquerdo para abrir espaço
                    // para a logo do cliente aparecer no lado direito"):
                    // start zerado (era 4dp, antes disso 16dp) deixa a logo
                    // BRAgro colada na borda esquerda de vez -- com mais um
                    // ícone no cabeçalho agora (filtro de fazenda), o espaço
                    // à direita ficou mais disputado, então cada dp que sai
                    // daqui ajuda o cluster de ícones + logo do cliente a
                    // não cortar. end tambem reduzido (16dp -> 8dp) pelo
                    // mesmo motivo, do outro lado.
                    // bottom reduzido de 4dp pra 2dp -- pedido do usuário
                    // ("diminua distância da logo para os ícones fazendas,
                    // safra e cultura"): a linha nova abaixo (Farm/Safra/
                    // Cultura) sobe mais perto da logo.
                    // CenterVertically (era Bottom) -- pedido do usuário
                    // ("coloque os ícones ao lado da logo, não é pra mexer
                    // no tamanho da logo"). Causa raiz encontrada (bug real,
                    // confirmado por captura de tela do usuário): o
                    // Modifier.height(150.dp) da logo abaixo forçava uma
                    // CAIXA de layout de 150dp de altura, mas a imagem
                    // visível dentro dela sempre foi limitada pela LARGURA
                    // (widthIn(max=190dp) -- o logo tem proporção larga/
                    // baixa, então o Fit escala pela largura primeiro),
                    // renderizando de verdade só uns 50-57dp -- o resto
                    // (quase 100dp) era espaço vazio invisível dentro da
                    // própria caixa da logo. Como o cluster de ícones se
                    // alinhava pela BASE dessa caixa de 150dp, ele ficava
                    // ~90dp abaixo da logo visível, mesmo os dois estando na
                    // mesma Row -- exatamente o "ícones abaixo da logo" que
                    // a captura mostrou. Com o height(150dp) removido (ver
                    // comentário na Image abaixo), a caixa da logo agora
                    // encolhe pro tamanho real renderizado, e os dois ficam
                    // genuinamente lado a lado com CenterVertically.
                    modifier = Modifier.fillMaxWidth().padding(start = 0.dp, end = 8.dp, top = 4.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // ATENÇÃO -- height(150.dp) REMOVIDO de propósito (não é
                    // redução de tamanho, ver comentário no Row acima): o
                    // tamanho VISÍVEL da logo não muda nem um pixel, porque
                    // ele sempre foi determinado pelo widthIn(max=190dp), não
                    // pela altura -- só a caixa de layout invisível ao redor
                    // dela encolhe pra bater com o tamanho real renderizado.
                    // Histórico de tentativas de aumentar a logo (96dp ->
                    // 120dp -> 100dp -> 112dp -> 128dp -> 150dp) documentado
                    // aqui pra registro -- na prática, qualquer valor de
                    // altura acima do que os 190dp de largura permitem nunca
                    // teve efeito visual nenhum (era só caixa vazia).
                    Image(
                        painter = painterResource(R.drawable.logo_bragro),
                        contentDescription = "BRAgro",
                        // widthIn(max) evita que a logo, sozinha, já tome
                        // metade da tela em telas estreitas -- bug real
                        // encontrado ("logo do cliente não aparece"): com
                        // muitos ícones no cabeçalho, a soma das larguras
                        // passava da tela e o ÚLTIMO item (logo do cliente /
                        // botão "+") ficava cortado fora da área visível,
                        // sem nenhum aviso. O cluster de ícones ainda rola
                        // horizontalmente (Row.horizontalScroll abaixo) como
                        // rede de segurança, mesmo com mais espaço agora.
                        // Sem .height(150.dp) -- pedido do usuário ("ícones
                        // ao lado da logo, não é pra mexer no tamanho da
                        // logo"): removido, mas o tamanho VISÍVEL não muda
                        // (ver comentário longo no Row acima) -- só a caixa
                        // de layout invisível ao redor da logo encolhe.
                        modifier = Modifier.widthIn(max = 190.dp),
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    // Cluster de ícones do cabeçalho com rolagem horizontal --
                    // mesma correção do bug acima: mesmo que a soma das
                    // larguras dos ícones não caiba na tela (aparelhos mais
                    // estreitos, ou fontes/densidade maiores), nada fica
                    // permanentemente inacessível -- o usuário arrasta pra ver
                    // o restante, mas o botão de logo do cliente nunca some.
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
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
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
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
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
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
                    } // fecha Row de rolagem horizontal do cluster de ícones
                // Fazenda/Safra/Cultura saíram daqui -- pedido do usuário
                // ("transfira para baixo do slogan os ícones fazenda, safra
                // cultura"): agora ficam dentro do item "greeting" da
                // LazyColumn, logo abaixo do slogan ("Conectando a força da
                // nossa terra..."), ver mais abaixo neste arquivo. Sem essa
                // linha aqui, o divisor volta a ficar colado na logo.
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

        if (pendingDialogOpen) {
            PendingSyncDialog(items = pendingItems, onDismiss = { pendingDialogOpen = false })
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            // Topo reduzido de 6dp pra 2dp -- pedido do usuário ("diminua a
            // distância consideravelmente de 'olá bom dia' para a logo"):
            // "Olá, bem-vindo" sobe bem mais perto da linha divisória/logo
            // acima. Ajudou também remover a linha de fazenda/safra/cultura
            // que antes ficava entre a logo e essa linha (ver comentário no
            // Row do TopAppBar acima) -- já reduziu boa parte da distância
            // sozinho, essa mudança aqui é o resto.
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 0.dp, bottom = 12.dp),
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
                        // BrGreen (fixo) -> colorScheme.primary (adapta por
                        // tema) -- pedido do usuário ("coloque as cores das
                        // fontes preto/branco modo claro/escuro"): BrGreen
                        // cru ficava quase ilegível no fundo quase-preto do
                        // modo Escuro (ver Theme.kt).
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    if (pending > 0) {
                        // Virou um banner destacado (ícone + fundo tonal em
                        // âmbar) em vez de texto simples -- pedido do
                        // usuário (apontou esse aviso numa captura de tela
                        // como algo que precisa chamar mais atenção).
                        // Clicável agora -- pedido do usuário ("poderia
                        // saber ao clicar na frase quais são os
                        // lançamentos"): abre um diálogo com o módulo, tipo
                        // de operação e horário de cada pendência na fila.
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .background(BrOrange.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                .clickable { pendingDialogOpen = true }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.CloudSync, contentDescription = null, tint = BrOrange, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "$pending lançamento(s) aguardando conexão para sincronizar. Toque para ver quais.",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = BrOrange,
                            )
                        }
                        // Task #124 (conflito de sync) -- item(ns) da fila
                        // que o backend recusou com 409 CONFLICT (outro
                        // aparelho editou o MESMO lançamento antes deste
                        // sincronizar) NÃO entram mais no retry automático
                        // (ver RecordRepository.syncAll/hasPending) -- sem
                        // este segundo aviso, o problema ficaria invisível
                        // pro usuário (o lançamento simplesmente nunca
                        // sincronizaria, sem explicação nenhuma na tela).
                        // Cor de erro (mais forte que o âmbar de "pendente
                        // normal" acima) porque isso não se resolve só
                        // esperando conexão -- precisa de uma ação do
                        // usuário.
                        if (pendingItems.any { it.conflictMessage != null }) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp)
                                    .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(8.dp))
                                    .clickable { pendingDialogOpen = true }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Filled.WarningAmber,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Um lançamento foi alterado em outro dispositivo — abra e confira antes de tentar salvar de novo.",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            }
                        }
                    }
                }
            }

            if (data == null) {
                item(key = "empty") {
                    Card(modifier = Modifier.fillMaxWidth(), border = BorderStroke(0.dp, Color.Transparent)) {
                        Text(
                            if (loading) "Carregando..." else "Sem dados ainda. Conecte-se à internet e atualize.",
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
                return@LazyColumn
            }

            // Sequência do Canvas (site) portada pro app -- pedido do
            // usuário ("implemente nessa sequencia no app nativo depois de
            // mural de avisos pra cima implemente nesta sequencia da
            // plataforama"): filtros (agora em pill, ver FarmSelectorButton/
            // GlobalFieldSelectorButton) → círculos das fazendas → card de
            // detalhe (Custo médio/ha) → estágio da safra/janela → captura
            // rápida → sugestão adaptativa, TODOS antes do Mural de Avisos,
            // na mesma ordem do dashboard web (page.tsx).
            // Cada bloco da Início abaixo agora pode ser ligado/desligado
            // por membro (pedido do usuário, ver Segurança e Acessos no
            // site -- vale pros dois, site e app) -- data.hasWidget() lê
            // allowedWidgets vindo de /api/mobile/home; null (backend
            // antigo ou ninguém restringiu nada) = mostra tudo.
            if (data.hasWidget("inicio.filtros")) {
            item(key = "filtros-canvas") {
                // Duas linhas em vez de uma só -- pedido do usuário ("os
                // filtros e o importar kml estão ultrapassando o limite da
                // tela, distribua de forma limpa abaixo"): com os 3 pills +
                // botão KML todos numa única Row com horizontalScroll, o
                // botão ficava colado na borda direita ao rolar (sem
                // padding/respiro), parecendo cortado. O botão "Importar
                // KML" agora ocupa uma linha própria abaixo dos pills.
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FarmSelectorButton(asPill = true, onChanged = { viewModel.onFiltroGlobalChanged(logoScreenContext) })
                        SafraSelectorButton(asPill = true, onChanged = { viewModel.onFiltroGlobalChanged(logoScreenContext) })
                        CulturaSelectorButton(asPill = true, onChanged = { viewModel.onFiltroGlobalChanged(logoScreenContext) })
                    }
                    // "Importar KML" saiu daqui -- pedido do usuário ("dentro
                    // do bloco de fazendas coloque o botão importar kml
                    // dentro do bloco"): agora mora dentro do card de
                    // círculos das fazendas (CanvasCirclesRow, ver
                    // CanvasSection.kt), logo abaixo neste mesmo item.
            }
            }
            if (data.hasWidget("inicio.canvas")) {
            data.canvas?.let { canvas ->
                item(key = "canvas-circles") {
                    CanvasCirclesRow(
                        fazendas = canvas.fazendas,
                        selectedId = selecionadaFazendaId,
                        onSelect = { id -> selecionadaFazendaId = id },
                        onImportKml = onOpenFieldview,
                        safra = SafraSelection.selected.value,
                        cultura = CulturaSelection.selected.value,
                    )
                }
                val fazendaSelecionada = canvas.fazendas.find { it.id == selecionadaFazendaId }
                if (fazendaSelecionada != null) {
                    item(key = "canvas-detail") { CanvasDetailCard(fazendaSelecionada) }
                }
            }
            }
            // Bloco "Estagio da safra + Sugestao" REMOVIDO da Início --
            // pedido do usuario ("não é necessário o bloco no dashboard do
            // estágio e pragas na plataforma e no app native"): mesma
            // remocao ja feita no site (dashboard/page.tsx) -- a sugestao da
            // fase "vegetativo" apontava pra Pragas, e nem esse card nem o
            // de Estagio sao necessarios na Início nos dois lados agora.
            // EstagioSugestaoCard (CanvasSection.kt) fica sem uso aqui --
            // nao apagado do arquivo pra nao arriscar quebrar outro call
            // site sem confirmar, mas pode ser removido numa faxina futura
            // se ninguem mais chamar.

            if (data.hasWidget("inicio.mural")) {
            item(key = "mural") { BulletinBoardCard(data.notices, canManage, viewModel) }
            }
            if (data.hasWidget("inicio.alertas")) {
            item(key = "alertas") { AlertsCard(data.alerts, onOpenDomain) }
            }
            // Revertido -- pedido do usuário ("os kpis estão dentro do
            // monitor em tempo real, volte eles para a posição original"):
            // dentro do Monitor os cards ficavam espremidos (Card dentro de
            // Card, menos largura disponível) e os rótulos cortavam
            // ("Itens no e...", "Operaç...", "Colabor..."). Volta a ser
            // seção própria, largura cheia.
            if (data.hasWidget("inicio.monitor")) {
            item(key = "monitor") { ActivityMonitorCard(data.recentActivity, onOpenDomain) }
            }
            if (data.hasWidget("inicio.kpis")) {
            item(key = "kpis") { KpiGrid(data) }
            }
            // Clima ao lado de Câmbio, Cotações ao lado de Destaques -- cada
            // par em blocos separados (Card) lado a lado, pedido do usuário
            // ("coloque câmbio ao lado de clima separados por blocos"). Cada
            // um agora também liga/desliga independente.
            val clima = weather?.weather
            val fx = weather?.fx
            val showClima = data.hasWidget("inicio.clima") && clima != null
            val showCambio = data.hasWidget("inicio.cambio") && fx != null
            if (showClima || showCambio) {
                item(key = "clima-cambio") {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (showClima) ClimaCard(clima!!, onRefresh = { viewModel.refresh() }, modifier = Modifier.weight(1f).fillMaxHeight())
                        if (showCambio) CambioCard(fx!!, onRefresh = { viewModel.refresh() }, modifier = Modifier.weight(1f).fillMaxHeight())
                    }
                }
            }
            // Fazendas cadastradas ao lado de Destaques, dividindo o bloco --
            // pedido do usuário ("realoque o kpi destaques, para o lado de
            // fazendas cadastradas, dividindo os blocos"). Fazendas
            // cadastradas saiu do grid 2x2 de KpiGrid (ver `fazendasKpi`
            // acima) pra poder formar essa dupla aqui. Sem toggle próprio no
            // site (o dashboard web não tem mais esse KPI) -- amarrado ao
            // MESMO toggle "inicio.destaques" por simplicidade.
            if (data.hasWidget("inicio.destaques")) {
            item(key = "fazendas-destaques") {
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    KpiCard(fazendasKpi(data), modifier = Modifier.weight(1f).fillMaxHeight(), fillHeight = true)
                    DestaquesCard(data, viewModel.lastUpdatedAt.value, modifier = Modifier.weight(1f).fillMaxHeight())
                }
            }
            }
            // Cotações Grãos agora ocupa a linha inteira, até o limite da
            // tela -- pedido do usuário ("expanda kpi cotações grãos até o
            // limite da tela"), separado de Destaques (que foi pro lado de
            // Fazendas cadastradas acima).
            if (data.hasWidget("inicio.cotacoes")) {
            item(key = "cotacoes") {
                weather?.commodities?.let { CotacoesCard(it, onRefresh = { viewModel.refresh() }, modifier = Modifier.fillMaxWidth()) }
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
                                Text(
                                    n.titulo,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (!n.lida) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("· ${n.tipo}", style = MaterialTheme.typography.labelSmall, maxLines = 1)
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

// Nome amigável de cada módulo pro diálogo de pendências -- mesma lista de
// domainId usada em toda a base (ver DomainVisuals.kt), só que aqui como
// rótulo pra exibição, não pra ícone/agrupamento.
private val PENDING_SYNC_DOMAIN_LABELS: Map<String, String> = mapOf(
    "financeiro" to "Financeiro",
    "safra" to "Safra",
    "planejamentosafra" to "Planejamento de Safra",
    "colheita" to "Colheita",
    "romaneios" to "Romaneios",
    "receituarios" to "Receituários",
    "pragas" to "Pragas",
    "clima" to "Clima",
    "estoque" to "Estoque",
    "inventario" to "Inventário",
    "frota" to "Frota",
    "controleinterno" to "Controle Interno",
    "rh" to "RH",
    "pedidos" to "Pedidos",
    "contratos" to "Contratos",
    "caixainterno" to "Caixa Interno",
    "cobrancas" to "Cobranças",
    "nfse" to "NFS-e",
)

/** "kind" salvo em PendingSyncEntity ("create"/"update"/"delete") -> texto
 * legível na lista de pendências. */
private fun pendingSyncKindLabel(kind: String): String = when (kind) {
    "create" -> "Novo lançamento"
    "update" -> "Edição"
    "delete" -> "Exclusão"
    else -> kind
}

/** Diálogo do banner "N lançamento(s) aguardando conexão" -- pedido do
 * usuário ("poderia saber ao clicar na frase quais são os lançamentos"):
 * lista cada item da fila local (PendingSyncEntity) com módulo, tipo de
 * operação, horário em que foi salvo offline e o motivo do último erro de
 * sincronização, se já tentou e falhou (ultimoErro, preenchido pelo
 * RecordRepository.trySyncOne). Sem isso, o usuário só via um número sem
 * explicação de por quê ainda está pendente. */
@Composable
private fun PendingSyncDialog(items: List<PendingSyncEntity>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Lançamentos aguardando sincronizar") },
        text = {
            if (items.isEmpty()) {
                Text("Nenhum lançamento pendente.", style = MaterialTheme.typography.bodySmall)
            } else {
                Column(
                    modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        "Esses lançamentos foram salvos neste aparelho, mas ainda não foram confirmados pelo servidor -- geralmente por falta de conexão, mas também pode ser sessão expirada ou algum erro no envio (ver abaixo).",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    items.forEach { item ->
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    PENDING_SYNC_DOMAIN_LABELS[item.domainId] ?: item.domainId,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("· ${pendingSyncKindLabel(item.kind)}", style = MaterialTheme.typography.labelSmall)
                            }
                            Text(formatUpdatedAt(item.criadoEmMillis), style = MaterialTheme.typography.labelSmall)
                            if (item.ultimoErro != null) {
                                Text(
                                    "Erro: ${item.ultimoErro}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                            // Task #124 (conflito de sync) -- distinto de
                            // ultimoErro acima: este item PAROU de ser
                            // tentado automaticamente (ver
                            // RecordRepository.syncAll/hasPending), só
                            // resolve com o usuário abrindo o lançamento no
                            // módulo e decidindo o que fazer.
                            if (item.conflictMessage != null) {
                                Text(
                                    "Conflito: ${item.conflictMessage}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } },
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

    Card(modifier = Modifier.fillMaxWidth(), border = BorderStroke(0.dp, Color.Transparent)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                // Ícone estilo KPI (badge circular colorido) no cabeçalho --
                // pedido do usuário ("coloque um ícone como nos kpis abaixo
                // nos blocos mural de avisos central de alertas").
                SectionBadgeIcon(Icons.Filled.Campaign, MaterialTheme.colorScheme.tertiary)
                Spacer(Modifier.width(8.dp))
                Box(modifier = Modifier.weight(1f)) {
                    CollapsibleHeader("Mural de Avisos (${notices.size})", expanded) { expanded = !expanded }
                }
                // Botão de adicionar aviso -- o app antes não tinha NENHUM
                // jeito de publicar um aviso (só o site tinha), pedido
                // explícito do usuário. Só OWNER/ADMIN vê, mesma regra do site.
                // Tamanho fixo em 28.dp (mesmo do botão excluir aviso, linha
                // ~890) -- pedido do usuário ("altura do bloco mural igual ao
                // bloco central de alertas"): sem isso, o IconButton padrão
                // (48.dp de área de toque) deixava o cabeçalho do Mural mais
                // alto que o de Alertas (que não tem esse botão extra),
                // fazendo os dois cards ficarem com altura diferente quando
                // recolhidos.
                if (canManage) {
                    IconButton(onClick = { showAddDialog = true }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.Add, contentDescription = "Adicionar aviso", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            if (expanded) {
                if (notices.isEmpty()) {
                    Text("Nenhum aviso publicado.", style = MaterialTheme.typography.bodySmall)
                } else {
                    val tones = noticeTones()
                    notices.forEachIndexed { i, n ->
                        val tone = tones[i % tones.size]
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
                OutlinedTextField(value = titulo, onValueChange = { titulo = it }, label = { Text("Título") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = appFieldColors())
                OutlinedTextField(value = mensagem, onValueChange = { mensagem = it }, label = { Text("Mensagem") }, modifier = Modifier.fillMaxWidth(), colors = appFieldColors())
                OutlinedTextField(
                    value = expiraEm,
                    onValueChange = { expiraEm = it },
                    label = { Text("Expira em (AAAA-MM-DD, opcional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth(),
                    colors = appFieldColors(),
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

// Badge circular colorido -- pedido do usuário ("tire todas as bordas de
// todo app"), que reverte a fase anterior (só contorno, sem preenchimento).
// Volta a ter fundo translúcido na própria cor do ícone (mesmo tom de antes
// da fase de "só borda") pra não ficar sem NENHUM contorno visível --
// mesmo critério aplicado em AppCard.kt (Cards) e nas pills de seletor
// (FarmSelectorButton.kt/GlobalFieldSelectorButton.kt).
@Composable
private fun SectionBadgeIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, size: androidx.compose.ui.unit.Dp = 28.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.14f)),
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
    Card(modifier = Modifier.fillMaxWidth(), border = BorderStroke(0.dp, Color.Transparent)) {
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
                            // fillMaxWidth() -> weight(1f) -- pedido do usuário
                            // ("limite a tela, não deixe nenhum caractere
                            // passar do limite da tela"): fillMaxWidth() aqui
                            // (2º filho do Row, depois do Icon de largura
                            // fixa) tentava ocupar a largura TOTAL do Row
                            // além do espaço já usado pelo ícone, estourando
                            // a borda direita da tela quando título/descrição
                            // era longo. weight(1f) faz a Column dividir só o
                            // espaço que sobra depois do ícone.
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    a.titulo,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    a.descricao,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
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
private fun ActivityMonitorCard(events: List<ActivityEventData>, onOpenDomain: (String) -> Unit) {
    // Fechado por padrão -- pedido do usuário ("os blocos mural, alertas e
    // monitor tem que aparecer fechados").
    var expanded by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth(), border = BorderStroke(0.dp, Color.Transparent)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Bolt, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
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
                            // Clicavel -- leva pro modulo de origem do
                            // evento (e.table ja e o mesmo id de rota
                            // usado em onOpenDomain, ver ACTIVITY_TABLE_
                            // LABEL/home/route.ts no backend), mesmo
                            // padrao ja usado na Central de Alertas
                            // (AlertsCard acima) -- pedido do usuario
                            // ("quando trocar em alguma movimentação ser
                            // direcionado para a tela correspondente").
                            modifier = Modifier.fillMaxWidth().clickable { onOpenDomain(e.table) }.padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Reordenado -- pedido do usuário: "tipo, item e
                            // quantidade, status". "Tipo" aqui é o campo
                            // `operacao` (tipo de lançamento/operação, ex.:
                            // "VENDA"/"COMPRA") -- é o que faz sentido
                            // semântico de "tipo" no contexto do monitor, e
                            // não o `type` (criado/atualizado), que é uma
                            // flag interna de origem do evento (INSERT/
                            // UPDATE). `tableLabel` (setor) continua como
                            // prefixo, dando o contexto de qual módulo é.
                            // `type` foi empurrado pra depois do status em
                            // vez de removido, pra não perder a informação
                            // de criado/atualizado (fica com menos destaque,
                            // no fim da linha) -- MESMO critério aplicado em
                            // paralelo no site (ver realtime-monitor.tsx),
                            // pra manter a ordem consistente nas duas
                            // plataformas. Cada pedaço só entra se a tabela
                            // daquele evento realmente tiver o campo.
                            Text(
                                buildString {
                                    append(e.tableLabel)
                                    if (!e.operacao.isNullOrBlank()) append(" · ${e.operacao}")
                                    if (!e.item.isNullOrBlank()) append(" · ${e.item}")
                                    if (!e.qtde.isNullOrBlank()) append(" · ${e.qtde}")
                                    if (!e.status.isNullOrBlank()) append(" · ${e.status}")
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
        // Estes 4 (Financeiro/Estoque/Safra/RH) formam o grid 2x2 abaixo --
        // o 5º KPI ("Fazendas cadastradas") foi separado da lista (ver
        // `kpiFazendas` mais abaixo) e passou a ocupar a linha cheia, fora
        // deste grid -- pedido do usuário ("expandir o card fazendas
        // cadastradas pra ocupar a linha toda"): antes ele entrava junto
        // nesta mesma lista de 5 e o `chunked(2)` deixava ele sozinho numa
        // 3ª fileira de 2 colunas, com a metade da linha vazia (Spacer).
        // Cores dos ícones Safra/Financeiro invertidas -- pedido do usuário
        // ("inverta as cores dos ícones dos kpis safra e financeiro").
        // Descrição mais explícita -- pedido do usuário ("no bloco kpi
        // financeiro tem que aparecer a descrição").
        Kpi(
            "Financeiro",
            formatMoneyBrl(data.saldoFinanceiroAberto),
            Icons.Filled.AccountBalanceWallet,
            // BrBlue (fixo) -> colorScheme.tertiary (adapta por tema) --
            // pedido do usuário ("coloque as cores das fontes preto/branco
            // modo claro/escuro"): BrBlue cru tinha baixo contraste no modo
            // Escuro (ver Theme.kt/BrBlueTertiaryDark).
            MaterialTheme.colorScheme.tertiary,
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
            MaterialTheme.colorScheme.primary,
            KpiKind.QUANTIDADE,
            description = "Lançamentos de safra em andamento",
        ),
        Kpi(
            "Colaboradores ativos",
            data.colaboradoresAtivos.toString(),
            Icons.Filled.Groups,
            MaterialTheme.colorScheme.primary,
            KpiKind.QUANTIDADE,
            description = "Total cadastrado no módulo RH",
        ),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        kpis.chunked(2).forEach { row ->
            // IntrinsicSize.Min + fillMaxHeight -- pedido do usuário
            // ("alinhe qualquer bloco... que não tiver a mesma altura ao
            // bloco do lado"): sem isso, se um KPI tivesse descrição maior
            // que o vizinho na mesma fileira, os 2 cards ficavam com
            // alturas diferentes.
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { kpi -> KpiCard(kpi, modifier = Modifier.weight(1f).fillMaxHeight(), fillHeight = true) }
                // Fileira ímpar (última sobra 1 KPI): Spacer no lugar do 2º
                // card mantém a largura igual à fileira de cima, sem
                // "padronizar" a largura de propósito -- só evita o card
                // sozinho esticar até o fim.
                if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
        // "Fazendas cadastradas" saiu daqui -- pedido do usuário ("realoque
        // o kpi destaques para o lado de fazendas cadastradas, dividindo os
        // blocos"): agora mora no item "fazendas-destaques" (ver HomeScreen
        // abaixo), lado a lado com Destaques, em vez de sozinho numa linha
        // cheia aqui dentro do grid.
    }
}

// 5º KPI -- espelha numeroFazendas/areaTotalHa, novos campos de
// /api/mobile/home e /api/mobile/dashboard (contagem de fazendas do
// cadastro + soma da área em hectares). Mesmo padrão visual dos outros 4
// (ícone com borda colorida, quantidade em destaque, legenda menor abaixo do
// nome) -- aqui a legenda mostra a área total em vez de um texto fixo, já
// que é a métrica-irmã da contagem de fazendas. Extraído de dentro de
// KpiGrid pra poder ser renderizado ao lado de Destaques (ver comentário no
// item "fazendas-destaques" em HomeScreen), em vez de dentro do grid.
private fun fazendasKpi(data: HomeData): Kpi = Kpi(
    "Fazendas cadastradas",
    data.numeroFazendas.toString(),
    Icons.Filled.Map,
    BrOrange,
    KpiKind.QUANTIDADE,
    description = "Área total: ${formatAreaHa(data.areaTotalHa)}",
)

// Corpo visual de um card de KPI -- extraído de dentro do loop de KpiGrid
// pra poder ser reaproveitado tanto nas 2 colunas do grid (Financeiro/
// Estoque/Safra/RH) quanto no card "Fazendas cadastradas" em largura cheia,
// fora do grid (ver comentário em KpiGrid).
// `fillHeight` -- pedido do usuário ("kpi financeiro alinhar o valor à linha
// do kpi estoque"): dentro da fileira de 2 colunas (KpiGrid, Row com
// IntrinsicSize.Min), os 2 cards já tinham a MESMA altura total -- mas
// quando a descrição de um KPI quebrava em mais linhas que a do vizinho
// (ex.: "Saldo em aberto (a receber − a pagar)" de Financeiro vs. "Total de
// itens cadastrados no estoque" de Estoque), o valor ficava mais embaixo num
// card que no outro, já que ele vinha logo depois da descrição no fluxo
// normal -- a altura "sobrando" da equalização ficava invisível no final do
// card mais curto, em vez de empurrar o valor pra baixo igual. Com
// `fillHeight = true` (só usado pelos 4 KPIs da fileira 2x2, que têm altura
// garantida pelo IntrinsicSize.Min do Row pai), o cabeçalho (ícone+
// label+descrição) vira `weight(1f)` dentro de uma Column com
// `fillMaxHeight()`, empurrando o valor pro rodapé do card -- agora sempre
// na MESMA linha em todo card da fileira, não importa quantas linhas a
// descrição ocupou. `kpiFazendas` (linha cheia, fora do grid, sem altura
// garantida por nenhum vizinho) continua com `fillHeight = false` (padrão) --
// aplicar `fillMaxHeight()` ali, sem um Row pai com IntrinsicSize.Min pra
// dar uma altura de verdade, quebraria o layout (altura infinita).
@Composable
private fun KpiCard(kpi: Kpi, modifier: Modifier = Modifier, fillHeight: Boolean = false) {
    // Reestruturado -- pedido do usuário ("resolva de uma vez por todas o
    // porque o kpi financeiro não consegue puxar as ,00"): a caixa do valor
    // (`widthIn(min=4dp)`, sem largura máxima) ficava competindo por espaço
    // com a Column(weight(1f)) do rótulo NA MESMA Row -- quando o valor era
    // longo (ex.: "R$ 1.164.566,00"), ele "roubava" toda a largura solta,
    // sobrando quase nada pro rótulo, que então quebrava em muitas linhas
    // (sem maxLines) e explodia a altura mínima da Row inteira (height =
    // IntrinsicSize.Min), estufando o card vazio -- exatamente o bug visto
    // no card do Financeiro. Agora o valor/quantidade fica numa linha
    // PRÓPRIA, com a largura TOTAL do card, sem nenhum vizinho disputando
    // espaço -- nunca mais é cortado nem força a altura de ninguém.
    Card(modifier = modifier, border = BorderStroke(0.dp, Color.Transparent)) {
        Column(modifier = Modifier.padding(12.dp).fillMaxWidth().let { if (fillHeight) it.fillMaxHeight() else it }) {
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.let { if (fillHeight) it.weight(1f) else it }) {
                // Fundo translúcido em vez de contorno -- pedido do usuário
                // ("tire todas as bordas de todo app"), mesmo critério de
                // SectionBadgeIcon acima.
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        kpi.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // Sem maxLines/ellipsis -- pedido do usuário ("aumente a
                    // altura pra caber todas as informações sem cortar"): a
                    // descrição quebra em quantas linhas precisar, e o Card
                    // cresce junto.
                    if (kpi.description != null) {
                        Text(
                            kpi.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            // Valor/quantidade em linha própria, largura cheia do card --
            // nunca mais compete por espaço com o rótulo, então nunca mais
            // corta o "R$" nem as casas decimais.
            Text(
                kpi.value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
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
    Card(modifier = modifier, border = BorderStroke(0.dp, Color.Transparent)) {
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
            // Periodicidade + fonte -- pedido do usuário ("coloque a
            // periodicidade que é atualizado e a fonte"). Valor real do
            // backend (getWeather em weather.ts): revalidate 1800s = 30
            // min, Open-Meteo.
            Text(
                "Atualizado a cada 30 min · Fonte: Open-Meteo",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CambioCard(fx: com.bragro.mobile.data.model.FxRatesData, onRefresh: () -> Unit = {}, modifier: Modifier = Modifier.fillMaxWidth()) {
    Card(modifier = modifier, border = BorderStroke(0.dp, Color.Transparent)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            MiniCardHeaderWithRefresh("Câmbio", Icons.Filled.CurrencyExchange, MaterialTheme.colorScheme.primary, MaterialTheme.typography.titleMedium, onRefresh)
            // Ícone de variação (alta/baixa) + porcentagem do dia ao lado do
            // valor -- pedido do usuário ("na linha dólar e na linha euro
            // adicione ícone de variação e porcentagem"), mesmo padrão já
            // usado nas cotações agrícolas do site (TrendingUp/TrendingDown
            // + "x,xx%"). Some sozinho quando a fonte não informa variação
            // (fallback exchangerate-api.com).
            // Rótulo com largura fixa + algarismos tabulares (tnum) no valor
            // -- pedido do usuário ("alinhe o R$ e alinhe as casas
            // decimais... é assim que quer a distribuição dos valores"):
            // com largura fixa no rótulo, o "R$" de todas as linhas começa
            // no mesmo X; com tnum, cada dígito ocupa a mesma largura, então
            // a vírgula/casas decimais também ficam alinhadas quando os
            // valores são empilhados. Mesmo padrão usado em CotacoesCard
            // logo abaixo, pra alinhar entre os dois KPIs também.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Dólar:", modifier = Modifier.width(52.dp))
                Text(
                    fx.usdBrl?.let { formatMoneyBrl(it) } ?: "—",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                )
                FxVariacaoTag(fx.usdVariacaoPct)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Euro:", modifier = Modifier.width(52.dp))
                Text(
                    fx.eurBrl?.let { formatMoneyBrl(it) } ?: "—",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                )
                FxVariacaoTag(fx.eurVariacaoPct)
            }
            // Periodicidade + fonte -- pedido do usuário. Valor real do
            // backend (getFxRates em quotes.ts): revalidate 900s = 15 min,
            // AwesomeAPI (com fallback pra exchangerate-api.com só se a
            // AwesomeAPI falhar).
            Text(
                "Atualizado a cada 15 min · Fonte: AwesomeAPI",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FxVariacaoTag(pct: Double?) {
    if (pct == null) return
    val positive = pct >= 0
    val color = if (positive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 6.dp)) {
        Icon(
            if (positive) Icons.Filled.TrendingUp else Icons.Filled.TrendingDown,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(14.dp),
        )
        Text(formatVariacaoPct(pct), style = MaterialTheme.typography.labelSmall, color = color)
    }
}

// Cotações e Destaques agora replicam o MESMO layout/tamanho de fonte do
// Câmbio -- pedido do usuário ("use o layout de câmbio para aplicar nos kpis
// cotações e destaques pulando uma linha, alternando com negrito e sem
// negrito"): título titleMedium, padding 16dp, uma Row por item com o rótulo
// sem negrito seguido do valor em negrito, cada um na sua própria linha.
@Composable
private fun CotacoesCard(com: com.bragro.mobile.data.model.CommodityQuotesData, onRefresh: () -> Unit = {}, modifier: Modifier = Modifier.fillMaxWidth()) {
    Card(modifier = modifier, border = BorderStroke(0.dp, Color.Transparent)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // Renomeado -- pedido do usuário ("altere o kpi cotações
            // agrícolas para cotações grãos").
            MiniCardHeaderWithRefresh("Cotações Grãos", Icons.Filled.Agriculture, MaterialTheme.colorScheme.primary, MaterialTheme.typography.titleMedium, onRefresh)
            val itens = listOfNotNull(com.soja, com.milho, com.sorgo)
            // Quantidade (unidade "sc" = saca) + variação/porcentagem do dia
            // ao lado do preço -- pedido do usuário ("insira a quantidade de
            // cada sc, os ícones de variações e a porcentagem"), mesmo
            // padrão já usado no Câmbio (FxVariacaoTag). Cabe numa linha só
            // agora que o card ocupa a largura inteira da tela (deixou de
            // dividir a linha com Destaques -- ver item "cotacoes" acima).
            // Distribuição melhor aproveitando a largura cheia do card --
            // pedido do usuário ("distribuir melhor as informações dentro
            // do bloco"): antes tudo ficava espremido à esquerda (nome +
            // preço + variação em sequência); agora o nome fica fixo à
            // esquerda e preço/variação vão para a ponta direita da linha,
            // usando o espaço extra que sobrou desde que o card passou a
            // ocupar a tela inteira (Task #68).
            // Row simples (rótulo + valor colados à esquerda), igual ao
            // Câmbio -- pedido do usuário ("alinhe o R$ do kpi cotações
            // grãos e câmbio na mesma altura"): o SpaceBetween anterior
            // empurrava o valor pra ponta direita do card, começando em X
            // diferente do "R$" de Dólar/Euro no Câmbio (que ficam colados
            // logo depois do rótulo). Voltando ao padrão inline dos dois,
            // o "R$" começa no mesmo ponto horizontal nos dois cards.
            // Reformulado -- pedido do usuário (layout exato: "Soja:    R$
            // 137,88 / 60kg / sacas", com variação+% em TODAS as linhas).
            // Antes o valor e a unidade ficavam num Text só, sem largura
            // fixa -- em nomes mais longos ("Milho:", "Sorgo:") o texto
            // ficava mais comprido que o de Soja e empurrava o ícone de
            // variação pra fora da largura do card (cortado/invisível,
            // dava a impressão de "faltar" variação em alguns itens).
            // Agora: rótulo com largura fixa (cabe "Milho:"/"Sorgo:" sem
            // quebrar), valor em R$ com largura fixa + alinhado à direita
            // (tnum já alinha os dígitos, mas números com 2 ou 3 casas
            // antes da vírgula agora também alinham entre si pela unidade),
            // e a unidade/variação com weight(fill=false) -- absorve o
            // texto "/60kg/sacas" truncando com "..." só se precisar, sem
            // nunca empurrar o ícone de variação (que é medido primeiro,
            // com prioridade, e sempre cabe).
            // Reescrito pra ser IDÊNTICO ao card do site (dashboard/page.tsx,
            // grid-cols-3 divide-x) -- pedido do usuário ("coloque o kpi
            // cotações grãos como está na plataforma sem abreviar nada").
            // A versão anterior (linhas horizontais Soja/Milho/Sorgo com
            // "/60kg/sacas" abreviado) foi trocada por 3 colunas verticais
            // lado a lado, cada uma com: ícone+rótulo, preço em R$ (sem
            // nenhuma unidade abreviada do lado -- o site não mostra
            // "/60kg/sacas" nesse card), variação%, e a praça de referência
            // POR EXTENSO (sem cortar/abreviar o nome da região), exatamente
            // como o site renderiza cada commodity.
            Row(modifier = Modifier.fillMaxWidth()) {
                itens.forEachIndexed { index, q ->
                    if (index > 0) {
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .padding(vertical = 2.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant)
                                .fillMaxHeight(),
                        )
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = if (index > 0) 8.dp else 0.dp),
                        verticalArrangement = Arrangement.spacedBy(1.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(
                                Icons.Filled.Eco,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(12.dp),
                            )
                            Text(
                                q.nome,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            "R$ ${formatMoneyNumberOnly(q.valor)}",
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                        )
                        val positivo = q.variacaoPct >= 0
                        val corVariacao = if (positivo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            Icon(
                                if (positivo) Icons.Filled.TrendingUp else Icons.Filled.TrendingDown,
                                contentDescription = null,
                                tint = corVariacao,
                                modifier = Modifier.size(12.dp),
                            )
                            Text(
                                formatVariacaoPct(q.variacaoPct),
                                style = MaterialTheme.typography.labelSmall,
                                color = corVariacao,
                            )
                        }
                        // Praça de referência por extenso, sem abreviar --
                        // mesmo campo "praca" que o site exibe (Grão Direto).
                        if (q.praca.isNotBlank()) {
                            Text(
                                q.praca,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            // Fonte -- pedido do usuário ("a fonte por exemplo cotações é o
            // Grão Direto"). Valor real do backend (getCommodityQuotes em
            // quotes.ts): só Grão Direto, lido 1x por semana
            // (GRAO_DIRETO_REVALIDATE); usa a data que a própria fonte
            // informou quando disponível, em vez de "agora". "Atualizado
            // semanalmente" removido -- pedido do usuário ("retire a
            // palavra atualizado semanalmente, suba uma linha do bloco"):
            // a frase toda nao cabia numa linha so e empurrava o card pra
            // 2 linhas de legenda, deixando Cotações mais alto que
            // Destaques (mesmo com o fillMaxHeight/IntrinsicSize.Min do Row
            // que envolve os dois, ver item "cotacoes-destaques" -- o
            // conteudo interno que sobrava). Com a legenda cabendo numa
            // linha so, os dois cards voltam a ficar com a mesma altura de
            // conteudo.
            val dataFonte = itens.firstNotNullOfOrNull { it.atualizadoEm }
            Text(
                if (dataFonte != null) "Fonte: Grão Direto ($dataFonte)" else "Fonte: Grão Direto",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DestaquesCard(data: HomeData, updatedAtMillis: Long?, modifier: Modifier = Modifier.fillMaxWidth()) {
    Card(modifier = modifier, border = BorderStroke(0.dp, Color.Transparent)) {
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
            // Sem info extra aqui -- pedido do usuário ("retire no kpi os
            // destaques que você colocou, não faz sentido, são informações
            // dos kpis que já tem na página inicial"): tanto o resumo de
            // módulos quanto Alertas/Avisos duplicavam cards que já existem
            // na tela (KPIs Financeiro/Estoque/Safra/RH e Central de
            // Alertas/Mural de Avisos) -- Destaques volta a mostrar só o
            // que é exclusivo dele. A altura já casa com o bloco ao lado
            // (Cotações) via IntrinsicSize.Min + fillMaxHeight no Row que
            // envolve os dois (ver item "cotacoes-destaques").
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
