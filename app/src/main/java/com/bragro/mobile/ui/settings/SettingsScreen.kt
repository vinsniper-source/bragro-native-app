package com.bragro.mobile.ui.settings

import android.app.Application
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Smartphone
import com.bragro.mobile.ui.theme.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bragro.mobile.data.repo.SettingsRepository
import com.bragro.mobile.ui.theme.BrGreen
import com.bragro.mobile.ui.util.openInCustomTab
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = SettingsRepository(app)

    var data = mutableStateOf<JsonObject?>(null)
        private set
    var loading = mutableStateOf(false)
        private set
    var saving = mutableStateOf(false)
        private set
    var errorMessage = mutableStateOf<String?>(null)
        private set

    fun load() {
        loading.value = true
        viewModelScope.launch {
            val result = repo.run("get")
            loading.value = false
            result.onSuccess { data.value = it?.jsonObject }.onFailure { errorMessage.value = it.message }
        }
    }

    fun saveOrg(name: String, toleranciaPct: Double, onDone: (Boolean) -> Unit) {
        saving.value = true
        viewModelScope.launch {
            val result = repo.run("update_org", name = name, toleranciaPct = toleranciaPct)
            saving.value = false
            result.onSuccess { load(); onDone(true) }.onFailure { errorMessage.value = it.message; onDone(false) }
        }
    }

    fun saveNotifications(
        telegramBotToken: String, telegramChatId: String,
        whatsappPhoneId: String, whatsappToken: String, whatsappTo: String,
        channelPush: Boolean, channelTelegram: Boolean, channelWhatsapp: Boolean,
        frotaManutencao: Boolean, romaneioDiario: Boolean, estoqueMinimo: Boolean,
        onDone: (Boolean) -> Unit,
    ) {
        saving.value = true
        viewModelScope.launch {
            val result = repo.run(
                "update_notifications",
                notifTelegramBotToken = telegramBotToken, notifTelegramChatId = telegramChatId,
                notifWhatsappPhoneId = whatsappPhoneId, notifWhatsappToken = whatsappToken, notifWhatsappTo = whatsappTo,
                notifChannelPush = channelPush, notifChannelTelegram = channelTelegram, notifChannelWhatsapp = channelWhatsapp,
                notifFrotaManutencao = frotaManutencao, notifRomaneioDiario = romaneioDiario, notifEstoqueMinimo = estoqueMinimo,
            )
            saving.value = false
            result.onSuccess { load(); onDone(true) }.onFailure { errorMessage.value = it.message; onDone(false) }
        }
    }

    fun sendTest(channel: String, onResult: (String?, Boolean) -> Unit) {
        viewModelScope.launch {
            val result = repo.run("send_test", channel = channel)
            result.onSuccess {
                val obj = it?.jsonObject
                onResult(obj?.get("mensagem")?.jsonPrimitive?.contentOrNull, obj?.get("ok")?.jsonPrimitive?.booleanOrNull ?: false)
            }.onFailure { onResult(it.message, false) }
        }
    }

    fun billingCheckout(plano: String, onUrl: (String?) -> Unit) {
        viewModelScope.launch {
            val result = repo.run("billing_checkout", plano = plano)
            result.onSuccess { onUrl(it?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull) }
                .onFailure { errorMessage.value = it.message; onUrl(null) }
        }
    }

    fun billingPortal(onUrl: (String?) -> Unit) {
        viewModelScope.launch {
            val result = repo.run("billing_portal")
            result.onSuccess { onUrl(it?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull) }
                .onFailure { errorMessage.value = it.message; onUrl(null) }
        }
    }
}

// Réplica 100% nativa de Configurações (src/app/(app)/configuracoes/) --
// pedido explícito e repetido do usuário ("não use nada para redirecionar,
// quero ele fixo nesse app"). As 2 únicas exceções que ABREM o navegador
// são os botões de Assinatura (Stripe Checkout/Portal): páginas de
// pagamento hospedadas são inerentemente web por exigência do próprio
// Stripe (PCI compliance) -- não dá pra reimplementar isso nativamente sem
// reconstruir um checkout de cartão do zero. Todo o resto (Organização,
// Notificações) é tela nativa de verdade.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = viewModel()) {
    LaunchedEffect(Unit) { viewModel.load() }
    val data by viewModel.data
    val loading by viewModel.loading
    val saving by viewModel.saving
    val error by viewModel.errorMessage
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                // Seta+título uma linha abaixo + fonte verde -- mesmo padrão
                // já usado nos módulos (DomainListScreen/DomainFormScreen),
                // agora replicado aqui e em todos os títulos do app (pedido
                // do usuário: "rebaixe a seta e o título... troque a cor por
                // verde... em todos os títulos do mobile").
                title = {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Configurações", color = MaterialTheme.colorScheme.primary)
                    }
                },
                navigationIcon = {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar", tint = MaterialTheme.colorScheme.primary) }
                    }
                },
            )
        },
    ) { padding ->
        val org = data?.get("org")?.jsonObject
        if (loading && data == null) {
            Column(modifier = Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.Center) {
                Text("Carregando...", modifier = Modifier.padding(24.dp))
            }
            return@Scaffold
        }
        LazyColumn(
            contentPadding = PaddingValues(12.dp, padding.calculateTopPadding() + 4.dp, 12.dp, 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (error != null) {
                item(key = "error") { Text(error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 4.dp)) }
            }
            // "Aplicativo mobile" (Android + iOS) -- pedido do usuário
            // ("configurações: coloque os blocos android e ios como está na
            // plataforma"): mesmos 2 blocos que já existiam no site
            // (src/app/(app)/configuracoes/configuracoes-client.tsx),
            // replicados aqui de verdade (não é redirecionamento), mesmo
            // padrão das outras seções desta tela.
            item(key = "app-android") { AppMobileAndroidCard(data?.get("appRelease")?.jsonObject) }
            item(key = "app-ios") { AppMobileIosCard() }
            item(key = "org") { OrgCard(org, saving) { name, tolerancia, onDone -> viewModel.saveOrg(name, tolerancia, onDone) } }
            item(key = "assinatura") {
                AssinaturaCard(
                    org = org,
                    stripeAtivo = data?.get("stripeAtivo")?.jsonPrimitive?.booleanOrNull ?: false,
                    onCheckout = { plano -> viewModel.billingCheckout(plano) { url -> if (url != null) openInCustomTab(context, url) } },
                    onPortal = { viewModel.billingPortal { url -> if (url != null) openInCustomTab(context, url) } },
                )
            }
            item(key = "notificacoes") {
                NotificacoesCard(
                    org = org,
                    saving = saving,
                    onSave = { t1, t2, w1, w2, w3, cp, ct, cw, fm, rd, em, onDone ->
                        viewModel.saveNotifications(t1, t2, w1, w2, w3, cp, ct, cw, fm, rd, em, onDone)
                    },
                    onSendTest = { channel, onResult -> viewModel.sendTest(channel, onResult) },
                )
            }
        }
    }
}

@Composable
private fun CollapsibleCard(title: String, icon: (@Composable () -> Unit)? = null, initiallyOpen: Boolean = false, content: @Composable () -> Unit) {
    var open by remember { mutableStateOf(initiallyOpen) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { open = !open },
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                icon?.invoke()
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = Modifier.weight(1f).padding(start = if (icon != null) 8.dp else 0.dp))
                Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = if (open) "Recolher" else "Expandir")
            }
            if (open) {
                Column(modifier = Modifier.padding(top = 12.dp)) { content() }
            }
        }
    }
}

// Réplica do card "Aplicativo mobile (Android)" do site -- mesma fonte de
// dados (prisma.appRelease, ver /api/mobile/settings -> getConfigDataFor),
// já incluída na resposta de "get" que este ViewModel já carrega. Fechado
// por padrão (initiallyOpen = false), mesmo padrão das demais seções.
@Composable
private fun AppMobileAndroidCard(appRelease: JsonObject?) {
    val context = LocalContext.current
    CollapsibleCard("Aplicativo mobile (Android)", icon = { Icon(Icons.Filled.Smartphone, contentDescription = null) }) {
        if (appRelease == null) {
            Text("Nenhuma versão publicada ainda.", style = MaterialTheme.typography.bodySmall)
        } else {
            val versao = appRelease["versao"]?.jsonPrimitive?.contentOrNull ?: "?"
            val publicadoEm = appRelease["publicadoEm"]?.jsonPrimitive?.contentOrNull
            val notas = appRelease["notas"]?.jsonPrimitive?.contentOrNull
            val apkUrl = appRelease["apkUrl"]?.jsonPrimitive?.contentOrNull
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Versão $versao" + (publicadoEm?.let { " — publicada em ${it.take(10).split("-").reversed().joinToString("/")}" } ?: ""),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (!notas.isNullOrBlank()) {
                        Text(notas, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp))
                    }
                    Text(
                        "Toque em Baixar pra abrir o instalador no navegador (fora da Play Store, pode pedir pra permitir \"fontes desconhecidas\").",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                if (!apkUrl.isNullOrBlank()) {
                    androidx.compose.material3.Button(onClick = { openInCustomTab(context, apkUrl) }, modifier = Modifier.padding(start = 8.dp)) {
                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                        Text("Baixar")
                    }
                }
            }
        }
    }
}

// Réplica do card "Aplicativo mobile (iPhone/iPad)" do site -- iOS não
// permite instalar um .apk (isso é coisa de Android); a única forma sem
// custo e sem Mac/conta de desenvolvedor Apple é o PWA via Safari
// ("Adicionar à Tela de Início"). Mesmo texto/passo a passo do site.
@Composable
private fun AppMobileIosCard() {
    CollapsibleCard("Aplicativo mobile (iPhone/iPad)", icon = { Icon(Icons.Filled.Smartphone, contentDescription = null) }) {
        Text(
            "O iOS não permite instalar um arquivo baixado como app — a Apple só libera isso pela App Store. O caminho que funciona sem custo, direto pelo Safari:",
            style = MaterialTheme.typography.bodySmall,
        )
        Column(modifier = Modifier.padding(top = 8.dp)) {
            listOf(
                "Abra o sistema no Safari (precisa ser o Safari -- outros navegadores no iPhone não mostram essa opção).",
                "Toque no ícone de Compartilhar, na barra inferior.",
                "Role a lista e toque em \"Adicionar à Tela de Início\".",
                "Toque em Adicionar -- o ícone aparece na tela como um app, com uso offline.",
            ).forEachIndexed { i, step ->
                Row(modifier = Modifier.padding(top = if (i == 0) 0.dp else 6.dp)) {
                    Text("${i + 1}.", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = Modifier.padding(end = 6.dp))
                    Text(step, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun OrgCard(org: JsonObject?, saving: Boolean, onSave: (String, Double, (Boolean) -> Unit) -> Unit) {
    var name by remember(org) { mutableStateOf(org?.get("name")?.jsonPrimitive?.contentOrNull ?: "") }
    var tolerancia by remember(org) { mutableStateOf((org?.get("toleranciaPct")?.jsonPrimitive?.doubleOrNull ?: 5.0).toString()) }

    CollapsibleCard("Organização") {
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nome") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(
            value = tolerancia,
            onValueChange = { tolerancia = it },
            label = { Text("Tolerância (%) para alertas de desvio") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            singleLine = true,
        )
        Spacer(Modifier.height(12.dp))
        androidx.compose.material3.Button(
            onClick = { onSave(name, tolerancia.toDoubleOrNull() ?: 5.0) { } },
            enabled = !saving,
        ) {
            if (saving) CircularProgressIndicator(modifier = Modifier.padding(2.dp)) else Text("Salvar")
        }
    }
}

@Composable
private fun AssinaturaCard(org: JsonObject?, stripeAtivo: Boolean, onCheckout: (String) -> Unit, onPortal: () -> Unit) {
    val planTier = org?.get("planTier")?.jsonPrimitive?.contentOrNull ?: "TRIAL"
    val subscriptionStatus = org?.get("subscriptionStatus")?.jsonPrimitive?.contentOrNull
    val hasStripeCustomer = !org?.get("stripeCustomerId")?.jsonPrimitive?.contentOrNull.isNullOrBlank()

    CollapsibleCard("Assinatura") {
        Text(
            "Plano atual: $planTier" + (subscriptionStatus?.let { " · status Stripe: $it" } ?: ""),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (stripeAtivo) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
                listOf("STARTER", "PRO", "ENTERPRISE").forEach { plano ->
                    OutlinedButton(onClick = { onCheckout(plano) }) { Text("Assinar $plano") }
                }
            }
            if (hasStripeCustomer) {
                androidx.compose.material3.Button(onClick = onPortal, modifier = Modifier.padding(top = 8.dp)) { Text("Gerenciar assinatura") }
            }
            Text(
                "Abre a página segura do Stripe pra pagamento -- é a única etapa deste app que sai pro navegador (exigência do próprio Stripe).",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        } else {
            Text(
                "Cobrança automática via Stripe ainda não configurada nesta instalação. Faturas continuam sendo lançadas manualmente pelo suporte.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun NotificacoesCard(
    org: JsonObject?,
    saving: Boolean,
    onSave: (String, String, String, String, String, Boolean, Boolean, Boolean, Boolean, Boolean, Boolean, (Boolean) -> Unit) -> Unit,
    onSendTest: (String, (String?, Boolean) -> Unit) -> Unit,
) {
    fun str(key: String) = org?.get(key)?.jsonPrimitive?.contentOrNull ?: ""
    fun bool(key: String, default: Boolean) = org?.get(key)?.jsonPrimitive?.booleanOrNull ?: default

    var telegramBotToken by remember(org) { mutableStateOf(str("notifTelegramBotToken")) }
    var telegramChatId by remember(org) { mutableStateOf(str("notifTelegramChatId")) }
    var whatsappPhoneId by remember(org) { mutableStateOf(str("notifWhatsappPhoneId")) }
    var whatsappToken by remember(org) { mutableStateOf(str("notifWhatsappToken")) }
    var whatsappTo by remember(org) { mutableStateOf(str("notifWhatsappTo")) }
    var channelPush by remember(org) { mutableStateOf(bool("notifChannelPush", true)) }
    var channelTelegram by remember(org) { mutableStateOf(bool("notifChannelTelegram", false)) }
    var channelWhatsapp by remember(org) { mutableStateOf(bool("notifChannelWhatsapp", false)) }
    var frotaManutencao by remember(org) { mutableStateOf(bool("notifFrotaManutencao", true)) }
    var romaneioDiario by remember(org) { mutableStateOf(bool("notifRomaneioDiario", true)) }
    var estoqueMinimo by remember(org) { mutableStateOf(bool("notifEstoqueMinimo", true)) }
    var testingChannel by remember { mutableStateOf<String?>(null) }
    var testResult by remember { mutableStateOf<String?>(null) }

    CollapsibleCard("Notificações automáticas") {
        Text(
            "Avisos automáticos de revisão de frota, fechamento diário de Romaneios e estoque mínimo. O sino do app sempre recebe; Telegram/WhatsApp são opcionais.",
            style = MaterialTheme.typography.bodySmall,
        )

        Column(modifier = Modifier.padding(top = 12.dp)) {
            CheckboxRow("Manutenção de frota", frotaManutencao) { frotaManutencao = it }
            CheckboxRow("Fechamento diário de Romaneios", romaneioDiario) { romaneioDiario = it }
            CheckboxRow("Itens de Estoque abaixo do mínimo", estoqueMinimo) { estoqueMinimo = it }
        }

        Column(modifier = Modifier.padding(top = 12.dp)) {
            CheckboxRow("Sino no app", channelPush) { channelPush = it }

            CheckboxRow("Telegram", channelTelegram) { channelTelegram = it }
            OutlinedTextField(
                value = telegramBotToken, onValueChange = { telegramBotToken = it },
                label = { Text("Bot Token") }, visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp), singleLine = true,
            )
            OutlinedTextField(
                value = telegramChatId, onValueChange = { telegramChatId = it },
                label = { Text("Chat ID") }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp), singleLine = true,
            )
            TextButton(
                onClick = { testingChannel = "telegram"; onSendTest("telegram") { msg, _ -> testingChannel = null; testResult = msg } },
                enabled = testingChannel == null,
            ) {
                Icon(Icons.Filled.Send, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                Text(if (testingChannel == "telegram") "Enviando..." else "Enviar teste (Telegram)")
            }

            CheckboxRow("WhatsApp", channelWhatsapp) { channelWhatsapp = it }
            OutlinedTextField(
                value = whatsappPhoneId, onValueChange = { whatsappPhoneId = it },
                label = { Text("Phone Number ID") }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp), singleLine = true,
            )
            OutlinedTextField(
                value = whatsappToken, onValueChange = { whatsappToken = it },
                label = { Text("Token de acesso") }, visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp), singleLine = true,
            )
            OutlinedTextField(
                value = whatsappTo, onValueChange = { whatsappTo = it },
                label = { Text("Número de destino") }, placeholder = { Text("5511999999999") },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp), singleLine = true,
            )
            TextButton(
                onClick = { testingChannel = "whatsapp"; onSendTest("whatsapp") { msg, _ -> testingChannel = null; testResult = msg } },
                enabled = testingChannel == null,
            ) {
                Icon(Icons.Filled.Send, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                Text(if (testingChannel == "whatsapp") "Enviando..." else "Enviar teste (WhatsApp)")
            }
        }

        if (testResult != null) {
            Text(testResult!!, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
        }

        Spacer(Modifier.height(8.dp))
        androidx.compose.material3.Button(
            onClick = {
                onSave(
                    telegramBotToken, telegramChatId, whatsappPhoneId, whatsappToken, whatsappTo,
                    channelPush, channelTelegram, channelWhatsapp, frotaManutencao, romaneioDiario, estoqueMinimo,
                ) { }
            },
            enabled = !saving,
        ) {
            if (saving) CircularProgressIndicator(modifier = Modifier.padding(2.dp)) else Text("Salvar notificações")
        }
    }
}

@Composable
private fun CheckboxRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange, colors = androidx.compose.material3.CheckboxDefaults.colors(checkedColor = BrGreen))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
