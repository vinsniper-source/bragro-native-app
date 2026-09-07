package com.bragro.mobile.ui.cotacoes

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
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
import com.bragro.mobile.data.model.CotacaoPrecoMedioResponse
import com.bragro.mobile.data.repo.ConfigRepository
import com.bragro.mobile.data.repo.CotacaoMultiItemRepository
import com.bragro.mobile.data.repo.RecordRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Cotações de Fornecedores -- réplica da tela web (cotacao-multi-item-button.tsx).
// Esta tela vira o ÚNICO caminho pra CRIAR uma Cotação no app (editar
// continua na tela genérica de 1 item, DomainFormScreen.kt -- ver
// BRAgroNavHost.kt).
//
// Task #472 (pedido do usuário: "unifique fornecedores e itens em uma só
// página e exclua os botões, adicionar campo de comparação de lógica de
// média de preços"): os dois modos que existiam antes ("Vários itens" = 1
// fornecedor + N itens, e "Comparar fornecedores" = 1 item + N fornecedores,
// task #404) viraram UM só formulário -- "múltiplos itens, cada um com
// múltiplos fornecedores" é um superconjunto dos dois (1 fornecedor + N
// itens = N grupos de 1 proposta cada; 1 item + N fornecedores = 1 grupo de
// N propostas), então o alternador de modo saiu, sem perder nenhum caso de
// uso. Cada grupo agora também mostra o PREÇO MÉDIO HISTÓRICO daquele item
// (média de todas as cotações já lançadas antes desta submissão, ver
// /api/mobile/cotacao-preco-medio) com um indicador de quanto cada proposta
// está acima/abaixo dessa média. Continua chamando DIRETO
// createCotacaoComparacaoAction() no servidor via /api/mobile/cotacao-
// comparacao (1 chamada por grupo) -- nenhuma lógica de negócio duplicada em
// Kotlin (Índice de Vantagem/Avaliação recalculados só no servidor).

// Uma proposta de fornecedor dentro de um grupo (item).
class PropostaLinha {
    var fornecedor by mutableStateOf("")
    var precoUnitario by mutableStateOf("")
    var prazoEntregaDias by mutableStateOf("")
    var condicaoPagamento by mutableStateOf<String?>(null)
    var validadeProposta by mutableStateOf("")
}

// Um "grupo" = 1 item (categoria+item+quantidade+unidade) com sua própria
// lista de propostas de fornecedor -- mesmo raciocínio do site (ver
// cotacao-multi-item-button.tsx). Cobre os dois casos de uso antigos: 1
// fornecedor cotando vários itens (cada item = 1 grupo com 1 proposta) e
// vários fornecedores cotando o MESMO item (1 grupo com N propostas).
class GrupoLinha {
    var categoria by mutableStateOf("")
    var item by mutableStateOf("")
    var quantidade by mutableStateOf("")
    var unidade by mutableStateOf("")
    val propostas = mutableStateListOf(PropostaLinha())
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

/** Chave do cache de preço médio histórico -- mesma convenção do site. */
private fun historicoKey(categoria: String, item: String) = "${categoria.trim()}||${item.trim()}"

private fun isoParaBr(iso: String): String {
    val partes = iso.take(10).split("-")
    return if (partes.size == 3) "${partes[2]}/${partes[1]}/${partes[0]}" else iso
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

    var data by mutableStateOf(hojeBr())
    var observacoes by mutableStateOf("")
    val grupos = mutableStateListOf(GrupoLinha())

    // Preço médio histórico por item (task #472), cacheado por
    // "categoria||item" pra não repetir a mesma chamada de rede enquanto o
    // usuário edita outros campos do mesmo grupo. Valor null na resposta
    // (ok=false, ex. sem conexão) é tratado como "sem histórico disponível
    // agora", não como erro bloqueante do formulário.
    val historico = mutableStateMapOf<String, CotacaoPrecoMedioResponse?>()
    // Set comum (nao precisa ser observavel pelo Compose -- so evita
    // disparar 2 chamadas de rede pro mesmo item, nunca eh lido por
    // nenhum @Composable).
    private val historicoLoading = mutableSetOf<String>()

    fun buscarHistoricoSeNecessario(categoria: String, item: String) {
        val cat = categoria.trim()
        val it = item.trim()
        if (cat.isEmpty() || it.isEmpty()) return
        val key = historicoKey(cat, it)
        if (historico.containsKey(key) || historicoLoading.contains(key)) return
        historicoLoading.add(key)
        viewModelScope.launch {
            historico[key] = repository.precoMedioHistorico(cat, it)
            historicoLoading.remove(key)
        }
    }

    var pending = mutableStateOf(false)
        private set
    var errorMessage = mutableStateOf<String?>(null)
        private set
    var successMessage = mutableStateOf<String?>(null)
        private set
    var copiando = mutableStateOf(false)
        private set

    fun addGrupo() {
        grupos.add(GrupoLinha())
    }

    fun removeGrupo(gi: Int) {
        if (grupos.size > 1) grupos.removeAt(gi)
    }

    fun addProposta(gi: Int) {
        grupos[gi].propostas.add(PropostaLinha())
    }

    fun removeProposta(gi: Int, pi: Int) {
        val propostas = grupos[gi].propostas
        if (propostas.size > 1) propostas.removeAt(pi)
    }

    private fun GrupoLinha.propostasValidas() = propostas.filter { it.fornecedor.isNotBlank() && it.precoUnitario.isNotBlank() }

    private fun gruposValidos() = grupos.filter { it.categoria.isNotBlank() && it.item.isNotBlank() && it.propostasValidas().isNotEmpty() }

    fun totalPropostasValidas(): Int = gruposValidos().sumOf { it.propostasValidas().size }

    fun podeSalvar(): Boolean = data.isNotBlank() && gruposValidos().isNotEmpty()

    init {
        viewModelScope.launch {
            categoriasOptions.value = configRepository.lookupsByCategory("categorias_cotacao").sortedBy { it.label }
            itensOptions.value = configRepository.lookupsByCategory("itens_estoque").sortedBy { it.label }
            unidadesOptions.value = configRepository.lookupsByCategory("unidades").sortedBy { it.label }
            formasPgtoOptions.value = configRepository.lookupsByCategory("formas_pgto").sortedBy { it.label }
            entidadesOptions.value = configRepository.lookupsByCategory("entidades_financeiro").sortedBy { it.label }
        }
    }

    fun reset() {
        data = hojeBr()
        observacoes = ""
        grupos.clear(); grupos.add(GrupoLinha())
        historico.clear()
        successMessage.value = null
        errorMessage.value = null
    }

    /** "Copiar último lançamento" -- busca a última proposta de cotação
     * lançada (qualquer fornecedor/item) no cache local e preenche a Data
     * comum + o primeiro grupo (categoria/item/quantidade/unidade + 1
     * proposta), mesmo padrão de preencherComUltimo() em
     * cotacao-multi-item-button.tsx (site). */
    fun preencherComUltimo() {
        viewModelScope.launch {
            copiando.value = true
            val last = recordRepository.mostRecent("cotacoesfornecedores")
            copiando.value = false
            if (last == null) {
                errorMessage.value = "Nenhuma cotação lançada ainda para copiar."
                return@launch
            }
            last["data"]?.let { data = com.bragro.mobile.ui.domain.isoDateToBr(it) }
            val grupo = GrupoLinha()
            last["categoria"]?.let { grupo.categoria = it }
            last["item"]?.let { grupo.item = it }
            last["quantidade"]?.let { grupo.quantidade = it }
            last["unidade"]?.let { grupo.unidade = it }
            val proposta = grupo.propostas[0]
            last["fornecedor"]?.let { proposta.fornecedor = it }
            last["precoUnitario"]?.let { proposta.precoUnitario = it }
            last["prazoEntregaDias"]?.let { proposta.prazoEntregaDias = it }
            last["condicaoPagamento"]?.let { proposta.condicaoPagamento = it }
            last["validadeProposta"]?.let { proposta.validadeProposta = com.bragro.mobile.ui.domain.isoDateToBr(it) }
            grupos.clear()
            grupos.add(grupo)
            successMessage.value = null
            errorMessage.value = null
        }
    }

    fun submit() {
        if (!podeSalvar()) return
        val validos = gruposValidos()
        pending.value = true
        errorMessage.value = null
        viewModelScope.launch {
            var total = 0
            var erro: String? = null
            for (g in validos) {
                val resultado = repository.criarComparacao(
                    data = com.bragro.mobile.ui.domain.brDateToIso(data),
                    categoria = g.categoria,
                    item = g.item,
                    quantidade = g.quantidade.takeIf { it.isNotBlank() }?.let { parseDecimal(it) },
                    unidade = g.unidade.ifBlank { null },
                    observacoes = observacoes.trim().ifBlank { null },
                    propostas = g.propostasValidas().map {
                        CotacaoComparacaoPropostaData(
                            fornecedor = it.fornecedor.trim(),
                            precoUnitario = parseDecimal(it.precoUnitario),
                            prazoEntregaDias = it.prazoEntregaDias.takeIf { v -> v.isNotBlank() }?.let { v -> parseDecimal(v) },
                            condicaoPagamento = it.condicaoPagamento,
                            validadeProposta = it.validadeProposta.takeIf { v -> v.isNotBlank() }?.let { v -> com.bragro.mobile.ui.domain.brDateToIso(v) },
                        )
                    },
                )
                if (resultado == null || !resultado.ok) {
                    erro = resultado?.error ?: "Erro ao lançar a cotação."
                    break
                }
                total += resultado.count ?: g.propostasValidas().size
            }
            pending.value = false
            if (erro != null) {
                errorMessage.value = erro
                return@launch
            }
            successMessage.value = if (validos.size > 1) "$total proposta(s) lançada(s) em ${validos.size} itens." else "$total proposta(s) lançada(s)."
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

/** Bloco individual (scrim onSurface, sem borda -- regra do app de não ter
 * bordas em lugar nenhum) separando cada campo dentro de um card de item/
 * proposta. alpha 0.12f -- ver histórico do valor em CHANGELOG.md (0.05f
 * era imperceptível em display real). */
@Composable
private fun ItemFieldBlock(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
        shape = MaterialTheme.shapes.small,
    ) {
        Box(modifier = Modifier.padding(6.dp)) {
            content()
        }
    }
}

/** Texto do preço médio histórico do item (task #472) -- mesmo texto do
 * site, adaptado. hist == null pode significar "ainda carregando" OU "a
 * chamada falhou" (repository.precoMedioHistorico devolve null em qualquer
 * falha de rede); nos dois casos mostramos "Calculando..." em vez de tentar
 * distinguir, já que uma nova tentativa acontece sozinha na próxima
 * recomposição com a mesma chave (ela não fica marcada como já buscada). */
private fun historicoTexto(hist: CotacaoPrecoMedioResponse?): String {
    if (hist == null) return "Calculando preço médio histórico..."
    if (!hist.ok || hist.amostras == null || hist.amostras == 0) return "Sem cotações anteriores deste item para comparar ainda."
    val media = hist.mediaPreco ?: return "Sem cotações anteriores deste item para comparar ainda."
    val plural = hist.amostras > 1
    val dataTxt = hist.ultimaData?.let { ", última em ${isoParaBr(it)}" } ?: ""
    return "Preço médio histórico: R$ ${"%.2f".format(media)} (${hist.amostras} cotação${if (plural) "ões" else ""} anterior${if (plural) "es" else ""}$dataTxt)"
}

// Card de UMA proposta de fornecedor dentro de um grupo (item).
@Composable
private fun PropostaCard(
    proposta: PropostaLinha,
    entidadesOptions: List<LookupEntity>,
    formasPgtoOptions: List<LookupEntity>,
    mediaHistorica: Double?,
    showRemove: Boolean,
    onRemove: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ItemFieldBlock {
                StringDropdown(
                    label = "Fornecedor *",
                    value = entidadesOptions.firstOrNull { it.value == proposta.fornecedor }?.label ?: proposta.fornecedor.ifBlank { null },
                    options = entidadesOptions.map { it.label },
                    placeholder = "Selecione o fornecedor",
                    onSelect = { picked -> proposta.fornecedor = entidadesOptions.firstOrNull { it.label == picked }?.value ?: picked.orEmpty() },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ItemFieldBlock(modifier = Modifier.weight(1f)) {
                    Column {
                        OutlinedTextField(
                            value = proposta.precoUnitario,
                            onValueChange = { proposta.precoUnitario = it },
                            label = { Text("Preço unit. (R$) *") },
                            placeholder = { Text("0,00") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = appFieldColors(),
                        )
                        // Indicador vs. média histórica (task #472) -- mesmo
                        // cálculo do site: preço abaixo/igual à média em
                        // verde (▼), acima em vermelho (▲).
                        val preco = proposta.precoUnitario.let { if (it.isBlank()) null else parseDecimal(it) }
                        if (mediaHistorica != null && mediaHistorica > 0 && preco != null && preco > 0) {
                            val diffPct = ((preco - mediaHistorica) / mediaHistorica) * 100
                            Text(
                                (if (diffPct <= 0) "▼ " else "▲ ") + "${"%.0f".format(kotlin.math.abs(diffPct))}% vs. média histórica",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (diffPct <= 0) androidx.compose.ui.graphics.Color(0xFF059669) else MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
                ItemFieldBlock(modifier = Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = proposta.prazoEntregaDias,
                        onValueChange = { proposta.prazoEntregaDias = it },
                        label = { Text("Prazo (dias)") },
                        placeholder = { Text("Opcional") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = appFieldColors(),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ItemFieldBlock(modifier = Modifier.weight(1f)) {
                    StringDropdown(
                        label = "Condição pgto.",
                        value = formasPgtoOptions.firstOrNull { it.value == proposta.condicaoPagamento }?.label ?: proposta.condicaoPagamento,
                        options = formasPgtoOptions.map { it.label },
                        placeholder = "Opcional",
                        allowEmpty = true,
                        onSelect = { picked -> proposta.condicaoPagamento = formasPgtoOptions.firstOrNull { it.label == picked }?.value ?: picked },
                    )
                }
                ItemFieldBlock(modifier = Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = proposta.validadeProposta,
                        onValueChange = { proposta.validadeProposta = it },
                        label = { Text("Validade") },
                        placeholder = { Text("DD/MM/AAAA") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = appFieldColors(),
                    )
                }
            }
            if (showRemove) {
                IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.Delete, contentDescription = "Remover proposta", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

// Card de UM grupo (item cotado + suas propostas de fornecedor) -- task #472.
@Composable
private fun GrupoCard(
    grupo: GrupoLinha,
    categoriasOptions: List<LookupEntity>,
    itensOptions: List<LookupEntity>,
    unidadesOptions: List<LookupEntity>,
    entidadesOptions: List<LookupEntity>,
    formasPgtoOptions: List<LookupEntity>,
    historico: Map<String, CotacaoPrecoMedioResponse?>,
    onBuscarHistorico: (String, String) -> Unit,
    showRemoveGrupo: Boolean,
    onRemoveGrupo: () -> Unit,
    onAddProposta: () -> Unit,
    onRemoveProposta: (Int) -> Unit,
) {
    LaunchedEffect(grupo.categoria, grupo.item) {
        onBuscarHistorico(grupo.categoria, grupo.item)
    }
    val temItem = grupo.categoria.isNotBlank() && grupo.item.isNotBlank()
    val hist = if (temItem) historico[historicoKey(grupo.categoria, grupo.item)] else null
    val mediaHistorica = if (hist?.ok == true) hist.mediaPreco else null

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ItemFieldBlock {
                StringDropdown(
                    label = "Categoria *",
                    value = categoriasOptions.firstOrNull { it.value == grupo.categoria }?.label ?: grupo.categoria.ifBlank { null },
                    options = categoriasOptions.map { it.label },
                    placeholder = "Categoria",
                    onSelect = { picked -> grupo.categoria = categoriasOptions.firstOrNull { it.label == picked }?.value ?: picked.orEmpty() },
                )
            }
            ItemFieldBlock {
                StringDropdown(
                    label = "Item *",
                    value = itensOptions.firstOrNull { it.value == grupo.item }?.label ?: grupo.item.ifBlank { null },
                    options = itensOptions.map { it.label },
                    placeholder = "Selecione o item",
                    onSelect = { picked -> grupo.item = itensOptions.firstOrNull { it.label == picked }?.value ?: picked.orEmpty() },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ItemFieldBlock(modifier = Modifier.weight(1f)) {
                    StringDropdown(
                        label = "Unidade",
                        value = unidadesOptions.firstOrNull { it.value == grupo.unidade }?.label ?: grupo.unidade.ifBlank { null },
                        options = unidadesOptions.map { it.label },
                        placeholder = "Opcional",
                        allowEmpty = true,
                        onSelect = { picked -> grupo.unidade = unidadesOptions.firstOrNull { it.label == picked }?.value ?: picked.orEmpty() },
                    )
                }
                ItemFieldBlock(modifier = Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = grupo.quantidade,
                        onValueChange = { grupo.quantidade = it },
                        label = { Text("Quantidade") },
                        placeholder = { Text("Opcional") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = appFieldColors(),
                    )
                }
            }
            // Preço médio histórico do item (task #472) -- só aparece depois
            // de Categoria+Item preenchidos. Compara contra TODAS as
            // cotações já lançadas antes desta submissão pra este item,
            // independente de fornecedor.
            if (temItem) {
                Text(
                    historicoTexto(hist),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()
            Text("Propostas dos fornecedores *", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            grupo.propostas.forEachIndexed { pi, proposta ->
                PropostaCard(
                    proposta = proposta,
                    entidadesOptions = entidadesOptions,
                    formasPgtoOptions = formasPgtoOptions,
                    mediaHistorica = mediaHistorica,
                    showRemove = grupo.propostas.size > 1,
                    onRemove = { onRemoveProposta(pi) },
                )
            }
            OutlinedButton(onClick = onAddProposta, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                Text("Adicionar fornecedor")
            }
            if (showRemoveGrupo) {
                IconButton(onClick = onRemoveGrupo, modifier = Modifier.size(28.dp)) {
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
                actions = {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        IconButton(
                            onClick = { viewModel.preencherComUltimo() },
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
            item {
                Text(
                    "Descreva cada item que está cotando uma vez e lance o preço de cada fornecedor que cotou ele -- dá pra comparar quantos fornecedores quiser por item, e adicionar mais de um item na mesma submissão.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (errorMessage != null) {
                item { Text(errorMessage ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
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
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp)) }
            item { Text("Itens cotados (cada um com suas propostas) *", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
            items(viewModel.grupos.size) { gi ->
                GrupoCard(
                    grupo = viewModel.grupos[gi],
                    categoriasOptions = categoriasOptions,
                    itensOptions = itensOptions,
                    unidadesOptions = unidadesOptions,
                    entidadesOptions = entidadesOptions,
                    formasPgtoOptions = formasPgtoOptions,
                    historico = viewModel.historico,
                    onBuscarHistorico = { cat, it -> viewModel.buscarHistoricoSeNecessario(cat, it) },
                    showRemoveGrupo = viewModel.grupos.size > 1,
                    onRemoveGrupo = { viewModel.removeGrupo(gi) },
                    onAddProposta = { viewModel.addProposta(gi) },
                    onRemoveProposta = { pi -> viewModel.removeProposta(gi, pi) },
                )
            }
            item {
                OutlinedButton(onClick = { viewModel.addGrupo() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                    Text("Adicionar item")
                }
            }
            item {
                OutlinedTextField(
                    value = viewModel.observacoes,
                    onValueChange = { viewModel.observacoes = it },
                    label = { Text("Observações (opcional, vale para todos os itens/propostas)") },
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
                    else Text("Lançar ${viewModel.totalPropostasValidas().let { if (it > 0) it else "" }} proposta(s)")
                }
            }
        }
    }
}
