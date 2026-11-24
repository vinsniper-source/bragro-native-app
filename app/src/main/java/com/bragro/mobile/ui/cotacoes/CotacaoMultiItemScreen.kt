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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import com.bragro.mobile.ui.theme.Card
import com.bragro.mobile.ui.theme.SearchableDropdownField
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
import com.bragro.mobile.ui.theme.LinhaTextField as OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import com.bragro.mobile.ui.i18n.Text
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
import kotlinx.serialization.json.contentOrNull
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
    s.trim().replace("R$", "").replace(" ", "").let { t ->
        // Com vírgula: formato BR ("1.250,50"). Sem vírgula: ponto é decimal ("12.50").
        if (t.contains(",")) t.replace(".", "").replace(",", ".").toDoubleOrNull()
        else t.toDoubleOrNull()
    } ?: 0.0

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

    /** "+Criar" (Task #869) -- mesmo motor quick-create-lookup usado em
     * Prescrição/Pedidos, aplicado às 5 listas suspensas deste formulário
     * (categoria/item/unidade/forma de pagamento/fornecedor). */
    fun quickCreateLookupOption(category: String, rawValue: String, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            val result = com.bragro.mobile.data.repo.ModuleActionsRepository(getApplication()).run(
                "quick-create-lookup", domainId = "cotacoesfornecedores", category = category, rawValue = rawValue,
            )
            val value = (result?.get("value") as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
            if (!value.isNullOrBlank()) {
                fun merge(current: List<LookupEntity>) =
                    if (current.any { it.value == value }) current
                    else (current + LookupEntity(category, value, value, current.size)).sortedBy { it.label }
                when (category) {
                    "categorias_cotacao" -> categoriasOptions.value = merge(categoriasOptions.value)
                    "itens_estoque" -> itensOptions.value = merge(itensOptions.value)
                    "unidades" -> unidadesOptions.value = merge(unidadesOptions.value)
                    "formas_pgto" -> formasPgtoOptions.value = merge(formasPgtoOptions.value)
                    "entidades_financeiro" -> entidadesOptions.value = merge(entidadesOptions.value)
                }
                onDone(value)
            } else {
                onDone(null)
            }
        }
    }

    fun addGrupo() {
        grupos.add(GrupoLinha())
    }

    fun removeGrupo(gi: Int) {
        if (grupos.size > 1) grupos.removeAt(gi)
    }

    fun addProposta(gi: Int) {
        grupos[gi].propostas.add(PropostaLinha())
    }

    // v292 (mockup v384c) -- ⧉ copiar item (com as propostas) e ⧉ copiar proposta.
    private fun PropostaLinha.copia() = PropostaLinha().also {
        it.fornecedor = fornecedor; it.precoUnitario = precoUnitario; it.prazoEntregaDias = prazoEntregaDias
        it.condicaoPagamento = condicaoPagamento; it.validadeProposta = validadeProposta
    }
    fun copiarGrupo(gi: Int) {
        val o = grupos[gi]
        val n = GrupoLinha().also { it.categoria = o.categoria; it.item = o.item; it.quantidade = o.quantidade; it.unidade = o.unidade }
        n.propostas.clear()
        o.propostas.forEach { n.propostas.add(it.copia()) }
        grupos.add(gi + 1, n)
    }
    fun copiarProposta(gi: Int, pi: Int) {
        val ps = grupos[gi].propostas
        ps.add(pi + 1, ps[pi].copia())
    }

    /** "Preencher com foto": lê a proposta do fornecedor e preenche o
     * primeiro item/proposta (ou o primeiro ainda vazio). Só pré-preenche. */
    suspend fun aplicarOcrFoto(fotoUrl: String): String? {
        val r = com.bragro.mobile.data.repo.OcrFotoRepository(getApplication()).lerGenerico("cotacoesfornecedores", fotoUrl)
        val c = r.campos
        if (!r.ok || c.isNullOrEmpty()) return r.mensagem ?: r.error ?: "Não consegui ler nenhum campo desta foto."
        fun v(k: String) = c[k]?.takeIf { it.isNotBlank() }
        val g = grupos.firstOrNull { it.item.isBlank() } ?: GrupoLinha().also { grupos.add(it) }
        v("data")?.let { data = com.bragro.mobile.ui.domain.isoDateToBr(com.bragro.mobile.ui.domain.isoDateOnly(it)) }
        v("categoria")?.let { g.categoria = it }
        v("item")?.let { g.item = it }
        v("quantidade")?.let { g.quantidade = it }
        v("unidade")?.let { g.unidade = it }
        val p = g.propostas.first()
        v("fornecedor")?.let { p.fornecedor = it }
        v("precoUnitario")?.let { p.precoUnitario = it }
        v("prazoEntregaDias")?.let { p.prazoEntregaDias = it }
        v("condicaoPagamento")?.let { p.condicaoPagamento = it }
        v("validadeProposta")?.let { p.validadeProposta = com.bragro.mobile.ui.domain.isoDateToBr(com.bragro.mobile.ui.domain.isoDateOnly(it)) }
        v("observacoes")?.let { observacoes = it }
        return r.mensagem ?: "${c.size} campo(s) lido(s). Confira."
    }

    fun removeProposta(gi: Int, pi: Int) {
        val propostas = grupos[gi].propostas
        if (propostas.size > 1) propostas.removeAt(pi)
    }

    private fun GrupoLinha.propostasValidas() = propostas.filter { it.fornecedor.isNotBlank() && parseDecimal(it.precoUnitario) > 0.0 }

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

    fun submit() {
        if (!podeSalvar()) return
        val validos = gruposValidos()
        pending.value = true
        errorMessage.value = null
        viewModelScope.launch {
            var total = 0
            var erro: String? = null
            var offline = false
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
                if (resultado.savedOffline) offline = true
                total += resultado.count ?: g.propostasValidas().size
            }
            pending.value = false
            if (erro != null) {
                errorMessage.value = erro
                return@launch
            }
            successMessage.value = if (offline) {
                "$total proposta(s) salva(s). Envia quando houver internet."
            } else if (validos.size > 1) {
                "$total proposta(s) lançada(s) em ${validos.size} itens."
            } else {
                "$total proposta(s) lançada(s)."
            }
        }
    }
}

@Composable
private fun StringDropdown(
    label: String,
    value: String?,
    options: List<String>,
    placeholder: String,
    allowEmpty: Boolean = false,
    modifier: Modifier = Modifier,
    onSelect: (String?) -> Unit,
    // "+Criar" (Task #869) -- paridade com o onCreate de SearchableDropdownField.
    onCreate: ((String) -> Unit)? = null,
) {
    SearchableDropdownField(
        value = value ?: "",
        label = label,
        options = remember(options) { options.map { it to it } },
        onSelect = { picked -> onSelect(picked.ifEmpty { null }) },
        modifier = modifier,
        placeholder = placeholder,
        emptyOptionLabel = if (allowEmpty) " " else null,
        onCreate = onCreate,
    )
}

// Sombra verde: a partir de agora vem de graça pelo Card compartilhado
// (com.bragro.mobile.ui.theme.Card, ver AppCard.kt) -- pedido do usuário
// ("aplique o verde de fundo nas sombras dos campos não só nesse módulo mas
// em todos que esteja na mesma situação") estendeu o que antes era só desta
// tela (greenCardShadow(), removido daqui) pra TODO o app de uma vez.

/** Bloco individual (scrim onSurface, sem borda -- regra do app de não ter
 * bordas em lugar nenhum) separando cada campo dentro de um card de item/
 * proposta. alpha 0.12f -- ver histórico do valor em CHANGELOG.md (0.05f
 * era imperceptível em display real). */
@Composable
private fun ItemFieldBlock(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    com.bragro.mobile.ui.theme.StandardFieldBlock(modifier = modifier, content = content)
}

/** Texto do preço médio histórico do item (task #472) -- mesmo texto do
 * site, adaptado. hist == null pode significar "ainda carregando" OU "a
 * chamada falhou" (repository.precoMedioHistorico devolve null em qualquer
 * falha de rede); nos dois casos mostramos "Calculando..." em vez de tentar
 * distinguir, já que uma nova tentativa acontece sozinha na próxima
 * recomposição com a mesma chave (ela não fica marcada como já buscada). */
private fun historicoTexto(hist: CotacaoPrecoMedioResponse?): String {
    if (hist == null) return "Calculando preço médio histórico..."
    if (!hist.ok || hist.amostras == null || hist.amostras == 0) return "Sem cotações anteriores."
    val media = hist.mediaPreco ?: return "Sem cotações anteriores."
    val plural = hist.amostras > 1
    val dataTxt = hist.ultimaData?.let { ", última em ${isoParaBr(it)}" } ?: ""
    return "Preço médio histórico: R$ ${"%.2f".format(media)} (${hist.amostras} cotação${if (plural) "ões" else ""} anterior${if (plural) "es" else ""}$dataTxt)"
}

// v292 (mockup v384c aprovado) -- mesma estrutura da plataforma
// (cotacao-multi-item-button.tsx): bloco do Item com Categoria, Item,
// Quantidade, Unidade e preço médio histórico; dentro dele um bloco por
// Proposta (fornecedor). Os ícones + ficam na linha do título de cada bloco
// interno: no Item, + adiciona outro item; na Proposta, + adiciona outro
// fornecedor (ao lado de ⧉ copiar e 🗑 excluir).
@Composable
private fun AcoesBloco(nomeMais: String, onMais: () -> Unit, nomeCopiar: String, onCopiar: () -> Unit, nomeExcluir: String, onExcluir: (() -> Unit)?) {
    IconButton(onClick = onMais, modifier = Modifier.size(34.dp)) {
        com.bragro.mobile.ui.domain.IconeNegrito(Icons.Outlined.Add, contentDescription = nomeMais, tint = MaterialTheme.colorScheme.onSurface, size = 22.dp)
    }
    IconButton(onClick = onCopiar, modifier = Modifier.size(34.dp)) {
        com.bragro.mobile.ui.domain.IconeNegrito(Icons.Outlined.ContentCopy, contentDescription = nomeCopiar, tint = MaterialTheme.colorScheme.onSurface, size = 20.dp)
    }
    if (onExcluir != null) IconButton(onClick = onExcluir, modifier = Modifier.size(34.dp)) {
        com.bragro.mobile.ui.domain.IconeNegrito(Icons.Outlined.Delete, contentDescription = nomeExcluir, tint = com.bragro.mobile.ui.domain.corAtencao(), size = 20.dp)
    }
}

@Composable
private fun PropostaCard(
    numero: Int,
    proposta: PropostaLinha,
    quantidade: String,
    entidadesOptions: List<LookupEntity>,
    formasPgtoOptions: List<LookupEntity>,
    mediaHistorica: Double?,
    viewModel: CotacaoMultiItemViewModel,
    onAdd: () -> Unit,
    onCopy: () -> Unit,
    onRemove: (() -> Unit)?,
) {
    com.bragro.mobile.ui.theme.FormBlockCard(modifier = Modifier.fillMaxWidth(), invertido = false) {
        Column(modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Proposta $numero", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                AcoesBloco("Adicionar fornecedor", onAdd, "Copiar proposta", onCopy, "Remover proposta", onRemove)
            }
            Column(Modifier.padding(end = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                StringDropdown(
                    label = "Fornecedor *",
                    value = entidadesOptions.firstOrNull { it.value == proposta.fornecedor }?.label ?: proposta.fornecedor.ifBlank { null },
                    options = entidadesOptions.map { it.label },
                    placeholder = "",
                    onSelect = { picked -> proposta.fornecedor = entidadesOptions.firstOrNull { it.label == picked }?.value ?: picked.orEmpty() },
                    onCreate = { raw -> viewModel.quickCreateLookupOption("entidades_financeiro", raw) { value -> value?.let { proposta.fornecedor = it } } },
                )
                Column {
                    OutlinedTextField(
                        value = proposta.precoUnitario,
                        onValueChange = { proposta.precoUnitario = it },
                        label = { Text("Preço unit. (R$) *") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = appFieldColors(),
                    )
                    val preco = proposta.precoUnitario.let { if (it.isBlank()) null else parseDecimal(it) }
                    if (mediaHistorica != null && mediaHistorica > 0 && preco != null && preco > 0) {
                        val diffPct = ((preco - mediaHistorica) / mediaHistorica) * 100
                        Text(
                            (if (diffPct <= 0) "▼ " else "▲ ") + "${"%.0f".format(kotlin.math.abs(diffPct))}% vs. média histórica",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (diffPct <= 0) MaterialTheme.colorScheme.primary else com.bragro.mobile.ui.domain.corAtencao(),
                        )
                    }
                }
                val total = parseDecimal(quantidade) * parseDecimal(proposta.precoUnitario)
                com.bragro.mobile.ui.theme.CampoAutoLinha(
                    "Total",
                    if (total > 0) java.text.NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(total) else "",
                )
                OutlinedTextField(
                    value = proposta.prazoEntregaDias,
                    onValueChange = { proposta.prazoEntregaDias = it },
                    label = { Text("Prazo (dias)") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = appFieldColors(),
                )
                StringDropdown(
                    label = "Pagamento",
                    value = formasPgtoOptions.firstOrNull { it.value == proposta.condicaoPagamento }?.label ?: proposta.condicaoPagamento,
                    options = formasPgtoOptions.map { it.label },
                    placeholder = "",
                    allowEmpty = true,
                    onSelect = { picked -> proposta.condicaoPagamento = formasPgtoOptions.firstOrNull { it.label == picked }?.value ?: picked },
                    onCreate = { raw -> viewModel.quickCreateLookupOption("formas_pgto", raw) { value -> value?.let { proposta.condicaoPagamento = it } } },
                )
                OutlinedTextField(
                    value = proposta.validadeProposta,
                    onValueChange = { proposta.validadeProposta = it },
                    label = { Text("Validade") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = appFieldColors(),
                )
            }
        }
    }
}

@Composable
private fun GrupoCard(
    numero: Int,
    grupo: GrupoLinha,
    categoriasOptions: List<LookupEntity>,
    itensOptions: List<LookupEntity>,
    unidadesOptions: List<LookupEntity>,
    entidadesOptions: List<LookupEntity>,
    formasPgtoOptions: List<LookupEntity>,
    historico: Map<String, CotacaoPrecoMedioResponse?>,
    onBuscarHistorico: (String, String) -> Unit,
    viewModel: CotacaoMultiItemViewModel,
    onAddGrupo: () -> Unit,
    onCopyGrupo: () -> Unit,
    onRemoveGrupo: (() -> Unit)?,
    onAddProposta: () -> Unit,
    onCopyProposta: (Int) -> Unit,
    onRemoveProposta: (Int) -> Unit,
) {
    LaunchedEffect(grupo.categoria, grupo.item) {
        onBuscarHistorico(grupo.categoria, grupo.item)
    }
    val temItem = grupo.categoria.isNotBlank() && grupo.item.isNotBlank()
    val hist = if (temItem) historico[historicoKey(grupo.categoria, grupo.item)] else null
    val mediaHistorica = if (hist?.ok == true) hist.mediaPreco else null

    com.bragro.mobile.ui.theme.FormBlockCard(modifier = Modifier.fillMaxWidth(), invertido = false) {
        Column(modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Item $numero", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                AcoesBloco("Adicionar item", onAddGrupo, "Copiar item", onCopyGrupo, "Remover item", onRemoveGrupo)
            }
            Column(Modifier.padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                StringDropdown(
                    label = "Categoria *",
                    value = categoriasOptions.firstOrNull { it.value == grupo.categoria }?.label ?: grupo.categoria.ifBlank { null },
                    options = categoriasOptions.map { it.label },
                    placeholder = "",
                    onSelect = { picked -> grupo.categoria = categoriasOptions.firstOrNull { it.label == picked }?.value ?: picked.orEmpty() },
                    onCreate = { raw -> viewModel.quickCreateLookupOption("categorias_cotacao", raw) { value -> value?.let { grupo.categoria = it } } },
                )
                StringDropdown(
                    label = "Item *",
                    value = itensOptions.firstOrNull { it.value == grupo.item }?.label ?: grupo.item.ifBlank { null },
                    options = itensOptions.map { it.label },
                    placeholder = "",
                    onSelect = { picked -> grupo.item = itensOptions.firstOrNull { it.label == picked }?.value ?: picked.orEmpty() },
                    onCreate = { raw -> viewModel.quickCreateLookupOption("itens_estoque", raw) { value -> value?.let { grupo.item = it } } },
                )
                OutlinedTextField(
                    value = grupo.quantidade,
                    onValueChange = { grupo.quantidade = it },
                    label = { Text("Quantidade") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = appFieldColors(),
                )
                StringDropdown(
                    label = "Unidade",
                    value = unidadesOptions.firstOrNull { it.value == grupo.unidade }?.label ?: grupo.unidade.ifBlank { null },
                    options = unidadesOptions.map { it.label },
                    placeholder = "",
                    allowEmpty = true,
                    onSelect = { picked -> grupo.unidade = unidadesOptions.firstOrNull { it.label == picked }?.value ?: picked.orEmpty() },
                    onCreate = { raw -> viewModel.quickCreateLookupOption("unidades", raw) { value -> value?.let { grupo.unidade = it } } },
                )
                if (temItem) {
                    Text(historicoTexto(hist), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                grupo.propostas.forEachIndexed { pi, proposta ->
                    PropostaCard(
                        numero = pi + 1,
                        proposta = proposta,
                        quantidade = grupo.quantidade,
                        entidadesOptions = entidadesOptions,
                        formasPgtoOptions = formasPgtoOptions,
                        mediaHistorica = mediaHistorica,
                        viewModel = viewModel,
                        onAdd = onAddProposta,
                        onCopy = { onCopyProposta(pi) },
                        onRemove = if (grupo.propostas.size > 1) ({ onRemoveProposta(pi) }) else null,
                    )
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

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                },
                title = { androidx.compose.material3.ProvideTextStyle(androidx.compose.material3.LocalTextStyle.current.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)) {
                    // v21 -- ícone verde do módulo ao lado do título.
                    androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
                        // v274 (mockup v329) -- sem ícone ao lado do título.

                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Nova Cotação", color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Clip, modifier = Modifier.basicMarquee())
                    }
                
                    }
                } },
                actions = { /* v276 (mockup v333) -- sem o ícone com "+" no formulário */ // v274 (mockup v329)
                    com.bragro.mobile.ui.domain.SalvarTopo(onClick = { viewModel.submit() }, enabled = successMessage == null && viewModel.podeSalvar(), loading = pending, descricao = "Lançar propostas")
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        val dispFechar = androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
                        com.bragro.mobile.ui.domain.FecharIcone(onClick = { dispFechar?.onBackPressed() })
                        }
                        Column {
                            Spacer(modifier = Modifier.height(16.dp))
                            com.bragro.mobile.ui.domain.HomeNavButton() // v275 (mockup v331) -- ✕ volta ao módulo, ⌂ vai ao Início
                    } },
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
                com.bragro.mobile.ui.domain.OcrFotoButton(label = "Preencher com foto da proposta") { url -> viewModel.aplicarOcrFoto(url) }
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
                    /* v201 -- sem DD/MM/AAAA */
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = appFieldColors(),
                    trailingIcon = {
                        IconButton(onClick = { showPicker = true }) {
                            Icon(Icons.Outlined.CalendarMonth, contentDescription = "Escolher data", tint = MaterialTheme.colorScheme.primary)
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
                        DatePicker(state = pickerState, colors = com.bragro.mobile.ui.theme.appDatePickerColors())
                    }
                }
            }
            // v292 (mockup v384c) -- Observações logo abaixo da Data (vale para todos os itens).
            item {
                OutlinedTextField(
                    value = viewModel.observacoes,
                    onValueChange = { viewModel.observacoes = it },
                    label = { Text("Observações (vale para todos os itens)") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = appFieldColors(),
                )
            }
            items(viewModel.grupos.size) { gi ->
                GrupoCard(
                    numero = gi + 1,
                    grupo = viewModel.grupos[gi],
                    categoriasOptions = categoriasOptions,
                    itensOptions = itensOptions,
                    unidadesOptions = unidadesOptions,
                    entidadesOptions = entidadesOptions,
                    formasPgtoOptions = formasPgtoOptions,
                    historico = viewModel.historico,
                    onBuscarHistorico = { cat, it -> viewModel.buscarHistoricoSeNecessario(cat, it) },
                    viewModel = viewModel,
                    onAddGrupo = { viewModel.addGrupo() },
                    onCopyGrupo = { viewModel.copiarGrupo(gi) },
                    onRemoveGrupo = if (viewModel.grupos.size > 1) ({ viewModel.removeGrupo(gi) }) else null,
                    onAddProposta = { viewModel.addProposta(gi) },
                    onCopyProposta = { pi -> viewModel.copiarProposta(gi, pi) },
                    onRemoveProposta = { pi -> viewModel.removeProposta(gi, pi) },
                )
            }
            // v1.6.59 -- botão do final virou ✓ na linha do título.
        }
    }
}
