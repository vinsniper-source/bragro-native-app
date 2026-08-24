package com.bragro.mobile.ui.nfe

import android.app.Application
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bragro.mobile.data.local.FarmEntity
import com.bragro.mobile.data.local.LookupEntity
import com.bragro.mobile.data.model.NotaMultiItemItemData
import com.bragro.mobile.data.repo.ConfigRepository
import com.bragro.mobile.data.repo.NotaMultiItemRepository
import com.bragro.mobile.data.repo.RecordRepository
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Gap encontrado na auditoria módulo-a-módulo contra o site (pedido do
// usuário "implemente tudo que falta ainda para o app native da
// plataforma"): réplica do diálogo web "Lançar nota com itens"
// (nota-multi-item-button.tsx), como tela própria em vez de diálogo -- mesmo
// critério já usado pro Importar XML (NfeImportScreen.kt), porque uma lista
// dinâmica de itens não cabe bem num AlertDialog pequeno. Chama
// /api/mobile/nota-multi-item, que chama DIRETO criarNotaComItensAction() no
// servidor -- mesmo motor que o site usa (1 Invoice + N InvoiceItem, baixa
// automática no Estoque, 1 lançamento no Financeiro). Nenhuma lógica de
// negócio duplicada em Kotlin.
//
// "Copiar último lançamento" (varredura de auditoria, pedido do usuário
// "implemente tudo"): no SITE, o cabeçalho desta tela (Doc/NF, Data, Local,
// Entidade) vem do RecordForm genérico do domínio "financeiro" LOGO ACIMA
// (ver nota-multi-item-button.tsx, "Quinta"/"Sexta rodada"), que já tem seu
// próprio "Copiar último lançamento" -- não precisa de nada extra ali. Aqui
// no app, porém, esta tela é INDEPENDENTE (não embutida dentro de um
// formulário genérico de Financeiro), então ganha seu próprio botão,
// buscando o último registro do domínio "financeiro" no cache local
// (RecordRepository.mostRecent) e traduzindo os nomes de campo
// equivalentes: docNf->numero, data->dataEmissao, local->fazendaDestino,
// entidade->emitenteNome. Só o cabeçalho é copiado (itens não têm
// equivalente 1:1 num lançamento de Financeiro genérico).

/** Uma linha de item digitada -- campos em texto (não Double) pra aceitar
 * digitação livre (vírgula/ponto), convertidos só na hora de enviar, mesmo
 * critério do site (LinhaItem lá usa string também). */
class NotaMultiItemLinha {
    var descricao by mutableStateOf("")
    var unidade by mutableStateOf("")
    var quantidade by mutableStateOf("")
    var valorUnitario by mutableStateOf("")
}

private fun hojeIso(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

// Campo de data desta tela ainda mostrava/pedia "AAAA-MM-DD" -- pedido do
// usuário ("em lançar notas com itens o campo data não está como padrão
// Brasil"): todo o resto do app já converteu pra DD/MM/AAAA (ver
// isoDateToBr/brDateToIso em StatusStyle.kt e o campo "date" de
// DomainFormScreen.kt), essa tela ficou de fora por ser um formulário
// próprio (fora do motor genérico de domínio). dataEmissao no ViewModel
// passa a guardar o texto BR (like os demais campos de DomainFormScreen);
// só vira ISO na hora de montar o corpo enviado pro servidor (submit()).
private fun hojeBr(): String = com.bragro.mobile.ui.domain.isoDateToBr(hojeIso())

// Mesmo par de conversão do DatePicker usado em DomainFormScreen.kt (fica
// duplicado aqui de propósito -- os dois arquivos já duplicam
// formatMoneyBrl entre si, mesmo critério de não criar acoplamento entre
// telas por um helper tão pequeno).
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

private fun parseDecimal(s: String): Double =
    s.trim().replace(".", "").replace(",", ".").toDoubleOrNull()
        ?: s.trim().toDoubleOrNull()
        ?: 0.0

class NotaMultiItemViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = NotaMultiItemRepository(app)
    private val configRepository = ConfigRepository(app)
    private val recordRepository = RecordRepository(app)

    var farms = mutableStateOf<List<FarmEntity>>(emptyList())
        private set
    var itensOptions = mutableStateOf<List<LookupEntity>>(emptyList())
        private set
    var unidadesOptions = mutableStateOf<List<LookupEntity>>(emptyList())
        private set

    var numero by mutableStateOf("")
    var serie by mutableStateOf("")
    var emitenteNome by mutableStateOf("")
    // Guarda em DD/MM/AAAA (texto que o campo mostra/edita) -- só vira ISO
    // na hora de montar o corpo enviado pro servidor, em submit() abaixo.
    var dataEmissao by mutableStateOf(hojeBr())
    var fazendaDestino by mutableStateOf<String?>(null)

    val linhas = mutableStateListOf(NotaMultiItemLinha())

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
            farms.value = configRepository.farms()
            // Não pré-seleciona mais a 1ª fazenda automaticamente -- pedido
            // do usuário ("coloque lista suspensa com opção vazio no campo
            // fazenda destino"): antes o campo já vinha preenchido sozinho,
            // arriscando lançar a nota na fazenda errada sem o usuário
            // perceber que nunca escolheu de fato. Fica vazio até o usuário
            // selecionar (podeSalvar() já exige fazendaDestino preenchido).
            itensOptions.value = configRepository.lookupsByCategory("itens_estoque").sortedBy { it.label }
            unidadesOptions.value = configRepository.lookupsByCategory("unidades").sortedBy { it.label }
        }
    }

    fun addLinha() {
        linhas.add(NotaMultiItemLinha())
    }

    fun removeLinha(i: Int) {
        if (linhas.size > 1) linhas.removeAt(i)
    }

    private fun linhasValidas() = linhas.filter { it.descricao.isNotBlank() && it.quantidade.isNotBlank() && it.valorUnitario.isNotBlank() }

    fun valorTotal(): Double = linhasValidas().sumOf { parseDecimal(it.quantidade) * parseDecimal(it.valorUnitario) }

    fun podeSalvar(): Boolean =
        numero.isNotBlank() && emitenteNome.isNotBlank() && dataEmissao.isNotBlank() && !fazendaDestino.isNullOrBlank() && linhasValidas().isNotEmpty()

    fun reset() {
        numero = ""; serie = ""; emitenteNome = ""
        dataEmissao = hojeBr()
        fazendaDestino = null
        linhas.clear(); linhas.add(NotaMultiItemLinha())
        successMessage.value = null
        errorMessage.value = null
    }

    /** "Copiar último lançamento" -- busca o último lançamento do domínio
     * "financeiro" no cache local (o mesmo "pai" que, no site, alimenta
     * esta seção via props ao vivo -- ver comentário no topo do arquivo) e
     * preenche só o cabeçalho, traduzindo os nomes de campo equivalentes.
     * Itens não são copiados (sem correspondência 1:1 num lançamento
     * genérico de Financeiro). */
    fun preencherComUltimo() {
        viewModelScope.launch {
            copiando.value = true
            val last = recordRepository.mostRecent("financeiro")
            copiando.value = false
            if (last == null) {
                errorMessage.value = "Nenhum lançamento de Financeiro ainda para copiar."
                return@launch
            }
            last["docNf"]?.let { numero = it }
            last["data"]?.let { dataEmissao = com.bragro.mobile.ui.domain.isoDateToBr(it) }
            last["local"]?.let { fazendaDestino = it }
            last["entidade"]?.let { emitenteNome = it }
            successMessage.value = null
            errorMessage.value = null
        }
    }

    fun submit() {
        if (!podeSalvar()) return
        val fazenda = fazendaDestino ?: return
        val validas = linhasValidas()
        pending.value = true
        errorMessage.value = null
        viewModelScope.launch {
            val resultado = repository.criar(
                numero = numero.trim(),
                serie = serie.trim().ifBlank { null },
                emitenteNome = emitenteNome.trim(),
                // Só converte pra ISO aqui, na hora de montar o corpo pro
                // servidor -- dataEmissao no ViewModel/campo continua em
                // DD/MM/AAAA (ver comentário no var acima).
                dataEmissao = com.bragro.mobile.ui.domain.brDateToIso(dataEmissao),
                fazendaDestino = fazenda,
                itens = validas.map {
                    NotaMultiItemItemData(
                        descricao = it.descricao,
                        quantidade = parseDecimal(it.quantidade),
                        unidade = it.unidade.ifBlank { null },
                        valorUnitario = parseDecimal(it.valorUnitario),
                    )
                },
            )
            pending.value = false
            if (resultado == null || !resultado.ok) {
                errorMessage.value = resultado?.error ?: "Erro ao lançar a nota."
                return@launch
            }
            val total = resultado.valorTotal ?: 0.0
            successMessage.value = "Nota lançada: ${resultado.itensCount ?: validas.size} item(ns) baixado(s) no Estoque e ${formatMoneyBrlLocal(total)} lançados no Financeiro."
        }
    }
}

private fun formatMoneyBrlLocal(value: Double): String =
    NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(value)

// allowEmpty -- pedido do usuário ("coloque lista suspensa com opção vazio
// no campo fazenda destino"): antes a fazenda de destino vinha pré-marcada
// com a 1ª fazenda cadastrada (nenhuma opção pra "desmarcar"), o que
// arriscava lançar a nota na fazenda errada sem o usuário perceber que
// nunca tinha de fato escolhido. Com allowEmpty=true um item em branco
// aparece no topo do menu, selecionável, que zera o campo (onSelect(null)).
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
private fun LinhaItemCard(linha: NotaMultiItemLinha, itensOptions: List<LookupEntity>, unidadesOptions: List<LookupEntity>, showRemove: Boolean, onRemove: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            StringDropdown(
                label = "Item *",
                value = itensOptions.firstOrNull { it.value == linha.descricao }?.label ?: linha.descricao.ifBlank { null },
                options = itensOptions.map { it.label },
                placeholder = "Selecione o item",
                onSelect = { picked -> linha.descricao = itensOptions.firstOrNull { it.label == picked }?.value ?: picked.orEmpty() },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StringDropdown(
                    label = "Unidade",
                    value = unidadesOptions.firstOrNull { it.value == linha.unidade }?.label ?: linha.unidade.ifBlank { null },
                    options = unidadesOptions.map { it.label },
                    placeholder = "Opcional",
                    onSelect = { picked -> linha.unidade = unidadesOptions.firstOrNull { it.label == picked }?.value ?: picked.orEmpty() },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = linha.quantidade,
                    onValueChange = { linha.quantidade = it },
                    label = { Text("Quantidade *") },
                    placeholder = { Text("0") },
                    modifier = Modifier.weight(1f),
                    colors = appFieldColors(),
                )
                OutlinedTextField(
                    value = linha.valorUnitario,
                    onValueChange = { linha.valorUnitario = it },
                    label = { Text("Valor unit. (R$) *") },
                    placeholder = { Text("0,00") },
                    modifier = Modifier.weight(1f),
                    colors = appFieldColors(),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val subtotal = parseDecimal(linha.quantidade) * parseDecimal(linha.valorUnitario)
                Text(
                    "Subtotal: ${formatMoneyBrlLocal(subtotal)}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (showRemove) {
                    IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.Delete, contentDescription = "Remover item", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotaMultiItemScreen(onBack: () -> Unit, viewModel: NotaMultiItemViewModel = viewModel()) {
    val farms by viewModel.farms
    val itensOptions by viewModel.itensOptions
    val unidadesOptions by viewModel.unidadesOptions
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
                        Text("Lançar nota com itens", color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                        Text("Nota lançada com sucesso", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(successMessage ?: "", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Button(onClick = { viewModel.reset() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Lançar outra nota")
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
                // Mesmo aviso de duplicidade já adicionado no site (pedido
                // do usuário: "coloque um aviso exclusivo estoque... dar
                // duplicidade ou informações desencontradas") -- paridade.
                Text(
                    "Preencha o cabeçalho da nota uma vez e adicione quantos itens ela tiver. Cada item baixa automaticamente no Estoque; o valor total da nota vira um único lançamento no Financeiro. Não lance esta mesma nota de novo em Estoque nem em Financeiro depois -- os dois já são preenchidos por aqui, lançar de novo duplica o saldo em Estoque e distorce relatórios que dependem dele (Livro Caixa, DRE, Análises).",
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
                    Text(if (copiando) "Copiando..." else "Copiar cabeçalho do último lançamento")
                }
            }
            if (errorMessage != null) {
                item { Text(errorMessage ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
            item {
                OutlinedTextField(
                    value = viewModel.numero,
                    onValueChange = { viewModel.numero = it },
                    label = { Text("Número da nota *") },
                    placeholder = { Text("Ex.: 12345") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = appFieldColors(),
                )
            }
            item {
                OutlinedTextField(
                    value = viewModel.serie,
                    onValueChange = { viewModel.serie = it },
                    label = { Text("Série") },
                    placeholder = { Text("Opcional") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = appFieldColors(),
                )
            }
            item {
                OutlinedTextField(
                    value = viewModel.emitenteNome,
                    onValueChange = { viewModel.emitenteNome = it },
                    label = { Text("Fornecedor *") },
                    placeholder = { Text("Nome do fornecedor") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = appFieldColors(),
                )
            }
            item {
                // Padrão brasileiro DD/MM/AAAA (com calendário) -- pedido do
                // usuário ("o campo data não está como padrão Brasil"), mesmo
                // padrão já usado nos campos "date" do motor genérico (ver
                // DomainFormScreen.kt). Continua guardando/enviando ISO só
                // internamente (ver dataEmissao no ViewModel e submit()).
                var showPicker by remember { mutableStateOf(false) }
                OutlinedTextField(
                    value = viewModel.dataEmissao,
                    onValueChange = { viewModel.dataEmissao = it },
                    label = { Text("Data de emissão *") },
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
                        initialSelectedDateMillis = brDateToMillisOrNull(viewModel.dataEmissao) ?: System.currentTimeMillis(),
                    )
                    DatePickerDialog(
                        onDismissRequest = { showPicker = false },
                        confirmButton = {
                            TextButton(onClick = {
                                pickerState.selectedDateMillis?.let { viewModel.dataEmissao = millisToBrDate(it) }
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
                    label = "Fazenda de destino *",
                    value = viewModel.fazendaDestino,
                    options = farms.map { it.name },
                    placeholder = "Selecione a fazenda",
                    allowEmpty = true,
                    onSelect = { viewModel.fazendaDestino = it },
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp)) }
            item { Text("Itens da nota *", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
            items(viewModel.linhas.size) { i ->
                LinhaItemCard(
                    linha = viewModel.linhas[i],
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
                Text(
                    "Total da nota: ${formatMoneyBrlLocal(viewModel.valorTotal())}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            item {
                Button(
                    onClick = { viewModel.submit() },
                    enabled = !pending && viewModel.podeSalvar(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (pending) CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                    else Text("Lançar nota")
                }
            }
        }
    }
}
