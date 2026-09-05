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
import com.bragro.mobile.data.model.CotacaoComparacaoPropostaData
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

// Linha do modo "Comparar fornecedores" (task #404) -- inverso de
// CotacaoLinha acima: aqui é 1 item comum (categoria/item/data/quantidade/
// unidade ficam no cabeçalho, fora da linha) e cada linha é UMA proposta de
// fornecedor pra esse mesmo item.
class PropostaLinha {
    var fornecedor by mutableStateOf("")
    var precoUnitario by mutableStateOf("")
    var prazoEntregaDias by mutableStateOf("")
    var condicaoPagamento by mutableStateOf<String?>(null)
    var validadeProposta by mutableStateOf("")
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
    var entidadesOptions = mutableStateOf<List<LookupEntity>>(emptyList())
        private set

    // Fornecedor agora é dropdown de Base de Dados (pedido do usuário: "em
    // cotações campo fornecedores crie lista suspensa, tem que cadastrar
    // primeiro para acessar o campo") -- mesma categoria já usada em Pedido
    // ("entidades_financeiro"), replicando o site (cotacao-multi-item-
    // button.tsx). Antes era texto livre.
    var fornecedor by mutableStateOf("")
    var data by mutableStateOf(hojeBr())
    var condicaoPagamento by mutableStateOf<String?>(null)
    var validadeProposta by mutableStateOf("")
    var observacoes by mutableStateOf("")

    val linhas = mutableStateListOf(CotacaoLinha())

    // Estado do modo "Comparar fornecedores" (task #404) -- ver mesmo
    // raciocínio no site (cotacao-multi-item-button.tsx).
    var modo by mutableStateOf("itens")
    var categoriaComp by mutableStateOf("")
    var itemComp by mutableStateOf("")
    var dataComp by mutableStateOf(hojeBr())
    var quantidadeComp by mutableStateOf("")
    var unidadeComp by mutableStateOf("")
    var observacoesComp by mutableStateOf("")
    val propostas = mutableStateListOf(PropostaLinha(), PropostaLinha())

    var pending = mutableStateOf(false)
        private set
    var errorMessage = mutableStateOf<String?>(null)
        private set
    var successMessage = mutableStateOf<String?>(null)
        private set
    var copiando = mutableStateOf(false)
        private set

    fun addProposta() {
        propostas.add(PropostaLinha())
    }

    fun removeProposta(i: Int) {
        if (propostas.size > 1) propostas.removeAt(i)
    }

    private fun propostasValidas() = propostas.filter { it.fornecedor.isNotBlank() && it.precoUnitario.isNotBlank() }

    fun podeSalvarComp(): Boolean = categoriaComp.isNotBlank() && itemComp.isNotBlank() && dataComp.isNotBlank() && propostasValidas().isNotEmpty()

    fun submitComparacao() {
        if (!podeSalvarComp()) return
        val validas = propostasValidas()
        pending.value = true
        errorMessage.value = null
        viewModelScope.launch {
            val resultado = repository.criarComparacao(
                data = com.bragro.mobile.ui.domain.brDateToIso(dataComp),
                categoria = categoriaComp,
                item = itemComp,
                quantidade = quantidadeComp.takeIf { it.isNotBlank() }?.let { parseDecimal(it) },
                unidade = unidadeComp.ifBlank { null },
                observacoes = observacoesComp.trim().ifBlank { null },
                propostas = validas.map {
                    CotacaoComparacaoPropostaData(
                        fornecedor = it.fornecedor.trim(),
                        precoUnitario = parseDecimal(it.precoUnitario),
                        prazoEntregaDias = it.prazoEntregaDias.takeIf { v -> v.isNotBlank() }?.let { v -> parseDecimal(v) },
                        condicaoPagamento = it.condicaoPagamento,
                        validadeProposta = it.validadeProposta.takeIf { v -> v.isNotBlank() }?.let { v -> com.bragro.mobile.ui.domain.brDateToIso(v) },
                    )
                },
            )
            pending.value = false
            if (resultado == null || !resultado.ok) {
                errorMessage.value = resultado?.error ?: "Erro ao lançar as propostas."
                return@launch
            }
            successMessage.value = "${resultado.count ?: validas.size} proposta(s) lançada(s) para comparação."
        }
    }

    init {
        viewModelScope.launch {
            categoriasOptions.value = configRepository.lookupsByCategory("categorias_cotacao").sortedBy { it.label }
            itensOptions.value = configRepository.lookupsByCategory("itens_estoque").sortedBy { it.label }
            unidadesOptions.value = configRepository.lookupsByCategory("unidades").sortedBy { it.label }
            formasPgtoOptions.value = configRepository.lookupsByCategory("formas_pgto").sortedBy { it.label }
            entidadesOptions.value = configRepository.lookupsByCategory("entidades_financeiro").sortedBy { it.label }
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
        categoriaComp = ""
        itemComp = ""
        dataComp = hojeBr()
        quantidadeComp = ""
        unidadeComp = ""
        observacoesComp = ""
        propostas.clear(); propostas.add(PropostaLinha()); propostas.add(PropostaLinha())
        successMessage.value = null
        errorMessage.value = null
    }

    /** Mesma ideia de preencherComUltimo() abaixo, mas pro modo "Comparar
     * fornecedores" (task #404): preenche só o cabeçalho comum (categoria/
     * item/data/quantidade/unidade/observações), já que a lista de
     * propostas em si é nova a cada comparação -- pedido do usuário
     * ("coloque um ícone copiar do lado superior direito" também na aba
     * Comparar fornecedores, mesmo ícone único do topo da tela). Site não
     * tem equivalente pra este modo (cotacao-multi-item-button.tsx só copia
     * no modo "itens") -- funcionalidade nova, exclusiva do app. */
    fun preencherComparacaoComUltimo() {
        viewModelScope.launch {
            copiando.value = true
            val last = recordRepository.mostRecent("cotacoesfornecedores")
            copiando.value = false
            if (last == null) {
                errorMessage.value = "Nenhuma cotação lançada ainda para copiar."
                return@launch
            }
            last["categoria"]?.let { categoriaComp = it }
            last["item"]?.let { itemComp = it }
            last["data"]?.let { dataComp = com.bragro.mobile.ui.domain.isoDateToBr(it) }
            last["quantidade"]?.let { quantidadeComp = it }
            last["unidade"]?.let { unidadeComp = it }
            last["observacoes"]?.let { observacoesComp = it }
            successMessage.value = null
            errorMessage.value = null
        }
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
    modifier: Modifier = Modifier,
    onSelect: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
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
            // Blocos individuais em pares (pedido do usuário: "crie blocos
            // individuais, se der pra colocar dois blocos na mesma linha sem
            // cortar palavras coloque") -- Categoria/Item ficam sozinhos
            // (rótulos de lista suspensa podem ser longos), Unidade+
            // Quantidade e Preço+Prazo são curtos e cabem 2 por linha.
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StringDropdown(
                    label = "Unidade",
                    value = unidadesOptions.firstOrNull { it.value == linha.unidade }?.label ?: linha.unidade.ifBlank { null },
                    options = unidadesOptions.map { it.label },
                    placeholder = "Opcional",
                    allowEmpty = true,
                    onSelect = { picked -> linha.unidade = unidadesOptions.firstOrNull { it.label == picked }?.value ?: picked.orEmpty() },
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = linha.quantidade,
                    onValueChange = { linha.quantidade = it },
                    label = { Text("Quantidade") },
                    placeholder = { Text("0") },
                    modifier = Modifier.weight(1f),
                    colors = appFieldColors(),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = linha.precoUnitario,
                    onValueChange = { linha.precoUnitario = it },
                    label = { Text("Preço unit. (R$) *") },
                    placeholder = { Text("0,00") },
                    modifier = Modifier.weight(1f),
                    colors = appFieldColors(),
                )
                OutlinedTextField(
                    value = linha.prazoEntregaDias,
                    onValueChange = { linha.prazoEntregaDias = it },
                    label = { Text("Prazo (dias)") },
                    placeholder = { Text("Opcional") },
                    modifier = Modifier.weight(1f),
                    colors = appFieldColors(),
                )
            }
            if (showRemove) {
                IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.Delete, contentDescription = "Remover item", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

// Card de UMA proposta de fornecedor no modo "Comparar fornecedores" (task
// #404) -- inverso de CotacaoLinhaCard acima: aqui não tem categoria/item
// (ficam no cabeçalho comum), só fornecedor + condições da proposta.
@Composable
private fun PropostaCard(proposta: PropostaLinha, entidadesOptions: List<LookupEntity>, formasPgtoOptions: List<LookupEntity>, showRemove: Boolean, onRemove: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Fornecedor agora é dropdown de Base de Dados (pedido do
            // usuário: "em cotações campo fornecedores crie lista suspensa,
            // tem que cadastrar primeiro para acessar o campo") -- antes era
            // texto livre.
            StringDropdown(
                label = "Fornecedor *",
                value = entidadesOptions.firstOrNull { it.value == proposta.fornecedor }?.label ?: proposta.fornecedor.ifBlank { null },
                options = entidadesOptions.map { it.label },
                placeholder = "Selecione o fornecedor",
                onSelect = { picked -> proposta.fornecedor = entidadesOptions.firstOrNull { it.label == picked }?.value ?: picked.orEmpty() },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = proposta.precoUnitario,
                    onValueChange = { proposta.precoUnitario = it },
                    label = { Text("Preço unit. (R$) *") },
                    placeholder = { Text("0,00") },
                    modifier = Modifier.weight(1f),
                    colors = appFieldColors(),
                )
                OutlinedTextField(
                    value = proposta.prazoEntregaDias,
                    onValueChange = { proposta.prazoEntregaDias = it },
                    label = { Text("Prazo (dias)") },
                    placeholder = { Text("Opcional") },
                    modifier = Modifier.weight(1f),
                    colors = appFieldColors(),
                )
            }
            // Blocos individuais em pares (pedido do usuário: "crie blocos
            // individuais, se der pra colocar dois blocos na mesma linha sem
            // cortar palavras coloque") -- Condição de pagamento + Validade
            // são valores curtos, cabem lado a lado igual Preço+Prazo acima.
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StringDropdown(
                    label = "Condição pgto.",
                    value = formasPgtoOptions.firstOrNull { it.value == proposta.condicaoPagamento }?.label ?: proposta.condicaoPagamento,
                    options = formasPgtoOptions.map { it.label },
                    placeholder = "Opcional",
                    allowEmpty = true,
                    onSelect = { picked -> proposta.condicaoPagamento = formasPgtoOptions.firstOrNull { it.label == picked }?.value ?: picked },
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = proposta.validadeProposta,
                    onValueChange = { proposta.validadeProposta = it },
                    label = { Text("Validade") },
                    placeholder = { Text("DD/MM/AAAA") },
                    modifier = Modifier.weight(1f),
                    colors = appFieldColors(),
                )
            }
            if (showRemove) {
                IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.Delete, contentDescription = "Remover proposta", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
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
    val entidadesOptions by viewModel.entidadesOptions
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
                // Ícone de copiar no topo direito -- pedido do usuário ("coloque
                // um ícone copiar do lado superior direito" nas duas abas, aqui
                // e em Comparar fornecedores), mesmo padrão já usado em
                // PedidoMultiItemScreen.kt: substitui o botão largo "Copiar
                // última cotação" que só existia no modo "itens" (ver comentário
                // no botão removido abaixo) -- um ícone só, sempre visível,
                // que dispara a função certa pro modo ativo no momento.
                actions = {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        IconButton(
                            onClick = { if (viewModel.modo == "itens") viewModel.preencherComUltimo() else viewModel.preencherComparacaoComUltimo() },
                            enabled = !copiando,
                        ) {
                            if (copiando) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Filled.ContentCopy, contentDescription = "Copiar última cotação", tint = MaterialTheme.colorScheme.primary)
                            }
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
            // Alternador de modo (task #404, pedido do usuário: "múltiplos
            // fornecedores por operação") -- mesmo padrão do site
            // (cotacao-multi-item-button.tsx). "Vários itens" é o modo
            // original (1 fornecedor, N itens); "Comparar fornecedores" é o
            // novo (1 item, N fornecedores lado a lado).
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { viewModel.modo = "itens" },
                        modifier = Modifier.weight(1f),
                        colors = if (viewModel.modo == "itens") androidx.compose.material3.ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) else androidx.compose.material3.ButtonDefaults.outlinedButtonColors(),
                    ) {
                        Text("Vários itens", style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedButton(
                        onClick = { viewModel.modo = "fornecedores" },
                        modifier = Modifier.weight(1f),
                        colors = if (viewModel.modo == "fornecedores") androidx.compose.material3.ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) else androidx.compose.material3.ButtonDefaults.outlinedButtonColors(),
                    ) {
                        Text("Comparar fornecedores", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (errorMessage != null) {
                item { Text(errorMessage ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
            if (viewModel.modo == "itens") {
            item {
                Text(
                    "Cada linha é UMA proposta de UM fornecedor. Lance 2+ propostas com a MESMA Categoria + Item pra comparar automaticamente -- Índice de Vantagem e Avaliação recalculam sozinhos.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            item {
                // Fornecedor agora é dropdown de Base de Dados (pedido do
                // usuário: "em cotações campo fornecedores crie lista
                // suspensa, tem que cadastrar primeiro para acessar o
                // campo") -- antes era texto livre.
                StringDropdown(
                    label = "Fornecedor *",
                    value = entidadesOptions.firstOrNull { it.value == viewModel.fornecedor }?.label ?: viewModel.fornecedor.ifBlank { null },
                    options = entidadesOptions.map { it.label },
                    placeholder = "Selecione o fornecedor",
                    onSelect = { picked -> viewModel.fornecedor = entidadesOptions.firstOrNull { it.label == picked }?.value ?: picked.orEmpty() },
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
            } else {
            // Modo "Comparar fornecedores" (task #404) -- 1 item comum
            // (categoria/item/data/quantidade/unidade), N propostas de
            // fornecedores diferentes lado a lado.
            item {
                // Instrução resumida -- pedido do usuário ("resuma as
                // instruções").
                Text(
                    "Descreva o item uma vez e lance o preço de cada fornecedor -- entram no mesmo grupo de comparação.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            item {
                StringDropdown(
                    label = "Categoria *",
                    value = categoriasOptions.firstOrNull { it.value == viewModel.categoriaComp }?.label ?: viewModel.categoriaComp.ifBlank { null },
                    options = categoriasOptions.map { it.label },
                    placeholder = "Categoria",
                    onSelect = { picked -> viewModel.categoriaComp = categoriasOptions.firstOrNull { it.label == picked }?.value ?: picked.orEmpty() },
                )
            }
            item {
                StringDropdown(
                    label = "Item *",
                    value = itensOptions.firstOrNull { it.value == viewModel.itemComp }?.label ?: viewModel.itemComp.ifBlank { null },
                    options = itensOptions.map { it.label },
                    placeholder = "Selecione o item",
                    onSelect = { picked -> viewModel.itemComp = itensOptions.firstOrNull { it.label == picked }?.value ?: picked.orEmpty() },
                )
            }
            item {
                var showPicker by remember { mutableStateOf(false) }
                OutlinedTextField(
                    value = viewModel.dataComp,
                    onValueChange = { viewModel.dataComp = it },
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
                        initialSelectedDateMillis = brDateToMillisOrNull(viewModel.dataComp) ?: System.currentTimeMillis(),
                    )
                    DatePickerDialog(
                        onDismissRequest = { showPicker = false },
                        confirmButton = {
                            TextButton(onClick = {
                                pickerState.selectedDateMillis?.let { viewModel.dataComp = millisToBrDate(it) }
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
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = viewModel.quantidadeComp,
                        onValueChange = { viewModel.quantidadeComp = it },
                        label = { Text("Quantidade") },
                        placeholder = { Text("Opcional") },
                        modifier = Modifier.weight(1f),
                        colors = appFieldColors(),
                    )
                }
            }
            item {
                StringDropdown(
                    label = "Unidade",
                    value = unidadesOptions.firstOrNull { it.value == viewModel.unidadeComp }?.label ?: viewModel.unidadeComp.ifBlank { null },
                    options = unidadesOptions.map { it.label },
                    placeholder = "Opcional",
                    allowEmpty = true,
                    onSelect = { picked -> viewModel.unidadeComp = unidadesOptions.firstOrNull { it.label == picked }?.value ?: picked.orEmpty() },
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp)) }
            item { Text("Propostas dos fornecedores *", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
            items(viewModel.propostas.size) { i ->
                PropostaCard(
                    proposta = viewModel.propostas[i],
                    entidadesOptions = entidadesOptions,
                    formasPgtoOptions = formasPgtoOptions,
                    showRemove = viewModel.propostas.size > 1,
                    onRemove = { viewModel.removeProposta(i) },
                )
            }
            item {
                OutlinedButton(onClick = { viewModel.addProposta() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                    Text("Adicionar fornecedor")
                }
            }
            item {
                OutlinedTextField(
                    value = viewModel.observacoesComp,
                    onValueChange = { viewModel.observacoesComp = it },
                    label = { Text("Observações (opcional, vale para todas as propostas)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    colors = appFieldColors(),
                )
            }
            item {
                Button(
                    onClick = { viewModel.submitComparacao() },
                    enabled = !pending && viewModel.podeSalvarComp(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (pending) CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                    else Text("Lançar propostas")
                }
            }
            }
        }
    }
}
