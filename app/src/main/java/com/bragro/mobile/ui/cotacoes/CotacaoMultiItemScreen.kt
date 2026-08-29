package com.bragro.mobile.ui.cotacoes

import android.app.Application
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import com.bragro.mobile.ui.theme.Card
import com.bragro.mobile.ui.theme.appFieldColors
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bragro.mobile.data.local.LookupEntity
import com.bragro.mobile.data.model.CotacaoMultiItemItemData
import com.bragro.mobile.data.repo.ConfigRepository
import com.bragro.mobile.data.repo.CotacaoMultiItemRepository
import com.bragro.mobile.data.repo.RecordRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// "Novo modelo" de Cotações de Fornecedores (vários itens do MESMO
// fornecedor numa única submissão) -- pedido do usuário ("insira o novo
// modelo dos modulos cotaçoes e pedidos no app native"): réplica da tela web
// (cotacao-multi-item-button.tsx, task #234), que já substituiu o
// formulário "1 item só" no site. Esta tela vira o ÚNICO caminho pra CRIAR
// uma Cotação no app (editar continua na tela genérica de 1 item,
// DomainFormScreen.kt -- ver BRAgroNavHost.kt). Chama
// /api/mobile/cotacao-multi-item, que chama DIRETO
// createCotacaoMultiItemAction() no servidor. Mesmo padrão de tela/
// ViewModel já usado em NotaMultiItemScreen.kt/PedidoMultiItemScreen.kt.
//
// "Copiar último lançamento" (varredura de auditoria, pedido do usuário
// "implemente tudo" -- corrige a nota antiga deste comentário, que dizia
// "não existe endpoint mobile equivalente ainda": na verdade
// RecordRepository.mostRecent(domainId) já lê do cache local (Room) sem
// precisar de endpoint nenhum, mesmo mecanismo genérico do resto do app;
// só faltava esta tela própria -- fora do motor genérico de domínio --
// ligar ela mesma, igual replicado agora em PedidoMultiItemScreen.kt).

class CotacaoLinha {
    var categoria by mutableStateOf("")
    var item by mutableStateOf("")
    var unidade by mutableStateOf("")
    var quantidade by mutableStateOf("")
    var precoUnitario by mutableStateOf("")
    var prazoEntregaDias by mutableStateOf("")
}

private fun hojeIso(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
private fun hojeBr(): String = com.bragro.mobile.ui.domain.isoDateToBr(hojeIso())

private fun parseDecimal(s: String): Double =
    s.trim().replace(".", "").replace(",", ".").toDoubleOrNull()
        ?: s.trim().toDoubleOrNull()
        ?: 0.0

private fun millisToBrDate(millis: Long): String {
    val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
    cal.timeInMillis = millis
    val d = cal.get(java.util.Calendar.DAY_OF_MONTH)
    val mo = cal.get(java.util.Calendar.MONTH) + 1
    val y = cal.get(java.util.Calendar.YEAR)
    return "%02d/%02d/%04d".format(d, mo, y)
}

private fun brDateToMillisOrNull(br: String): Long? {
    val m = Regex("^(\\d{2})/(\\d{2})/(\\d{4})$").find(br.trim()) ?: return null
    val (d, mo, y) = m.destructured
    val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
    cal.clear()
    cal.set(y.toInt(), mo.toInt() - 1, d.toInt())
    return cal.timeInMillis
}

class CotacaoMultiItemViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = CotacaoMultiItemRepository(app)
    private val configRepository = ConfigRepository(app)
    private val recordRepository = RecordRepository(app)

    var categoriasOptions = mutableStateOf<List<LookupEntity>>(emptyList())
        private set
    var itensOptions = mutableStateOf<List<LookupEntity>>(emptyList())
        private set
    var unidadesOptions = mutableStateOf<List<LookupEntity>>(emptyList())
        private set
    var formasPgtoOptions = mutableStateOf<List<LookupEntity>>(emptyList())
        private set

    // Fornecedor aqui é TEXTO LIVRE (não dropdown de Base de Dados) -- mesmo
    // critério do site (cotacao-multi-item-button.tsx: <Input>, não
    // <Select>); diferente do Pedido, que usa "entidades_financeiro".
    var fornecedor by mutableStateOf("")
    var data by mutableStateOf(hojeBr())
    var condicaoPagamento by mutableStateOf<String?>(null)
    var validadeProposta by mutableStateOf("")
    var observacoes by mutableStateOf("")

    val linhas = mutableStateListOf(CotacaoLinha())

    var pending = mutableStateOf(false)
        private set
    var errorMessage = mutableStateOf<String?>(null)
        private set
    var successMessage = mutableStateOf<String?>(null)
        private set
    var copiando = mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            categoriasOptions.value = configRepository.lookupsByCategory("categorias_cotacao").sortedBy { it.label }
            itensOptions.value = configRepository.lookupsByCategory("itens_estoque").sortedBy { it.label }
            unidadesOptions.value = configRepository.lookupsByCategory("unidades").sortedBy { it.label }
            formasPgtoOptions.value = configRepository.lookupsByCategory("formas_pgto").sortedBy { it.label }
        }
    }

    fun addLinha() {
        linhas.add(CotacaoLinha())
    }

    fun removeLinha(i: Int) {
        if (linhas.size > 1) linhas.removeAt(i)
    }

    private fun linhasValidas() = linhas.filter { it.categoria.isNotBlank() && it.item.isNotBlank() && it.precoUnitario.isNotBlank() }

    fun podeSalvar(): Boolean = fornecedor.isNotBlank() && data.isNotBlank() && linhasValidas().isNotEmpty()

    fun reset() {
        fornecedor = ""
        data = hojeBr()
        condicaoPagamento = null
        validadeProposta = ""
        observacoes = ""
        linhas.clear(); linhas.add(CotacaoLinha())
        successMessage.value = null
        errorMessage.value = null
    }

    /** "Copiar último lançamento" -- busca a última proposta de cotação
     * lançada (qualquer fornecedor/item) no cache local e preenche o
     * cabeçalho + a primeira linha de item, mesmo padrão de
     * preencherComUltimo() em cotacao-multi-item-button.tsx (site). */
    fun preencherComUltimo() {
        viewModelScope.launch {
            copiando.value = true
            val last = recordRepository.mostRecent("cotacoesfornecedores")
            copiando.value = false
            if (last == null) {
                errorMessage.value = "Nenhuma cotação lançada ainda para copiar."
                return@launch
            }
            last["fornecedor"]?.let { fornecedor = it }
            last["data"]?.let { data = com.bragro.mobile.ui.domain.isoDateToBr(it) }
            last["condicaoPagamento"]?.let { condicaoPagamento = it }
            last["validadeProposta"]?.let { validadeProposta = com.bragro.mobile.ui.domain.isoDateToBr(it) }
            last["observacoes"]?.let { observacoes = it }
            val linha = CotacaoLinha()
            last["categoria"]?.let { linha.categoria = it }
            last["item"]?.let { linha.item = it }
            last["unidade"]?.let { linha.unidade = it }
            last["quantidade"]?.let { linha.quantidade = it }
            last["precoUnitario"]?.let { linha.precoUnitario = it }
            last["prazoEntregaDias"]?.let { linha.prazoEntregaDias = it }
            linhas.clear()
            linhas.add(linha)
            successMessage.value = null
            errorMessage.value = null
        }
    }

    fun submit() {
        if (!podeSalvar()) return
        val validas = linhasValidas()
        pending.value = true
        errorMessage.value = null
        viewModelScope.launch {
            val resultado = repository.criar(
                data = com.bragro.mobile.ui.domain.brDateToIso(data),
                fornecedor = fornecedor.trim(),
                condicaoPagamento = condicaoPagamento,
                validadeProposta = validadeProposta.takeIf { it.isNotBlank() }?.let { com.bragro.mobile.ui.domain.brDateToIso(it) },
                observacoes = observacoes.trim().ifBlank { null },
                itens = validas.map {
                    CotacaoMultiItemItemData(
                        categoria = it.categoria,
                        item = it.item,
                        quantidade = it.quantidade.takeIf { v -> v.isNotBlank() }?.let { v -> parseDecimal(v) },
                        unidade = it.unidade.ifBlank { null },
                        precoUnitario = parseDecimal(it.precoUnitario),
                        prazoEntregaDias = it.prazoEntregaDias.takeIf { v -> v.isNotBlank() }?.let { v -> parseDecimal(v) },
                    )
                },
            )
            pending.value = false
            if (resultado == null || !resultado.ok) {
                errorMessage.value = resultado?.error ?: "Erro ao lançar a cotação."
                return@launch
            }
            successMessage.value = "${resultado.count ?: validas.size} item(ns) lançado(s) para ${fornecedor.trim()}."
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StringDropdown(
    label: String,
    value: String?,
    options: List<String>,
    placeholder: String,
    allowEmpty: Boolean = false,
    onSelect: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = value ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            colors = appFieldColors(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (allowEmpty) {
                DropdownMenuItem(text = { Text(" ") }, onClick = { onSelect(null); expanded = false })
            }
            for (opt in options) {
                DropdownMenuItem(text = { Text(opt, maxLines = 1, overflow = TextOverflow.Ellipsis) }, onClick = { onSelect(opt); expanded = false })
            }
        }
    }
}

@Composable
private fun CotacaoLinhaCard(linha: CotacaoLinha, categoriasOptions: List<LookupEntity>, itensOptions: List<LookupEntity>, unidadesOptions: List<LookupEntity>, showRemove: Boolean, onRemove: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            StringDropdown(
                label = "Categoria *",
                value = categoriasOptions.firstOrNull { it.value == linha.categoria }?.label ?: linha.categoria.ifBlank { null },
                options = categoriasOptions.map { it.label },
                placeholder = "Categoria",
                onSelect = { picked -> linha.categoria = categoriasOptions.firstOrNull { it.label == picked }?.value ?: picked.orEmpty() },
            )
            StringDropdown(
                label = "Item *",
                value = itensOptions.firstOrNull { it.value == linha.item }?.label ?: linha.item.ifBlank { null },
                options = itensOptions.map { it.label },
                placeholder = "Selecione o item",
                onSelect = { picked -> linha.item = itensOptions.firstOrNull { it.label == picked }?.value ?: picked.orEmpty() },
            )
            StringDropdown(
                label = "Unidade",
                value = unidadesOptions.firstOrNull { it.value == linha.unidade }?.label ?: linha.unidade.ifBlank { null },
                options = unidadesOptions.map { it.label },
                placeholder = "Opcional",
                allowEmpty = true,
                onSelect = { picked -> linha.unidade = unidadesOptions.firstOrNull { it.label == picked }?.value ?: picked.orEmpty() },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = linha.quantidade,
                    onValueChange = { linha.quantidade = it },
                    label = { Text("Quantidade") },
                    placeholder = { Text("0") },
                    modifier = Modifier.weight(1f),
                    colors = appFieldColors(),
                )
                OutlinedTextField(
                    value = linha.precoUnitario,
                    onValueChange = { linha.precoUnitario = it },
                    label = { Text("Preço unit. (R$) *") },
                    placeholder = { Text("0,00") },
                    modifier = Modifier.weight(1f),
                    colors = appFieldColors(),
                )
            }
            OutlinedTextField(
                value = linha.prazoEntregaDias,
                onValueChange = { linha.prazoEntregaDias = it },
                label = { Text("Prazo entrega (dias)") },
                placeholder = { Text("Opcional") },
                modifier = Modifier.fillMaxWidth(),
                colors = appFieldColors(),
            )
            if (showRemove) {
                IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.Delete, contentDescription = "Remover item", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CotacaoMultiItemScreen(onBack: () -> Unit, viewModel: CotacaoMultiItemViewModel = viewModel()) {
    val categoriasOptions by viewModel.categoriasOptions
    val itensOptions by viewModel.itensOptions
    val unidadesOptions by viewModel.unidadesOptions
    val formasPgtoOptions by viewModel.formasPgtoOptions
    val pending by viewModel.pending
    val errorMessage by viewModel.errorMessage
    val successMessage by viewModel.successMessage
    val copiando by viewModel.copiando

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Nova Cotação", color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Clip, modifier = Modifier.basicMarquee())
                    }
                },
                navigationIcon = {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (successMessage != null) {
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Cotação lançada com sucesso", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(successMessage ?: "", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Button(onClick = { viewModel.reset() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Lançar outra cotação")
                }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    "Cada linha é UMA proposta de UM fornecedor. Lance 2+ propostas com a MESMA Categoria + Item pra comparar automaticamente -- Índice de Vantagem e Avaliação recalculam sozinhos.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            item {
                OutlinedButton(
                    onClick = { viewModel.preencherComUltimo() },
                    enabled = !copiando,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.padding(end = 6.dp).size(16.dp))
                    Text(if (copiando) "Copiando..." else "Copiar última cotação")
                }
            }
            if (errorMessage != null) {
                item { Text(errorMessage ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
            item {
                OutlinedTextField(
                    value = viewModel.fornecedor,
                    onValueChange = { viewModel.fornecedor = it },
                    label = { Text("Fornecedor *") },
                    placeholder = { Text("Nome do fornecedor") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = appFieldColors(),
                )
            }
            item {
                var showPicker by remember { mutableStateOf(false) }
                OutlinedTextField(
                    value = viewModel.data,
                    onValueChange = { viewModel.data = it },
                    label = { Text("Data *") },
                    placeholder = { Text("DD/MM/AAAA") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = appFieldColors(),
                    trailingIcon = {
                        IconButton(onClick = { showPicker = true }) {
                            Icon(Icons.Filled.CalendarMonth, contentDescription = "Escolher data", tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                )
                if (showPicker) {
                    val pickerState = rememberDatePickerState(
                        initialSelectedDateMillis = brDateToMillisOrNull(viewModel.data) ?: System.currentTimeMillis(),
                    )
                    DatePickerDialog(
                        onDismissRequest = { showPicker = false },
                        confirmButton = {
                            TextButton(onClick = {
                                pickerState.selectedDateMillis?.let { viewModel.data = millisToBrDate(it) }
                                showPicker = false
                            }) { Text("OK") }
                        },
                        dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancelar") } },
                    ) {
                        DatePicker(state = pickerState)
                    }
                }
            }
            item {
                StringDropdown(
                    label = "Condição de pagamento",
                    value = formasPgtoOptions.firstOrNull { it.value == viewModel.condicaoPagamento }?.label ?: viewModel.condicaoPagamento,
                    options = formasPgtoOptions.map { it.label },
                    placeholder = "Opcional",
                    allowEmpty = true,
                    onSelect = { picked -> viewModel.condicaoPagamento = formasPgtoOptions.firstOrNull { it.label == picked }?.value ?: picked },
                )
            }
            item {
                var showPicker by remember { mutableStateOf(false) }
                OutlinedTextField(
                    value = viewModel.validadeProposta,
                    onValueChange = { viewModel.validadeProposta = it },
                    label = { Text("Validade da proposta") },
                    placeholder = { Text("DD/MM/AAAA") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = appFieldColors(),
                    trailingIcon = {
                        IconButton(onClick = { showPicker = true }) {
                            Icon(Icons.Filled.CalendarMonth, contentDescription = "Escolher data", tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                )
                if (showPicker) {
                    val pickerState = rememberDatePickerState(
                        initialSelectedDateMillis = brDateToMillisOrNull(viewModel.validadeProposta) ?: System.currentTimeMillis(),
                    )
                    DatePickerDialog(
                        onDismissRequest = { showPicker = false },
                        confirmButton = {
                            TextButton(onClick = {
                                pickerState.selectedDateMillis?.let { viewModel.validadeProposta = millisToBrDate(it) }
                                showPicker = false
                            }) { Text("OK") }
                        },
                        dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancelar") } },
                    ) {
                        DatePicker(state = pickerState)
                    }
                }
            }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp)) }
            item { Text("Itens cotados *", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
            items(viewModel.linhas.size) { i ->
                CotacaoLinhaCard(
                    linha = viewModel.linhas[i],
                    categoriasOptions = categoriasOptions,
                    itensOptions = itensOptions,
                    unidadesOptions = unidadesOptions,
                    showRemove = viewModel.linhas.size > 1,
                    onRemove = { viewModel.removeLinha(i) },
                )
            }
            item {
                OutlinedButton(onClick = { viewModel.addLinha() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                    Text("Adicionar item")
                }
            }
            item {
                OutlinedTextField(
                    value = viewModel.observacoes,
                    onValueChange = { viewModel.observacoes = it },
                    label = { Text("Observações (opcional, vale para todos os itens)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    colors = appFieldColors(),
                )
            }
            item {
                Button(
                    onClick = { viewModel.submit() },
                    enabled = !pending && viewModel.podeSalvar(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (pending) CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                    else Text("Lançar cotação")
                }
            }
        }
    }
}
