package com.bragro.mobile.ui.nfe

import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.KeyboardDoubleArrowDown
import androidx.compose.material.icons.outlined.KeyboardDoubleArrowUp
import androidx.compose.material.icons.outlined.Refresh
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import com.bragro.mobile.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bragro.mobile.data.model.InvoiceData
import com.bragro.mobile.data.repo.NfeRepository
import com.bragro.mobile.ui.domain.isoDateOnly
import com.bragro.mobile.ui.theme.Card
import com.bragro.mobile.ui.theme.SearchableDropdownField
import com.bragro.mobile.ui.theme.appFieldColors
import com.bragro.mobile.ui.util.shareBinaryFile
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

    // "Repositório de XML" (Task #655/#644) -- baixa o .zip com o XML de
    // cada NF-e (recebidas e enviadas) do período informado e devolve os
    // bytes já decodificados pra quem chamou compartilhar/salvar
    // (shareBinaryFile, ver NfeScreen abaixo). Mesmo critério de
    // pendingAction dos outros métodos, com uma chave fixa própria ("
    // downloadLote") já que não é por nota.
    fun baixarXmlLote(inicio: String?, fim: String?, onDone: (ByteArray?, String?) -> Unit) {
        pendingAction.value = "downloadLote"
        viewModelScope.launch {
            val resultado = repository.baixarLote(inicio, fim)
            pendingAction.value = null
            if (resultado == null || !resultado.ok || resultado.base64.isNullOrBlank()) {
                onDone(null, resultado?.error ?: "Sem conexão -- não foi possível baixar os XMLs agora.")
                return@launch
            }
            try {
                val bytes = android.util.Base64.decode(resultado.base64, android.util.Base64.DEFAULT)
                onDone(bytes, null)
            } catch (e: Exception) {
                onDone(null, "Falha ao processar o arquivo recebido.")
            }
        }
    }
}

private fun formatoMoeda(valor: Double): String =
    NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(valor)

/** Inverso de parseValor() (ver NovaNotaDialog abaixo) -- usado só pra
 * pré-preencher o campo "Valor total" a partir de um Double vindo do
 * servidor (ex.: "Copiar última nota"). parseValor() remove "." (separador
 * de milhar) e troca "," por "." antes de converter, então o campo NUNCA
 * pode conter um "." aqui (senão um valor como 1234.56 viraria "123456" ao
 * ser reaberto/reenviado) -- por isso a vírgula decimal sem separador de
 * milhar, mesmo com valores grandes. */
private fun valorParaCampo(v: Double): String {
    val texto = if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()
    return texto.replace(".", ",")
}

private val TIPO_OPTIONS = listOf("ENTRADA", "SAIDA")

/** Alternância Linhas/Blocos (achado de auditoria: paridade com o toggle
 * "mobileView" de nfe-client.tsx, site) -- BLOCOS é o card completo já
 * existente (InvoiceCard); LINHAS é uma versão compacta de uma linha só
 * (InvoiceRow, abaixo), útil pra escanear muitas notas de uma vez. Começa em
 * BLOCOS, mesmo padrão default do site. */
private enum class NfeViewMode { BLOCOS, LINHAS }

@Composable
private fun StringDropdown(label: String, value: String, options: List<String>, onSelect: (String) -> Unit) {
    SearchableDropdownField(
        value = value,
        label = label,
        options = remember(options) { options.map { it to it } },
        onSelect = onSelect,
        emptyOptionLabel = null,
    )
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
                            else Icon(Icons.Outlined.Send, contentDescription = "Emitir junto à SEFAZ", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    IconButton(onClick = onExcluir, enabled = !excluindo) {
                        if (excluindo) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Outlined.Delete, contentDescription = "Excluir nota", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

/** Versão compacta (uma linha) de InvoiceCard acima, pra vista "Linhas"
 * (achado de auditoria: paridade com a tabela/linhas do site, ver
 * mobileView em nfe-client.tsx). Mesmas ações (Emitir/Excluir), só que sem
 * os blocos internos de status/erro SEFAZ (ficam só no card completo) --
 * o objetivo aqui é caber mais notas na tela pra escanear rápido. */
@Composable
private fun InvoiceRow(
    inv: InvoiceData,
    pendingAction: String?,
    onEmitir: () -> Unit,
    onExcluir: () -> Unit,
) {
    val emitindo = pendingAction == "emitir:${inv.id}"
    val excluindo = pendingAction == "delete:${inv.id}"
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Nº ${inv.numero}${inv.serie?.takeIf { it.isNotBlank() }?.let { " / $it" } ?: ""}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                inv.emitenteNome ?: inv.destinatarioNome ?: "-",
                style = MaterialTheme.typography.labelSmall,
            )
        }
        Text(
            formatoMoeda(inv.valorTotal),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (inv.tipo == "SAIDA" && inv.statusSefaz != "AUTORIZADA") {
                IconButton(onClick = onEmitir, enabled = !emitindo, modifier = Modifier.size(36.dp)) {
                    if (emitindo) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Outlined.Send, contentDescription = "Emitir junto à SEFAZ", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                }
            }
            IconButton(onClick = onExcluir, enabled = !excluindo, modifier = Modifier.size(36.dp)) {
                if (excluindo) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                else Icon(Icons.Outlined.Delete, contentDescription = "Excluir nota", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NovaNotaDialog(
    pending: Boolean,
    initial: InvoiceData? = null,
    onDismiss: () -> Unit,
    onConfirm: (String, String?, String, String, Double, String?) -> Unit,
) {
    // "Copiar última nota" (achado de auditoria, paridade com
    // openDuplicateManual()/nfe-client.tsx no site) -- reabre este MESMO
    // diálogo de "Nova NF-e", só que pré-preenchido com os campos da nota
    // mais recente (numero/dataEmissao inclusive, igual ao site: o usuário
    // ainda ajusta o que for preciso antes de salvar, nunca sobrescreve a
    // nota copiada).
    var numero by remember { mutableStateOf(initial?.numero ?: "") }
    var serie by remember { mutableStateOf(initial?.serie ?: "") }
    var tipo by remember { mutableStateOf(initial?.tipo?.takeIf { it in TIPO_OPTIONS } ?: "ENTRADA") }
    var emitenteNome by remember { mutableStateOf(initial?.emitenteNome ?: "") }
    var valorTotal by remember { mutableStateOf(initial?.let { valorParaCampo(it.valorTotal) } ?: "") }
    var dataEmissao by remember { mutableStateOf(initial?.dataEmissao?.let { isoDateOnly(it) } ?: "") }

    fun parseValor(s: String): Double =
        s.trim().replace(".", "").replace(",", ".").toDoubleOrNull() ?: s.trim().toDoubleOrNull() ?: 0.0

    val podeSalvar = numero.isNotBlank() && emitenteNome.isNotBlank() && valorTotal.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial != null) "Nova NF-e (copiada da última)" else "Nova NF-e (manual)") },
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

// "Repositório de XML" (Task #655/#644) -- diálogo de filtro por período
// (De/Até, ambos opcionais) antes de baixar o .zip com todos os XMLs no
// intervalo -- mesmo par de campos do site (nfe-client.tsx, "Baixar XMLs
// (lote)"), formato AAAA-MM-DD (mesmo critério de dataEmissao acima).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BaixarXmlLoteDialog(pending: Boolean, onDismiss: () -> Unit, onConfirm: (String?, String?) -> Unit) {
    var inicio by remember { mutableStateOf("") }
    var fim by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Baixar XMLs (lote)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Baixa um .zip com o XML de cada nota fiscal recebida e enviada (autorizada ou importada) no período. Deixe em branco pra baixar todas.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(value = inicio, onValueChange = { inicio = it }, label = { Text("De") }, placeholder = { Text("AAAA-MM-DD, opcional") }, modifier = Modifier.fillMaxWidth(), colors = appFieldColors())
                OutlinedTextField(value = fim, onValueChange = { fim = it }, label = { Text("Até") }, placeholder = { Text("AAAA-MM-DD, opcional") }, modifier = Modifier.fillMaxWidth(), colors = appFieldColors())
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(inicio.ifBlank { null }, fim.ifBlank { null }) }, enabled = !pending) {
                if (pending) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text("Baixar")
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
    // "Copiar última nota" (achado de auditoria, paridade com
    // duplicateSource/nfe-client.tsx) -- guarda a nota de origem quando o
    // diálogo é aberto pelo ícone Copiar (em vez do FAB "+"); null = diálogo
    // abre em branco, igual antes.
    var copiaOrigem by remember { mutableStateOf<InvoiceData?>(null) }
    // Alternância Linhas/Blocos (achado de auditoria, paridade com
    // mobileView/nfe-client.tsx) -- ver NfeViewMode acima.
    var viewMode by remember { mutableStateOf(NfeViewMode.BLOCOS) }
    var dialogErro by remember { mutableStateOf<String?>(null) }
    var confirmarExclusao by remember { mutableStateOf<InvoiceData?>(null) }
    var mensagemEmissao by remember { mutableStateOf<String?>(null) }
    // "Repositório de XML" (Task #655/#644) -- estado do diálogo De/Até e
    // mensagem de erro própria (sucesso não precisa de mensagem: o próprio
    // menu "Compartilhar" já confirma visualmente que o .zip foi gerado).
    var showXmlLote by remember { mutableStateOf(false) }
    var showCertificado by remember { mutableStateOf(false) }
    var lancAberto by remember { mutableStateOf(false) }
    var erroXmlLote by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { com.bragro.mobile.ui.domain.BackNavButton() },
                title = { androidx.compose.material3.ProvideTextStyle(androidx.compose.material3.LocalTextStyle.current.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)) {
                    // v21 -- ícone verde do módulo ao lado do título.
                    androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
                        com.bragro.mobile.ui.domain.IconeNegrito(androidx.compose.material.icons.Icons.Outlined.Receipt, contentDescription = null, tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurface, size = 22.dp, modifier = androidx.compose.ui.Modifier.padding(end = 8.dp, bottom = 3.dp))
 Text("NF-e", color = MaterialTheme.colorScheme.onSurface) 
                    }
                } },
                actions = { com.bragro.mobile.ui.domain.HomeNavButton() },
            )
        },
        // Ícones do topo e botão flutuante foram para o bloco Ações (padrão v7).
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
            } else {
                val totalValor = invoices.sumOf { it.valorTotal }
                val nEntrada = invoices.count { (it.tipo ?: "ENTRADA").uppercase() == "ENTRADA" }
                val nSaida = invoices.size - nEntrada
                val nAutorizadas = invoices.count { it.statusSefaz.orEmpty().uppercase().contains("AUTORIZ") }
                val ultimaNota = invoices.firstOrNull()
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(key = "painel") {
                        com.bragro.mobile.ui.domain.PainelMonitoramento(
                            com.bragro.mobile.ui.domain.PainelModulo(
                                titulo = "NF-e",
                                subtitulo = "${invoices.size} notas · ${formatoMoeda(totalValor)}",
                                kpis = listOf(
                                    com.bragro.mobile.ui.domain.PainelKpi("Notas", invoices.size.toDouble(), "numero", null, "registradas", "Lançamentos"),
                                    com.bragro.mobile.ui.domain.PainelKpi("Valor total", totalValor, "moeda", null, "soma das notas", "Lançamentos"),
                                    com.bragro.mobile.ui.domain.PainelKpi("Entradas", nEntrada.toDouble(), "numero", null, "compras", "Lançamentos"),
                                    com.bragro.mobile.ui.domain.PainelKpi("Saídas", nSaida.toDouble(), "numero", null, "$nAutorizadas autorizadas", "Lançamentos"),
                                ),
                                barras = emptyList(),
                            ),
                            onKpi = { lancAberto = true },
                        )
                    }
                    item(key = "acoes") {
                        com.bragro.mobile.ui.domain.AcoesEmAbas(
                            dados = {
                                com.bragro.mobile.ui.domain.LabeledIconButton(
                                    icon = if (viewMode == NfeViewMode.BLOCOS) Icons.Outlined.ViewList else Icons.Outlined.ViewAgenda,
                                    label = if (viewMode == NfeViewMode.BLOCOS) "Linhas" else "Blocos",
                                    onClick = { viewMode = if (viewMode == NfeViewMode.BLOCOS) NfeViewMode.LINHAS else NfeViewMode.BLOCOS; lancAberto = true },
                                )
                                com.bragro.mobile.ui.domain.LabeledIconButton(
                                    icon = Icons.AutoMirrored.Outlined.ViewList,
                                    label = "Lançamentos",
                                    active = lancAberto,
                                    onClick = { lancAberto = !lancAberto },
                                )
                            },
                            operacoes = {
                                com.bragro.mobile.ui.domain.LabeledIconButton(icon = Icons.Outlined.Add, label = "+ Nota", onClick = { copiaOrigem = null; showNovaNota = true })
                                com.bragro.mobile.ui.domain.LabeledIconButton(icon = Icons.Outlined.ContentCopy, label = "Copiar", onClick = { if (ultimaNota != null) { copiaOrigem = ultimaNota; showNovaNota = true } })
                                com.bragro.mobile.ui.domain.LabeledIconButton(icon = Icons.Outlined.VerifiedUser, label = "Certificado", onClick = { showCertificado = true })
                                com.bragro.mobile.ui.domain.LabeledIconButton(icon = Icons.Outlined.Refresh, label = "Atualizar", onClick = { viewModel.carregar() })
                            },
                            arquivos = {
                                com.bragro.mobile.ui.domain.LabeledIconButton(icon = Icons.Outlined.FolderZip, label = "XML lote", onClick = { showXmlLote = true })
                                com.bragro.mobile.ui.domain.ArquivosTabela("NF-e", listOf("Número", "Série", "Tipo", "Emitente", "Valor", "Emissão", "Status")) {
                                    invoices.map { listOf(it.numero, it.serie.orEmpty(), it.tipo.orEmpty(), it.emitenteNome.orEmpty(), formatoMoeda(it.valorTotal), it.dataEmissao?.take(10)?.let { d -> com.bragro.mobile.ui.domain.isoDateToBr(d) }.orEmpty(), it.statusSefaz ?: it.status) }
                                }
                            },
                        )
                    }
                    item(key = "lanc-header") {
                        com.bragro.mobile.ui.domain.CabecalhoLancamentos(total = invoices.size, aberto = lancAberto, titulo = "Notas") { lancAberto = !lancAberto }
                    }
                    if (invoices.isEmpty()) item {
                        Text("Nenhuma nota fiscal lançada ainda.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 16.dp))
                    }
                    if (lancAberto) items(invoices, key = { it.id }) { inv ->
                        if (viewMode == NfeViewMode.BLOCOS) {
                            InvoiceCard(
                                inv = inv,
                                pendingAction = pendingAction,
                                onEmitir = { viewModel.emitir(inv.id) { mensagem, _ -> mensagemEmissao = mensagem } },
                                onExcluir = { confirmarExclusao = inv },
                            )
                        } else {
                            Column {
                                InvoiceRow(
                                    inv = inv,
                                    pendingAction = pendingAction,
                                    onEmitir = { viewModel.emitir(inv.id) { mensagem, _ -> mensagemEmissao = mensagem } },
                                    onExcluir = { confirmarExclusao = inv },
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCertificado) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showCertificado = false },
            modifier = Modifier.fillMaxWidth(0.96f),
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            title = { Text("Certificado digital") },
            text = {
                Column(modifier = Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                    CertificadoDigitalCard()
                }
            },
            confirmButton = { TextButton(onClick = { showCertificado = false }) { Text("Fechar") } },
        )
    }

    if (showNovaNota) {
        NovaNotaDialog(
            pending = pendingAction == "create",
            initial = copiaOrigem,
            onDismiss = { if (pendingAction != "create") { showNovaNota = false; copiaOrigem = null } },
            onConfirm = { numero, serie, tipo, emitenteNome, valorTotal, dataEmissao ->
                viewModel.criar(numero, serie, tipo, emitenteNome, valorTotal, dataEmissao) { erroMsg ->
                    if (erroMsg == null) { showNovaNota = false; copiaOrigem = null } else dialogErro = erroMsg
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

    if (showXmlLote) {
        BaixarXmlLoteDialog(
            pending = pendingAction == "downloadLote",
            onDismiss = { if (pendingAction != "downloadLote") showXmlLote = false },
            onConfirm = { inicio, fim ->
                viewModel.baixarXmlLote(inicio, fim) { bytes, erro ->
                    if (bytes != null) {
                        showXmlLote = false
                        shareBinaryFile(context, bytes, "NFe-XMLs-lote.zip", "application/zip")
                    } else {
                        erroXmlLote = erro
                    }
                }
            },
        )
    }

    if (erroXmlLote != null) {
        AlertDialog(
            onDismissRequest = { erroXmlLote = null },
            title = { Text("Não foi possível baixar") },
            text = { Text(erroXmlLote ?: "") },
            confirmButton = { TextButton(onClick = { erroXmlLote = null }) { Text("Ok") } },
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

