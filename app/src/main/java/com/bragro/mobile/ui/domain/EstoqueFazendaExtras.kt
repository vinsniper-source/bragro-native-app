package com.bragro.mobile.ui.domain

import android.app.Application
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import com.bragro.mobile.ui.theme.Card
import com.bragro.mobile.ui.theme.appFieldColors
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
import com.bragro.mobile.data.local.LookupEntity
import com.bragro.mobile.data.repo.ConfigRepository
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
//
// Pedido do usuário ("precisamos tambem colocar saída/devolução e mudarmos o
// nome dos campos... ajuste manual (com registro data hora e usuário, fique
// permanente esse aviso) listas ssuspensas em item, origem, destino"): além
// de Transferir/Devolver, mais dois botões/diálogos -- "Saída" (baixa manual,
// uma linha só) e "Ajuste manual" (correção de saldo com motivo
// OBRIGATÓRIO). Os rótulos de fazenda em TODOS os diálogos agora são
// "Origem"/"Destino" (antes "De onde sai"/"Fazenda de destino"), e o campo
// Item deixou de ser texto livre -- agora é uma lista suspensa vinda da
// categoria "itens_estoque" (mesma lookup do formulário genérico de
// Estoque), igual ao site.

data class FazendaOpt(val id: String, val name: String)
data class SaldoLinha(val fazendaNome: String, val item: String, val unidade: String?, val saldo: Double)
data class TransferenciaRecebida(
    val id: String, val item: String, val unidade: String?, val quantidade: Double,
    val devolvido: Double, val fazendaNome: String,
)

class EstoqueFazendaViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = ModuleActionsRepository(app)
    private val configRepo = ConfigRepository(app)
    var loading = mutableStateOf(false)
        private set
    var farms = mutableStateOf<List<FazendaOpt>>(emptyList())
        private set
    var itens = mutableStateOf<List<LookupEntity>>(emptyList())
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
            // Item agora é lista suspensa (pedido do usuário) -- mesma
            // categoria "itens_estoque" que o formulário genérico de Estoque
            // usa (ver DomainFormViewModel.load em DomainFormScreen.kt).
            itens.value = configRepo.lookupsByCategory("itens_estoque")
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

    /** Saída manual (consumo/perda/baixa) -- uma linha só, sem "outro lado"
     * recebendo. "fazendaOrigemId" aqui é a fazenda ÚNICA da saída (rótulo
     * "Origem" no diálogo). */
    fun saida(item: String, unidade: String, quantidade: Double, fazendaOrigemId: String?, motivo: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val result = repo.run(
                "estoque-saida", item = item, unidade = unidade.ifBlank { null },
                quantidade = quantidade, fazendaOrigemId = fazendaOrigemId, motivo = motivo.ifBlank { null },
            )
            message.value = if (result != null) "Saída registrada." else "Não foi possível registrar a saída -- confira os dados e a conexão."
            if (result != null) load()
            onDone(result != null)
        }
    }

    /** Ajuste manual de saldo -- motivo OBRIGATÓRIO (pedido do usuário: "com
     * registro data hora e usuário, fique permanente esse aviso"). O
     * backend grava responsavel + criadoEm (permanentes, nunca expurgados)
     * junto com o motivo. */
    fun ajusteManual(item: String, unidade: String, quantidade: Double, fazendaOrigemId: String?, tipo: String, motivo: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val result = repo.run(
                "estoque-ajuste", item = item, unidade = unidade.ifBlank { null },
                quantidade = quantidade, fazendaOrigemId = fazendaOrigemId, tipo = tipo, motivo = motivo,
            )
            message.value = if (result != null) "Ajuste manual registrado." else "Não foi possível registrar o ajuste -- confira os dados e a conexão."
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
            colors = appFieldColors(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { f ->
                DropdownMenuItem(text = { Text(f.name) }, onClick = { onSelect(f.id); expanded = false })
            }
        }
    }
}

/** Item agora é lista suspensa (pedido do usuário) -- mesmo mecanismo de
 * FazendaDropdown acima, só que sobre a lookup "itens_estoque" (mesma
 * categoria do formulário genérico de Estoque, ver FormField em
 * DomainFormScreen.kt). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItemDropdown(label: String, options: List<LookupEntity>, placeholder: String, selectedValue: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.value == selectedValue }?.label ?: selectedValue.ifBlank { placeholder }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedLabel, onValueChange = {}, readOnly = true, label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            colors = appFieldColors(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { i ->
                DropdownMenuItem(text = { Text(i.label) }, onClick = { onSelect(i.value); expanded = false })
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
    var saidaOpen by remember { mutableStateOf(false) }
    var ajusteOpen by remember { mutableStateOf(false) }
    var devolverAlvo by remember { mutableStateOf<String?>(null) }
    val loading by viewModel.loading
    val farms by viewModel.farms
    val itens by viewModel.itens
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
                    // horizontalScroll -- pedido do usuário ("coloque o
                    // bloco ajuste na mesma linha horizontal do bloco
                    // saída"): Row simples não quebra linha sozinho, então
                    // em telas estreitas o 3º botão (Ajuste) podia ficar
                    // cortado na borda; com scroll, os 3 continuam sempre na
                    // mesma linha, sem nunca sumir.
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(onClick = { transferOpen = true }) {
                            Icon(Icons.Filled.CompareArrows, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(" Transferir")
                        }
                        OutlinedButton(onClick = { saidaOpen = true }) {
                            Icon(Icons.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(" Saída")
                        }
                        OutlinedButton(onClick = { ajusteOpen = true }) {
                            Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(" Ajuste")
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
        TransferirFazendaDialog(farms = farms, itens = itens, onDismiss = { transferOpen = false }, viewModel = viewModel)
    }
    if (saidaOpen) {
        SaidaFazendaDialog(farms = farms, itens = itens, onDismiss = { saidaOpen = false }, viewModel = viewModel)
    }
    if (ajusteOpen) {
        AjusteManualFazendaDialog(farms = farms, itens = itens, onDismiss = { ajusteOpen = false }, viewModel = viewModel)
    }
    if (devolverAlvo != null) {
        DevolverFazendaDialog(transferenciaEntradaId = devolverAlvo!!, onDismiss = { devolverAlvo = null }, viewModel = viewModel)
    }
}

@Composable
private fun TransferirFazendaDialog(farms: List<FazendaOpt>, itens: List<LookupEntity>, onDismiss: () -> Unit, viewModel: EstoqueFazendaViewModel) {
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
                ItemDropdown("Item *", itens, "Selecione", item) { item = it }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = quantidade, onValueChange = { quantidade = it }, label = { Text("Quantidade *") }, modifier = Modifier.weight(1f), colors = appFieldColors())
                    OutlinedTextField(value = unidade, onValueChange = { unidade = it }, label = { Text("Unidade") }, modifier = Modifier.weight(1f), colors = appFieldColors())
                }
                FazendaDropdown("Origem", farms, "Depósito Central", fazendaOrigemId) { fazendaOrigemId = it }
                FazendaDropdown("Destino *", farms, "Selecione", fazendaDestinoId) { fazendaDestinoId = it }
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

/** Saída manual (consumo/perda/baixa) -- uma fazenda só (rótulo "Origem",
 * já que o item está saindo do estoque dela), sem par de destino. */
@Composable
private fun SaidaFazendaDialog(farms: List<FazendaOpt>, itens: List<LookupEntity>, onDismiss: () -> Unit, viewModel: EstoqueFazendaViewModel) {
    var item by remember { mutableStateOf("") }
    var unidade by remember { mutableStateOf("") }
    var quantidade by remember { mutableStateOf("") }
    var fazendaId by remember { mutableStateOf("") }
    var motivo by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Saída de estoque") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Baixa manual do item (consumo, perda, quebra etc.) -- não é transferência pra outra fazenda.", style = MaterialTheme.typography.bodySmall)
                ItemDropdown("Item *", itens, "Selecione", item) { item = it }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = quantidade, onValueChange = { quantidade = it }, label = { Text("Quantidade *") }, modifier = Modifier.weight(1f), colors = appFieldColors())
                    OutlinedTextField(value = unidade, onValueChange = { unidade = it }, label = { Text("Unidade") }, modifier = Modifier.weight(1f), colors = appFieldColors())
                }
                FazendaDropdown("Origem", farms, "Depósito Central", fazendaId) { fazendaId = it }
                OutlinedTextField(
                    value = motivo, onValueChange = { motivo = it }, label = { Text("Motivo") },
                    placeholder = { Text("Opcional -- ex.: consumo, perda, quebra") }, modifier = Modifier.fillMaxWidth(),
                    colors = appFieldColors(),
                )
            }
        },
        confirmButton = {
            Button(
                enabled = !saving && item.isNotBlank() && quantidade.toDoubleOrNull() != null,
                onClick = {
                    saving = true
                    viewModel.saida(item, unidade, quantidade.toDoubleOrNull() ?: 0.0, fazendaId.ifBlank { null }, motivo) {
                        saving = false
                        onDismiss()
                    }
                },
            ) {
                if (saving) CircularProgressIndicator(modifier = Modifier.size(18.dp)) else Text("Registrar saída")
            }
        },
        dismissButton = { Button(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Ajuste manual de saldo -- motivo OBRIGATÓRIO (pedido do usuário: "com
 * registro data hora e usuário, fique permanente esse aviso"). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AjusteManualFazendaDialog(farms: List<FazendaOpt>, itens: List<LookupEntity>, onDismiss: () -> Unit, viewModel: EstoqueFazendaViewModel) {
    var item by remember { mutableStateOf("") }
    var unidade by remember { mutableStateOf("") }
    var quantidade by remember { mutableStateOf("") }
    var fazendaId by remember { mutableStateOf("") }
    var tipo by remember { mutableStateOf("ENTRADA") }
    var motivo by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var tipoExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajuste manual de estoque") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Corrige o saldo de um item (contagem de inventário, sobra ou quebra na conferência etc.). O motivo é " +
                        "obrigatório e fica registrado permanentemente junto com o usuário e a data/hora do ajuste.",
                    style = MaterialTheme.typography.bodySmall,
                )
                ItemDropdown("Item *", itens, "Selecione", item) { item = it }
                ExposedDropdownMenuBox(expanded = tipoExpanded, onExpandedChange = { tipoExpanded = it }) {
                    OutlinedTextField(
                        value = if (tipo == "ENTRADA") "Entrada (aumenta o saldo)" else "Saída (reduz o saldo)",
                        onValueChange = {}, readOnly = true, label = { Text("Tipo *") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = tipoExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        colors = appFieldColors(),
                    )
                    ExposedDropdownMenu(expanded = tipoExpanded, onDismissRequest = { tipoExpanded = false }) {
                        DropdownMenuItem(text = { Text("Entrada (aumenta o saldo)") }, onClick = { tipo = "ENTRADA"; tipoExpanded = false })
                        DropdownMenuItem(text = { Text("Saída (reduz o saldo)") }, onClick = { tipo = "SAIDA"; tipoExpanded = false })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = quantidade, onValueChange = { quantidade = it }, label = { Text("Quantidade *") }, modifier = Modifier.weight(1f), colors = appFieldColors())
                    OutlinedTextField(value = unidade, onValueChange = { unidade = it }, label = { Text("Unidade") }, modifier = Modifier.weight(1f), colors = appFieldColors())
                }
                FazendaDropdown("Origem", farms, "Depósito Central", fazendaId) { fazendaId = it }
                OutlinedTextField(
                    value = motivo, onValueChange = { motivo = it }, label = { Text("Motivo *") },
                    placeholder = { Text("Obrigatório -- justifique o ajuste") }, modifier = Modifier.fillMaxWidth(),
                    colors = appFieldColors(),
                )
            }
        },
        confirmButton = {
            Button(
                enabled = !saving && item.isNotBlank() && quantidade.toDoubleOrNull() != null && motivo.isNotBlank(),
                onClick = {
                    saving = true
                    viewModel.ajusteManual(item, unidade, quantidade.toDoubleOrNull() ?: 0.0, fazendaId.ifBlank { null }, tipo, motivo) {
                        saving = false
                        onDismiss()
                    }
                },
            ) {
                if (saving) CircularProgressIndicator(modifier = Modifier.size(18.dp)) else Text("Registrar ajuste")
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
                colors = appFieldColors(),
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
