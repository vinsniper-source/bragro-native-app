package com.bragro.mobile.ui.nfe

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bragro.mobile.data.model.InvoiceData
import com.bragro.mobile.data.repo.NfeRepository
import com.bragro.mobile.ui.theme.Card
import com.bragro.mobile.ui.theme.appFieldColors
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

/** Módulo NF-e (Task #628, ausente por completo no app até aqui, réplica de
 * nfe-client.tsx do site). Escopo v1 do app (pedido do usuário): listar
 * notas, lançar manual (numero/serie/tipo/emitenteNome/valorTotal/
 * dataEmissao -- mesmo subconjunto de createManualInvoiceAction), emitir
 * (SAIDA) junto à SEFAZ e excluir. Importação de XML e edição de nota
 * continuam site-only (mesmo critério de "visualizador simplificado" já
 * usado em Prescrição/Dossiê nesta rodada). Sem cache offline de propósito
 * (mesmo critério de OrcamentoRepository/ReconciliacaoEstoqueRepository).
 */
class NfeViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = NfeRepository(app)

    var carregando = mutableStateOf(true)
        private set
    var erro = mutableStateOf<String?>(null)
        private set
    var invoices = mutableStateOf<List<InvoiceData>>(emptyList())
        private set

    var pendingAction = mutableStateOf<String?>(null)
        private set

    init { carregar() }

    fun carregar() {
        carregando.value = true
        erro.value = null
        viewModelScope.launch {
            val resposta = repository.listar()
            carregando.value = false
            if (resposta == null || !resposta.ok) {
                erro.value = resposta?.error ?: "Sem conexão -- não foi possível carregar as notas fiscais agora."
                return@launch
            }
            invoices.value = resposta.invoices ?: emptyList()
        }
    }

    fun criar(
        numero: String,
        serie: String?,
        tipo: String,
        emitenteNome: String,
        valorTotal: Double,
        dataEmissao: String?,
        onDone: (String?) -> Unit,
    ) {
        pendingAction.value = "create"
        viewModelScope.launch {
            val resultado = repository.criar(numero, serie, tipo, emitenteNome, valorTotal, dataEmissao)
            pendingAction.value = null
            if (resultado == null || !resultado.ok) {
                onDone(resultado?.error ?: "Erro ao lançar a nota fiscal.")
                return@launch
            }
            onDone(null)
            carregar()
        }
    }

    fun excluir(invoiceId: String, onDone: (String?) -> Unit) {
        pendingAction.value = "delete:$invoiceId"
        viewModelScope.launch {
            val resultado = repository.excluir(invoiceId)
            pendingAction.value = null
            if (resultado == null || !resultado.ok) {
                onDone(resultado?.error ?: "Erro ao excluir a nota fiscal.")
                return@launch
            }
            onDone(null)
            carregar()
        }
    }

    fun emitir(invoiceId: String, onDone: (String?, Boolean) -> Unit) {
        pendingAction.value = "emitir:$invoiceId"
        viewModelScope.launch {
            val resultado = repository.emitir(invoiceId)
            pendingAction.value = null
            if (resultado == null) {
                onDone("Sem conexão. Tente novamente.", false)
                return@launch
            }
            onDone(resultado.mensagem ?: resultado.error, resultado.ok)
            carregar()
        }
    }
}

private fun formatoMoeda(valor: Double): String =
    NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(valor)

private val TIPO_OPTIONS = listOf("ENTRADA", "SAIDA")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StringDropdown(label: String, value: String, options: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            colors = appFieldColors(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for (opt in options) {
                DropdownMenuItem(text = { Text(opt) }, onClick = { onSelect(opt); expanded = false })
            }
        }
    }
}

@Composable
private fun statusCor(inv: InvoiceData) = when {
    inv.statusSefaz == "AUTORIZADA" -> MaterialTheme.colorScheme.primary
    inv.statusSefaz == "REJEITADA" || inv.statusSefaz == "ERRO" -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun InvoiceCard(
    inv: InvoiceData,
    pendingAction: String?,
    onEmitir: () -> Unit,
    onExcluir: () -> Unit,
) {
    val emitindo = pendingAction == "emitir:${inv.id}"
    val excluindo = pendingAction == "delete:${inv.id}"
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Nº ${inv.numero}${inv.serie?.takeIf { it.isNotBlank() }?.let { " / série $it" } ?: ""}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    Text(inv.emitenteNome ?: inv.destinatarioNome ?: "-", style = MaterialTheme.typography.bodyMedium)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(formatoMoeda(inv.valorTotal), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    Text(inv.tipo ?: "ENTRADA", style = MaterialTheme.typography.labelSmall)
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(inv.status, style = MaterialTheme.typography.labelSmall)
                    if (!inv.statusSefaz.isNullOrBlank()) {
                        Text("SEFAZ: ${inv.statusSefaz}", style = MaterialTheme.typography.labelSmall, color = statusCor(inv))
                    }
                    if (!inv.erroSefaz.isNullOrBlank()) {
                        Text(inv.erroSefaz, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Emitir só faz sentido pra SAIDA ainda não autorizada -- mesmo
                    // critério de emitirNotaFiscalAction/nfe-client.tsx no site.
                    if (inv.tipo == "SAIDA" && inv.statusSefaz != "AUTORIZADA") {
                        IconButton(onClick = onEmitir, enabled = !emitindo) {
                            if (emitindo) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Filled.Send, contentDescription = "Emitir junto à SEFAZ", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    IconButton(onClick = onExcluir, enabled = !excluindo) {
                        if (excluindo) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Filled.Delete, contentDescription = "Excluir nota", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NovaNotaDialog(pending: Boolean, onDismiss: () -> Unit, onConfirm: (String, String?, String, String, Double, String?) -> Unit) {
    var numero by remember { mutableStateOf("") }
    var serie by remember { mutableStateOf("") }
    var tipo by remember { mutableStateOf("ENTRADA") }
    var emitenteNome by remember { mutableStateOf("") }
    var valorTotal by remember { mutableStateOf("") }
    var dataEmissao by remember { mutableStateOf("") }

    fun parseValor(s: String): Double =
        s.trim().replace(".", "").replace(",", ".").toDoubleOrNull() ?: s.trim().toDoubleOrNull() ?: 0.0

    val podeSalvar = numero.isNotBlank() && emitenteNome.isNotBlank() && valorTotal.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nova NF-e (manual)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = numero, onValueChange = { numero = it }, label = { Text("Número *") }, modifier = Modifier.fillMaxWidth(), colors = appFieldColors())
                OutlinedTextField(value = serie, onValueChange = { serie = it }, label = { Text("Série") }, placeholder = { Text("Opcional") }, modifier = Modifier.fillMaxWidth(), colors = appFieldColors())
                StringDropdown(label = "Tipo", value = tipo, options = TIPO_OPTIONS, onSelect = { tipo = it })
                OutlinedTextField(value = emitenteNome, onValueChange = { emitenteNome = it }, label = { Text("Emitente *") }, modifier = Modifier.fillMaxWidth(), colors = appFieldColors())
                OutlinedTextField(value = valorTotal, onValueChange = { valorTotal = it }, label = { Text("Valor total *") }, placeholder = { Text("0,00") }, modifier = Modifier.fillMaxWidth(), colors = appFieldColors())
                OutlinedTextField(value = dataEmissao, onValueChange = { dataEmissao = it }, label = { Text("Data de emissão") }, placeholder = { Text("AAAA-MM-DD, opcional") }, modifier = Modifier.fillMaxWidth(), colors = appFieldColors())
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(numero, serie.ifBlank { null }, tipo, emitenteNome, parseValor(valorTotal), dataEmissao.ifBlank { null }) },
                enabled = podeSalvar && !pending,
            ) {
                if (pending) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text("Lançar")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !pending) { Text("Cancelar") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NfeScreen(onBack: () -> Unit, viewModel: NfeViewModel = viewModel()) {
    val carregando by viewModel.carregando
    val erro by viewModel.erro
    val invoices by viewModel.invoices
    val pendingAction by viewModel.pendingAction

    var showNovaNota by remember { mutableStateOf(false) }
    var dialogErro by remember { mutableStateOf<String?>(null) }
    var confirmarExclusao by remember { mutableStateOf<InvoiceData?>(null) }
    var mensagemEmissao by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NF-e", color = MaterialTheme.colorScheme.primary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = MaterialTheme.colorScheme.primary)
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showNovaNota = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Nova NF-e")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (mensagemEmissao != null) {
                Surface(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f), modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(mensagemEmissao ?: "", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        TextButton(onClick = { mensagemEmissao = null }) { Text("Ok") }
                    }
                }
            }
            if (carregando) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (erro != null) {
                Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(erro ?: "", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = { viewModel.carregar() }) { Text("Tentar novamente") }
                }
            } else if (invoices.isEmpty()) {
                Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Nenhuma nota fiscal lançada ainda.", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(invoices, key = { it.id }) { inv ->
                        InvoiceCard(
                            inv = inv,
                            pendingAction = pendingAction,
                            onEmitir = {
                                viewModel.emitir(inv.id) { mensagem, _ -> mensagemEmissao = mensagem }
                            },
                            onExcluir = { confirmarExclusao = inv },
                        )
                    }
                }
            }
        }
    }

    if (showNovaNota) {
        NovaNotaDialog(
            pending = pendingAction == "create",
            onDismiss = { if (pendingAction != "create") showNovaNota = false },
            onConfirm = { numero, serie, tipo, emitenteNome, valorTotal, dataEmissao ->
                viewModel.criar(numero, serie, tipo, emitenteNome, valorTotal, dataEmissao) { erroMsg ->
                    if (erroMsg == null) showNovaNota = false else dialogErro = erroMsg
                }
            },
        )
    }

    if (dialogErro != null) {
        AlertDialog(
            onDismissRequest = { dialogErro = null },
            title = { Text("Não foi possível lançar") },
            text = { Text(dialogErro ?: "") },
            confirmButton = { TextButton(onClick = { dialogErro = null }) { Text("Ok") } },
        )
    }

    val paraExcluir = confirmarExclusao
    if (paraExcluir != null) {
        AlertDialog(
            onDismissRequest = { confirmarExclusao = null },
            title = { Text("Excluir nota fiscal?") },
            text = { Text("Nº ${paraExcluir.numero} -- os lançamentos de Estoque/Financeiro já gerados a partir dela (se houver) não são apagados automaticamente.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.excluir(paraExcluir.id) { erroMsg -> dialogErro = erroMsg }
                    confirmarExclusao = null
                }) { Text("Excluir", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmarExclusao = null }) { Text("Cancelar") } },
        )
    }
}

