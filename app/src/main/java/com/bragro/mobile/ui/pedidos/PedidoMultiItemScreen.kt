package com.bragro.mobile.ui.pedidos

import android.app.Application
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Surface
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
import com.bragro.mobile.data.model.PedidoMultiItemItemData
import com.bragro.mobile.data.repo.ConfigRepository
import com.bragro.mobile.data.repo.PedidoMultiItemRepository
import com.bragro.mobile.data.repo.RecordRepository
import kotlinx.coroutines.launch

// "Novo modelo" de Pedidos (vários itens no mesmo lançamento) -- pedido do
// usuário ("insira o novo modelo dos modulos cotaçoes e pedidos no app
// native"): réplica da tela web (pedido-multi-item-button.tsx, task #235),
// que já substituiu o formulário "1 item só" no site -- um Pedido É
// inerentemente "1 ou mais itens", então esta tela agora é o ÚNICO caminho
// pra CRIAR um Pedido no app (editar um Pedido existente continua na tela
// genérica de 1 item, DomainFormScreen.kt, já que "vários itens de uma vez"
// só faz sentido ao criar -- ver BRAgroNavHost.kt). Chama
// /api/mobile/pedido-multi-item, que chama DIRETO
// createPedidoMultiItemAction() no servidor -- mesmo motor que o site usa.
// Mesmo padrão de tela/ViewModel já usado em NotaMultiItemScreen.kt.
//
// "Copiar último lançamento" (varredura de auditoria, pedido do usuário
// "implemente tudo"): usa RecordRepository.mostRecent("pedidos"), que já lê
// do cache local (Room) sem precisar de nenhum endpoint novo -- mesmo
// mecanismo genérico usado no resto do app (Task #51/#77), só que essa tela
// própria (fora do motor genérico de domínio) precisava ligar ela mesma.

class PedidoLinha {
    var categoria by mutableStateOf("")
    var item by mutableStateOf("")
    var unidade by mutableStateOf("")
    var qtdPedida by mutableStateOf("")
    var qtdEntregue by mutableStateOf("")
}

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

class PedidoMultiItemViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = PedidoMultiItemRepository(app)
    private val configRepository = ConfigRepository(app)
    private val recordRepository = RecordRepository(app)

    var setoresOptions = mutableStateOf<List<LookupEntity>>(emptyList())
        private set
    var fornecedoresOptions = mutableStateOf<List<LookupEntity>>(emptyList())
        private set
    var categoriasOptions = mutableStateOf<List<LookupEntity>>(emptyList())
        private set
    var itensOptions = mutableStateOf<List<LookupEntity>>(emptyList())
        private set
    var unidadesOptions = mutableStateOf<List<LookupEntity>>(emptyList())
        private set
    var safrasOptions = mutableStateOf<List<LookupEntity>>(emptyList())
        private set
    var culturasOptions = mutableStateOf<List<LookupEntity>>(emptyList())
        private set

    var noPedido by mutableStateOf("")
    var nf by mutableStateOf("")
    var setor by mutableStateOf<String?>(null)
    var fornecedor by mutableStateOf<String?>(null)
    var safra by mutableStateOf<String?>(null)
    var cultura by mutableStateOf<String?>(null)
    // Opcional (diferente de "Data de emissão" da Nota) -- fica em branco até
    // o usuário escolher, mesmo critério do "allowEmpty" nos dropdowns.
    var dataEntrega by mutableStateOf("")

    val linhas = mutableStateListOf(PedidoLinha())

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
            setoresOptions.value = configRepository.lookupsByCategory("setores").sortedBy { it.label }
            fornecedoresOptions.value = configRepository.lookupsByCategory("entidades_financeiro").sortedBy { it.label }
            categoriasOptions.value = configRepository.lookupsByCategory("categorias_estoque").sortedBy { it.label }
            itensOptions.value = configRepository.lookupsByCategory("itens_estoque").sortedBy { it.label }
            unidadesOptions.value = configRepository.lookupsByCategory("unidades").sortedBy { it.label }
            safrasOptions.value = configRepository.lookupsByCategory("safras").sortedBy { it.label }
            culturasOptions.value = configRepository.lookupsByCategory("culturas").sortedBy { it.label }
        }
    }

    fun addLinha() {
        linhas.add(PedidoLinha())
    }

    fun removeLinha(i: Int) {
        if (linhas.size > 1) linhas.removeAt(i)
    }

    private fun linhasValidas() = linhas.filter { it.item.isNotBlank() && it.qtdPedida.isNotBlank() }

    fun podeSalvar(): Boolean = noPedido.isNotBlank() && linhasValidas().isNotEmpty()

    fun reset() {
        noPedido = ""; nf = ""
        setor = null; fornecedor = null; safra = null; cultura = null
        dataEntrega = ""
        linhas.clear(); linhas.add(PedidoLinha())
        successMessage.value = null
        errorMessage.value = null
    }

    /** "Copiar último lançamento" -- busca o último Pedido lançado (qualquer
     * fornecedor/item) no cache local e preenche o cabeçalho + a primeira
     * linha de item, mesmo padrão de preencherComUltimo() em
     * pedido-multi-item-button.tsx (site). */
    fun preencherComUltimo() {
        viewModelScope.launch {
            copiando.value = true
            val last = recordRepository.mostRecent("pedidos")
            copiando.value = false
            if (last == null) {
                errorMessage.value = "Nenhum pedido lançado ainda para copiar."
                return@launch
            }
            last["noPedido"]?.let { noPedido = it }
            last["setor"]?.let { setor = it }
            last["fornecedor"]?.let { fornecedor = it }
            last["safra"]?.let { safra = it }
            last["cultura"]?.let { cultura = it }
            last["dataEntrega"]?.let { dataEntrega = com.bragro.mobile.ui.domain.isoDateToBr(it) }
            last["nf"]?.let { nf = it }
            val linha = PedidoLinha()
            last["categoria"]?.let { linha.categoria = it }
            last["item"]?.let { linha.item = it }
            last["unidade"]?.let { linha.unidade = it }
            last["qtdPedida"]?.let { linha.qtdPedida = it }
            last["qtdEntregue"]?.let { linha.qtdEntregue = it }
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
                noPedido = noPedido.trim(),
                setor = setor,
                fornecedor = fornecedor,
                safra = safra,
                cultura = cultura,
                dataEntrega = dataEntrega.takeIf { it.isNotBlank() }?.let { com.bragro.mobile.ui.domain.brDateToIso(it) },
                nf = nf.trim().ifBlank { null },
                itens = validas.map {
                    PedidoMultiItemItemData(
                        categoria = it.categoria.ifBlank { null },
                        item = it.item,
                        unidade = it.unidade.ifBlank { null },
                        qtdPedida = parseDecimal(it.qtdPedida),
                        qtdEntregue = it.qtdEntregue.takeIf { v -> v.isNotBlank() }?.let { v -> parseDecimal(v) },
                    )
                },
            )
            pending.value = false
            if (resultado == null || !resultado.ok) {
                errorMessage.value = resultado?.error ?: "Erro ao lançar o pedido."
                return@launch
            }
            successMessage.value = "${resultado.count ?: validas.size} item(ns) lançado(s) no pedido ${noPedido.trim()}."
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

// Bloco individual por campo dentro de cada item -- pedido do usuário
// ("crie blocos individuais" nos itens do pedido). Scrim (onSurface com
// alpha baixo) em vez de um papel de cor fixo: cria contraste com QUALQUER
// fundo por trás (o Card pai aqui é branco/surface), então nunca "some"
// igual aconteceu com surfaceVariant em Cotações (ver mesmo comentário em
// CotacaoMultiItemScreen.kt).
@Composable
private fun ItemFieldBlock(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
        shape = MaterialTheme.shapes.small,
    ) {
        Box(modifier = Modifier.padding(6.dp)) {
            content()
        }
    }
}

@Composable
private fun PedidoLinhaCard(linha: PedidoLinha, categoriasOptions: List<LookupEntity>, itensOptions: List<LookupEntity>, unidadesOptions: List<LookupEntity>, showRemove: Boolean, onRemove: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ItemFieldBlock {
                StringDropdown(
                    label = "Categoria",
                    value = categoriasOptions.firstOrNull { it.value == linha.categoria }?.label ?: linha.categoria.ifBlank { null },
                    options = categoriasOptions.map { it.label },
                    placeholder = "Opcional",
                    allowEmpty = true,
                    onSelect = { picked -> linha.categoria = categoriasOptions.firstOrNull { it.label == picked }?.value ?: picked.orEmpty() },
                )
            }
            ItemFieldBlock {
                StringDropdown(
                    label = "Item *",
                    value = itensOptions.firstOrNull { it.value == linha.item }?.label ?: linha.item.ifBlank { null },
                    options = itensOptions.map { it.label },
                    placeholder = "Selecione o item",
                    onSelect = { picked -> linha.item = itensOptions.firstOrNull { it.label == picked }?.value ?: picked.orEmpty() },
                )
            }
            ItemFieldBlock {
                StringDropdown(
                    label = "Unidade",
                    value = unidadesOptions.firstOrNull { it.value == linha.unidade }?.label ?: linha.unidade.ifBlank { null },
                    options = unidadesOptions.map { it.label },
                    placeholder = "Opcional",
                    allowEmpty = true,
                    onSelect = { picked -> linha.unidade = unidadesOptions.firstOrNull { it.label == picked }?.value ?: picked.orEmpty() },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ItemFieldBlock(modifier = Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = linha.qtdPedida,
                        onValueChange = { linha.qtdPedida = it },
                        label = { Text("Qtd. pedida *") },
                        placeholder = { Text("0") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = appFieldColors(),
                    )
                }
                ItemFieldBlock(modifier = Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = linha.qtdEntregue,
                        onValueChange = { linha.qtdEntregue = it },
                        label = { Text("Qtd. entregue") },
                        placeholder = { Text("0") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = appFieldColors(),
                    )
                }
            }
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
fun PedidoMultiItemScreen(onBack: () -> Unit, viewModel: PedidoMultiItemViewModel = viewModel()) {
    val setoresOptions by viewModel.setoresOptions
    val fornecedoresOptions by viewModel.fornecedoresOptions
    val categoriasOptions by viewModel.categoriasOptions
    val itensOptions by viewModel.itensOptions
    val unidadesOptions by viewModel.unidadesOptions
    val safrasOptions by viewModel.safrasOptions
    val culturasOptions by viewModel.culturasOptions
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
                        Text("Novo Pedido", color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Clip, modifier = Modifier.basicMarquee())
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
                // Ícone de copiar no topo direito -- pedido do usuário ("na
                // imagem 4, crie um icone copiar do lado direito superior e
                // exclua o botão copiar ultimo pedido"): substitui o botão
                // largo "Copiar último pedido" que ocupava uma linha
                // inteira. Mesmo destino (preencherComUltimo), só muda onde
                // o usuário aciona.
                actions = {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        IconButton(onClick = { viewModel.preencherComUltimo() }, enabled = !copiando) {
                            if (copiando) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Filled.ContentCopy, contentDescription = "Copiar último pedido", tint = MaterialTheme.colorScheme.primary)
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
                        Text("Pedido lançado com sucesso", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(successMessage ?: "", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Button(onClick = { viewModel.reset() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Lançar outro pedido")
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
                // Aviso resumido -- pedido do usuário ("resuma esses
                // avisos"). O comportamento completo (Qtd. entregue vira
                // entrada em Estoque automaticamente) continua valendo, só a
                // explicação ficou mais curta.
                Text(
                    "Qtd. entregue já lança automaticamente como entrada em Estoque.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            // Botão largo "Copiar último pedido" removido -- virou o ícone
            // de copiar no topo direito da TopAppBar (ver actions acima).
            if (errorMessage != null) {
                item { Text(errorMessage ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
            // Campos do cabeçalho em pares (2 por linha) em vez de 1 por
            // linha -- pedido do usuário ("separe os campos como os acima
            // na mesma imagem"). Data de entrega fica sozinha na própria
            // linha por causa do seletor de calendário (precisa de mais
            // espaço horizontal pro ícone).
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = viewModel.noPedido,
                        onValueChange = { viewModel.noPedido = it },
                        label = { Text("Nº do pedido *") },
                        placeholder = { Text("Ex.: 12345") },
                        modifier = Modifier.weight(1f),
                        colors = appFieldColors(),
                    )
                    OutlinedTextField(
                        value = viewModel.nf,
                        onValueChange = { viewModel.nf = it },
                        label = { Text("NF") },
                        placeholder = { Text("Opcional") },
                        modifier = Modifier.weight(1f),
                        colors = appFieldColors(),
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        StringDropdown(
                            label = "Setor",
                            value = setoresOptions.firstOrNull { it.value == viewModel.setor }?.label ?: viewModel.setor,
                            options = setoresOptions.map { it.label },
                            placeholder = "Opcional",
                            allowEmpty = true,
                            onSelect = { picked -> viewModel.setor = setoresOptions.firstOrNull { it.label == picked }?.value ?: picked },
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        StringDropdown(
                            label = "Fornecedor",
                            value = fornecedoresOptions.firstOrNull { it.value == viewModel.fornecedor }?.label ?: viewModel.fornecedor,
                            options = fornecedoresOptions.map { it.label },
                            placeholder = "Opcional",
                            allowEmpty = true,
                            onSelect = { picked -> viewModel.fornecedor = fornecedoresOptions.firstOrNull { it.label == picked }?.value ?: picked },
                        )
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        StringDropdown(
                            label = "Safra",
                            value = safrasOptions.firstOrNull { it.value == viewModel.safra }?.label ?: viewModel.safra,
                            options = safrasOptions.map { it.label },
                            placeholder = "Opcional",
                            allowEmpty = true,
                            onSelect = { picked -> viewModel.safra = safrasOptions.firstOrNull { it.label == picked }?.value ?: picked },
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        StringDropdown(
                            label = "Cultura",
                            value = culturasOptions.firstOrNull { it.value == viewModel.cultura }?.label ?: viewModel.cultura,
                            options = culturasOptions.map { it.label },
                            placeholder = "Opcional",
                            allowEmpty = true,
                            onSelect = { picked -> viewModel.cultura = culturasOptions.firstOrNull { it.label == picked }?.value ?: picked },
                        )
                    }
                }
            }
            item {
                var showPicker by remember { mutableStateOf(false) }
                OutlinedTextField(
                    value = viewModel.dataEntrega,
                    onValueChange = { viewModel.dataEntrega = it },
                    label = { Text("Data de entrega") },
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
                        initialSelectedDateMillis = brDateToMillisOrNull(viewModel.dataEntrega) ?: System.currentTimeMillis(),
                    )
                    DatePickerDialog(
                        onDismissRequest = { showPicker = false },
                        confirmButton = {
                            TextButton(onClick = {
                                pickerState.selectedDateMillis?.let { viewModel.dataEntrega = millisToBrDate(it) }
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
            item { Text("Itens do pedido *", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
            items(viewModel.linhas.size) { i ->
                PedidoLinhaCard(
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
                Button(
                    onClick = { viewModel.submit() },
                    enabled = !pending && viewModel.podeSalvar(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (pending) CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                    else Text("Lançar pedido")
                }
            }
        }
    }
}
