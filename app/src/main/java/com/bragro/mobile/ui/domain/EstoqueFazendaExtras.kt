package com.bragro.mobile.ui.domain

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import com.bragro.mobile.ui.theme.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import com.bragro.mobile.data.repo.ModuleActionsRepository
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// Controle de Estoque por Fazenda -- pedido do usuário ("os produtos são
// distribuídos em várias fazendas, como faço pra fazer esse controle
// interligando com a tabela estoque"). Réplica nativa de
// transferencias-fazenda-panel.tsx: transferir do Depósito Central (ou de
// outra fazenda) pra uma fazenda, ver o saldo de cada item por fazenda, e
// devolver o que sobrou de uma entrega anterior. Tudo via
// /api/mobile/module-actions (mesmas Server-side functions do site, ver
// lib/services/estoque-fazenda.ts) -- sem tabela nova nenhuma.

data class FazendaOpt(val id: String, val name: String)
data class SaldoLinha(val fazendaNome: String, val item: String, val unidade: String?, val saldo: Double)
data class TransferenciaRecebida(
    val id: String, val item: String, val unidade: String?, val quantidade: Double,
    val devolvido: Double, val fazendaNome: String,
)

class EstoqueFazendaViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = ModuleActionsRepository(app)
    var loading = mutableStateOf(false)
        private set
    var farms = mutableStateOf<List<FazendaOpt>>(emptyList())
        private set
    var saldos = mutableStateOf<List<SaldoLinha>>(emptyList())
        private set
    var transferencias = mutableStateOf<List<TransferenciaRecebida>>(emptyList())
        private set
    var message = mutableStateOf<String?>(null)
        private set

    fun load() {
        loading.value = true
        viewModelScope.launch {
            val result = repo.run("estoque-saldo-fazenda")
            if (result != null) {
                farms.value = result["farms"]?.jsonArray.orEmpty().mapNotNull { it as? JsonObject }.map {
                    FazendaOpt(it["id"]?.jsonPrimitive?.content ?: "", it["name"]?.jsonPrimitive?.content ?: "")
                }
                saldos.value = result["saldos"]?.jsonArray.orEmpty().mapNotNull { it as? JsonObject }.map {
                    SaldoLinha(
                        fazendaNome = it["fazendaNome"]?.jsonPrimitive?.content ?: "—",
                        item = it["item"]?.jsonPrimitive?.content ?: "—",
                        unidade = it["unidade"]?.jsonPrimitive?.contentOrNull,
                        saldo = it["saldo"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                    )
                }
                transferencias.value = result["transferencias"]?.jsonArray.orEmpty().mapNotNull { it as? JsonObject }.map {
                    TransferenciaRecebida(
                        id = it["id"]?.jsonPrimitive?.content ?: "",
                        item = it["item"]?.jsonPrimitive?.content ?: "—",
                        unidade = it["unidade"]?.jsonPrimitive?.contentOrNull,
                        quantidade = it["quantidade"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                        devolvido = it["devolvido"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                        fazendaNome = it["fazendaNome"]?.jsonPrimitive?.content ?: "—",
                    )
                }
            }
            loading.value = false
        }
    }

    fun transferir(item: String, unidade: String, quantidade: Double, fazendaOrigemId: String?, fazendaDestinoId: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val result = repo.run(
                "estoque-transferir", item = item, unidade = unidade.ifBlank { null },
                quantidade = quantidade, fazendaOrigemId = fazendaOrigemId, fazendaDestinoId = fazendaDestinoId,
            )
            message.value = if (result != null) "Transferência registrada." else "Não foi possível transferir -- confira os dados e a conexão."
            if (result != null) load()
            onDone(result != null)
        }
    }

    fun devolver(transferenciaEntradaId: String, quantidade: Double, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val result = repo.run("estoque-devolver", quantidade = quantidade, transferenciaEntradaId = transferenciaEntradaId)
            message.value = if (result != null) "Devolução registrada." else "Não foi possível devolver -- confira os dados e a conexão."
            if (result != null) load()
            onDone(result != null)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FazendaDropdown(label: String, options: List<FazendaOpt>, placeholder: String, selectedId: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = options.firstOrNull { it.id == selectedId }?.name ?: placeholder
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedName, onValueChange = {}, readOnly = true, label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { f ->
                DropdownMenuItem(text = { Text(f.name) }, onClick = { onSelect(f.id); expanded = false })
            }
        }
    }
}

/** Bloco colapsável "Transferências entre Fazendas" -- só aparece no módulo
 * Estoque (ver DomainListScreen.kt). Fechado por padrão, mesmo critério do
 * bloco Filtros. */
@Composable
fun TransferenciasFazendaCard(viewModel: EstoqueFazendaViewModel = viewModel(), showHeader: Boolean = true) {
    LaunchedEffect(Unit) { viewModel.load() }
    // Sem cabeçalho, quem controla a visibilidade é a fileira de ícones do
    // módulo (ModuleIconRow) -- já nasce aberto.
    var open by remember { mutableStateOf(!showHeader) }
    var transferOpen by remember { mutableStateOf(false) }
    var devolverAlvo by remember { mutableStateOf<String?>(null) }
    val loading by viewModel.loading
    val farms by viewModel.farms
    val saldos by viewModel.saldos
    val transferencias by viewModel.transferencias
    val message by viewModel.message

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (showHeader) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CompareArrows, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text("Transferências entre Fazendas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = { open = !open }) {
                        Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = if (open) "Recolher" else "Expandir")
                    }
                }
            }
            if (open) {
                if (loading && farms.isEmpty()) {
                    Text("Carregando...", style = MaterialTheme.typography.bodySmall)
                } else {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { transferOpen = true }) {
                            Icon(Icons.Filled.CompareArrows, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(" Transferir pra fazenda")
                        }
                    }
                    Text("Saldo por fazenda", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    if (saldos.isEmpty()) {
                        Text("Nenhuma transferência lançada ainda.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        saldos.forEach { s ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${s.fazendaNome} — ${s.item}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                Text("${s.saldo} ${s.unidade ?: ""}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    if (transferencias.isNotEmpty()) {
                        Text("Entregas recentes (devolver o que sobrou)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        transferencias.forEach { t ->
                            val disponivel = t.quantidade - t.devolvido
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(
                                    "${t.fazendaNome} — ${t.item}: ${t.quantidade} ${t.unidade ?: ""}",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f),
                                )
                                if (disponivel > 0) {
                                    IconButton(onClick = { devolverAlvo = t.id }) {
                                        Icon(Icons.Filled.Undo, contentDescription = "Devolver")
                                    }
                                }
                            }
                        }
                    }
                    if (message != null) Text(message!!, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    if (transferOpen) {
        TransferirFazendaDialog(farms = farms, onDismiss = { transferOpen = false }, viewModel = viewModel)
    }
    if (devolverAlvo != null) {
        DevolverFazendaDialog(transferenciaEntradaId = devolverAlvo!!, onDismiss = { devolverAlvo = null }, viewModel = viewModel)
    }
}

@Composable
private fun TransferirFazendaDialog(farms: List<FazendaOpt>, onDismiss: () -> Unit, viewModel: EstoqueFazendaViewModel) {
    var item by remember { mutableStateOf("") }
    var unidade by remember { mutableStateOf("") }
    var quantidade by remember { mutableStateOf("") }
    var fazendaOrigemId by remember { mutableStateOf("") }
    var fazendaDestinoId by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Transferir pra fazenda") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Sai do depósito de origem e entra na fazenda de destino.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value = item, onValueChange = { item = it }, label = { Text("Item *") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = quantidade, onValueChange = { quantidade = it }, label = { Text("Quantidade *") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = unidade, onValueChange = { unidade = it }, label = { Text("Unidade") }, modifier = Modifier.weight(1f))
                }
                FazendaDropdown("De onde sai", farms, "Depósito Central", fazendaOrigemId) { fazendaOrigemId = it }
                FazendaDropdown("Fazenda de destino *", farms, "Selecione", fazendaDestinoId) { fazendaDestinoId = it }
            }
        },
        confirmButton = {
            Button(
                enabled = !saving && item.isNotBlank() && fazendaDestinoId.isNotBlank() && quantidade.toDoubleOrNull() != null,
                onClick = {
                    saving = true
                    viewModel.transferir(item, unidade, quantidade.toDoubleOrNull() ?: 0.0, fazendaOrigemId.ifBlank { null }, fazendaDestinoId) {
                        saving = false
                        onDismiss()
                    }
                },
            ) {
                if (saving) CircularProgressIndicator(modifier = Modifier.size(18.dp)) else Text("Transferir")
            }
        },
        dismissButton = { Button(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun DevolverFazendaDialog(transferenciaEntradaId: String, onDismiss: () -> Unit, viewModel: EstoqueFazendaViewModel) {
    var quantidade by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Devolver") },
        text = {
            OutlinedTextField(
                value = quantidade, onValueChange = { quantidade = it },
                label = { Text("Quantidade que está voltando *") },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(
                enabled = !saving && quantidade.toDoubleOrNull() != null,
                onClick = {
                    saving = true
                    viewModel.devolver(transferenciaEntradaId, quantidade.toDoubleOrNull() ?: 0.0) {
                        saving = false
                        onDismiss()
                    }
                },
            ) {
                if (saving) CircularProgressIndicator(modifier = Modifier.size(18.dp)) else Text("Confirmar devolução")
            }
        },
        dismissButton = { Button(onClick = onDismiss) { Text("Cancelar") } },
    )
}
