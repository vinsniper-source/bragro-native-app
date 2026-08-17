package com.bragro.mobile.ui.seguranca

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import com.bragro.mobile.ui.theme.Card
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bragro.mobile.data.repo.SecurityRepository
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private val ROLES = listOf("ADMIN", "AGRONOMO", "FINANCEIRO", "RH", "OPERADOR", "CUSTOM")
private val MODULES = listOf(
    "financeiro" to "Financeiro", "pedidos" to "Pedidos", "cotacoesfornecedores" to "Cotações de Fornecedores", "estoque" to "Estoque",
    "safra" to "Safra", "planejamentosafra" to "Planejamento de Safra", "colheita" to "Colheita",
    "romaneios" to "Romaneios", "contratos" to "Contratos", "inventario" to "Inventário (Ativos)",
    // Labels encurtados -- pedido do usuário (retirar "Recursos Humanos",
    // "e Doenças", "(EPI)", "(registro manual)", "(Serviços)").
    "frota" to "Frota", "rh" to "RH", "receituarios" to "Receituários",
    "pragas" to "Pragas", "controleinterno" to "Controle Interno",
    "clima" to "Clima", "caixainterno" to "Caixa Interno",
    "cobrancas" to "Cobranças", "nfse" to "NFS-e",
)

class SegurancaViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = SecurityRepository(app)

    var data = mutableStateOf<JsonObject?>(null)
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
            val result = repo.run("get")
            loading.value = false
            result.onSuccess { data.value = it?.jsonObject }.onFailure { errorMessage.value = it.message }
        }
    }

    fun invite(email: String, role: String, modulos: List<String>, onDone: (Boolean, String?) -> Unit) {
        busy.value = true
        viewModelScope.launch {
            val result = repo.run("invite", email = email, role = role, modulosPermitidos = modulos)
            busy.value = false
            result.onSuccess { load(); onDone(true, null) }.onFailure { onDone(false, it.message) }
        }
    }

    fun toggleMembership(id: String, ativo: Boolean) {
        busy.value = true
        viewModelScope.launch {
            val result = repo.run("toggle_membership", membershipId = id, ativo = ativo)
            busy.value = false
            result.onSuccess { load() }.onFailure { errorMessage.value = it.message }
        }
    }

    fun updateModules(id: String, role: String, modulos: List<String>) {
        busy.value = true
        viewModelScope.launch {
            val result = repo.run("update_membership_modules", membershipId = id, role = role, modulosPermitidos = modulos)
            busy.value = false
            result.onSuccess { load() }.onFailure { errorMessage.value = it.message }
        }
    }
}

// Réplica 100% nativa de Acessos/Segurança (src/app/(app)/seguranca/) --
// mesmo pedido do usuário de tela fixa no app, sem redirecionar pro site.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SegurancaScreen(onBack: () -> Unit, viewModel: SegurancaViewModel = viewModel()) {
    LaunchedEffect(Unit) { viewModel.load() }
    val data by viewModel.data
    val loading by viewModel.loading
    val busy by viewModel.busy
    val error by viewModel.errorMessage

    Scaffold(
        topBar = {
            TopAppBar(
                // Seta+título uma linha abaixo + fonte verde -- padrão global
                // (pedido do usuário, ver comentário em SettingsScreen.kt).
                title = {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Acessos e Segurança", color = MaterialTheme.colorScheme.primary)
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
        if (loading && data == null) {
            Column(modifier = Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.Center) {
                Text("Carregando...", modifier = Modifier.padding(24.dp))
            }
            return@Scaffold
        }
        val memberships = data?.get("memberships")?.jsonArray
        val logs = data?.get("logs")?.jsonArray
        val devices = data?.get("devices")?.jsonArray
        val auditLogs = data?.get("auditLogs")?.jsonArray

        LazyColumn(
            contentPadding = PaddingValues(12.dp, padding.calculateTopPadding() + 4.dp, 12.dp, 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (error != null) {
                item(key = "error") { Text(error!!, color = MaterialTheme.colorScheme.error) }
            }
            item(key = "equipe") {
                EquipeCard(
                    memberships = memberships,
                    busy = busy,
                    onInvite = { email, role, modulos, onDone -> viewModel.invite(email, role, modulos, onDone) },
                    onToggle = { id, ativo -> viewModel.toggleMembership(id, ativo) },
                    onUpdateModules = { id, role, modulos -> viewModel.updateModules(id, role, modulos) },
                )
            }
            item(key = "acessos") { AccessLogsCard(logs) }
            item(key = "dispositivos") { DevicesCard(devices) }
            item(key = "auditoria") { AuditLogsCard(auditLogs) }
        }
    }
}


@Composable
private fun CollapsibleCard(title: String, initiallyOpen: Boolean = false, content: @Composable () -> Unit) {
    var open by remember { mutableStateOf(initiallyOpen) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { open = !open },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = if (open) "Recolher" else "Expandir")
            }
            if (open) Column(modifier = Modifier.padding(top = 12.dp)) { content() }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoleDropdown(selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected, onValueChange = {}, readOnly = true, label = { Text("Papel") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ROLES.forEach { role ->
                DropdownMenuItem(text = { Text(role) }, onClick = { onSelect(role); expanded = false })
            }
        }
    }
}

@Composable
private fun ModulesChecklist(selected: List<String>, onChange: (List<String>) -> Unit) {
    Column {
        MODULES.forEach { (id, label) ->
            val checked = selected.contains(id)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 1.dp)) {
                Switch(
                    checked = checked,
                    onCheckedChange = { on -> onChange(if (on) selected + id else selected - id) },
                    colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary, checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                )
                Spacer(Modifier.width(6.dp))
                Text(label, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun EquipeCard(
    memberships: kotlinx.serialization.json.JsonArray?,
    busy: Boolean,
    onInvite: (String, String, List<String>, (Boolean, String?) -> Unit) -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onUpdateModules: (String, String, List<String>) -> Unit,
) {
    var inviteEmail by remember { mutableStateOf("") }
    var inviteRole by remember { mutableStateOf("OPERADOR") }
    var inviteModules by remember { mutableStateOf(listOf<String>()) }
    var inviteError by remember { mutableStateOf<String?>(null) }
    var showInviteForm by remember { mutableStateOf(false) }

    CollapsibleCard("Equipe (${memberships?.size ?: 0})", initiallyOpen = true) {
        memberships?.forEach { el ->
            val m = el.jsonObject
            val id = m["id"]?.jsonPrimitive?.contentOrNull ?: return@forEach
            val user = m["user"]?.jsonObject
            val email = user?.get("email")?.jsonPrimitive?.contentOrNull ?: ""
            val fullName = user?.get("fullName")?.jsonPrimitive?.contentOrNull
            val role = m["role"]?.jsonPrimitive?.contentOrNull ?: "OPERADOR"
            val ativo = m["ativo"]?.jsonPrimitive?.booleanOrNull ?: true
            val modulos = m["modulosPermitidos"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
            var expanded by remember(id) { mutableStateOf(false) }
            var editRole by remember(id) { mutableStateOf(role) }
            var editModules by remember(id) { mutableStateOf(modulos) }

            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { expanded = !expanded }) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(fullName ?: email, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text("$email · $role", style = MaterialTheme.typography.labelSmall)
                    }
                    // Bug real encontrado 2026-08-17: este switch nao tinha a
                    // mesma trava do site (seguranca-client.tsx:
                    // "disabled={m.role === 'OWNER'}") -- um toque aqui
                    // desativou o proprio OWNER (unico membro da org) e o
                    // trancou de fora com "conta sem organizacao ativa".
                    // Guard tambem reforcado no servidor (actions.ts e
                    // api/mobile/security/route.ts) pra nao depender so do
                    // client.
                    Switch(checked = ativo, onCheckedChange = { onToggle(id, it) }, enabled = !busy && role != "OWNER", colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary, checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)))
                    Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
                }
                if (expanded) {
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        RoleDropdown(selected = editRole) { editRole = it }
                        Text("Módulos liberados", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
                        ModulesChecklist(selected = editModules) { editModules = it }
                        Button(onClick = { onUpdateModules(id, editRole, editModules) }, enabled = !busy, modifier = Modifier.padding(top = 8.dp)) {
                            Text("Salvar permissões")
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        if (!showInviteForm) {
            Button(onClick = { showInviteForm = true }, modifier = Modifier.padding(top = 8.dp)) { Text("Convidar colaborador") }
        } else {
            Column(modifier = Modifier.padding(top = 12.dp)) {
                OutlinedTextField(value = inviteEmail, onValueChange = { inviteEmail = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                RoleDropdown(selected = inviteRole) { inviteRole = it }
                Text("Módulos liberados", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
                ModulesChecklist(selected = inviteModules) { inviteModules = it }
                if (inviteError != null) Text(inviteError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                Row(modifier = Modifier.padding(top = 8.dp)) {
                    Button(
                        onClick = {
                            onInvite(inviteEmail.trim(), inviteRole, inviteModules) { ok, err ->
                                if (ok) { showInviteForm = false; inviteEmail = ""; inviteModules = emptyList(); inviteError = null } else inviteError = err
                            }
                        },
                        enabled = !busy && inviteEmail.contains("@"),
                    ) { Text("Enviar convite") }
                    Spacer(Modifier.width(8.dp))
                    androidx.compose.material3.OutlinedButton(onClick = { showInviteForm = false }) { Text("Cancelar") }
                }
            }
        }
        if (busy) CircularProgressIndicator(modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun AccessLogsCard(logs: kotlinx.serialization.json.JsonArray?) {
    CollapsibleCard("Últimos acessos (${logs?.size ?: 0})") {
        logs?.take(50)?.forEach { el ->
            val l = el.jsonObject
            val email = l["email"]?.jsonPrimitive?.contentOrNull ?: ""
            val success = l["success"]?.jsonPrimitive?.booleanOrNull ?: true
            val reason = l["reason"]?.jsonPrimitive?.contentOrNull
            val createdAt = l["createdAt"]?.jsonPrimitive?.contentOrNull
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                Icon(
                    if (success) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
                    contentDescription = null,
                    tint = if (success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(email, style = MaterialTheme.typography.bodySmall)
                    Text(reason ?: formatIso(createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(formatIso(createdAt), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun DevicesCard(devices: kotlinx.serialization.json.JsonArray?) {
    CollapsibleCard("Dispositivos (${devices?.size ?: 0})") {
        devices?.forEach { el ->
            val d = el.jsonObject
            val deviceId = d["deviceId"]?.jsonPrimitive?.contentOrNull ?: ""
            val userAgent = d["userAgent"]?.jsonPrimitive?.contentOrNull ?: ""
            val lastSeenAt = d["lastSeenAt"]?.jsonPrimitive?.contentOrNull
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                Text(deviceId, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                Text(userAgent, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                Text("Visto pela última vez: ${formatIso(lastSeenAt)}", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun AuditLogsCard(auditLogs: kotlinx.serialization.json.JsonArray?) {
    CollapsibleCard("Histórico de alterações (${auditLogs?.size ?: 0})") {
        auditLogs?.take(100)?.forEach { el ->
            val a = el.jsonObject
            val userEmail = a["userEmail"]?.jsonPrimitive?.contentOrNull ?: ""
            val action = a["action"]?.jsonPrimitive?.contentOrNull ?: ""
            val domainId = a["domainId"]?.jsonPrimitive?.contentOrNull ?: ""
            val createdAt = a["createdAt"]?.jsonPrimitive?.contentOrNull
            Row(modifier = Modifier.padding(vertical = 3.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("$userEmail · $action · $domainId", style = MaterialTheme.typography.bodySmall)
                }
                Text(formatIso(createdAt), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private fun formatIso(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    return try {
        val parser = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
        parser.timeZone = java.util.TimeZone.getTimeZone("UTC")
        val date = parser.parse(iso) ?: return iso
        val fmt = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale("pt", "BR"))
        fmt.format(date)
    } catch (e: Exception) {
        iso
    }
}
