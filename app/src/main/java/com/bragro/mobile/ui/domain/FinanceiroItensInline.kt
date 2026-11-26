package com.bragro.mobile.ui.domain

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import com.bragro.mobile.ui.theme.LinhaTextField as OutlinedTextField
import androidx.compose.material3.Surface
import com.bragro.mobile.ui.i18n.Text
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
import com.bragro.mobile.data.model.NotaMultiItemItemData
import com.bragro.mobile.data.repo.ConfigRepository
import com.bragro.mobile.data.repo.NotaMultiItemRepository
import com.bragro.mobile.ui.theme.SearchableDropdownField
import com.bragro.mobile.ui.theme.appFieldColors
import kotlinx.coroutines.launch
import kotlinx.serialization.json.contentOrNull
import java.text.NumberFormat
import java.util.Locale

// "Lançar nota com itens" EMBUTIDO na sequência de campos do Novo Registro
// de Financeiro -- pedido do usuário (achado de auditoria: "não foi
// inserido no native como está na plataforma o módulo registros, está
// faltando adicionar itens na sequência dos campos, como está em
// plataforma"). Réplica do que o SITE já faz (ver nota-multi-item-button.tsx
// + insertAfterField="docNf" em data-table.tsx): esta seção aparece INLINE,
// logo depois do campo Doc/NF do formulário genérico (ver DomainFormScreen.kt),
// em vez de um ícone que abria uma tela separada com campos duplicados (isso
// existia -- NotaMultiItemScreen.kt -- mas ficou desatualizado em relação ao
// site: repetia Número/Fornecedor/Data/Fazenda como campos PRÓPRIOS, e ainda
// cobrava "Valor unitário" por item, coisas que o site já tinha removido
// há várias rodadas). Doc/NF, Data, Local, Entidade, Safra, Cultura, Setor,
// Banco, Forma Pgto., Período e Bruto (R$) são lidos AO VIVO do mesmo
// DomainFormViewModel.fields do formulário genérico -- exatamente como o
// site lê os campos do RecordForm por cima via props -- então não existe
// campo duplicado nem risco de os dois ficarem dessincronizados.

private fun parseDecimalLocal(s: String): Double =
    s.trim().replace(".", "").replace(",", ".").toDoubleOrNull()
        ?: s.trim().toDoubleOrNull()
        ?: 0.0

private fun formatMoneyBrlLocal(value: Double): String =
    com.bragro.mobile.ui.i18n.MoedaApp.formatar(value)

/** Uma linha de item -- só Item/Unidade/Quantidade (SEM valor unitário: a
 * nota inteira usa o total do campo "Bruto (R$)" do formulário acima, ver
 * comentário no topo do arquivo e criarNotaComItensAction no servidor, que
 * distribui esse total pelos itens proporcional à quantidade). */
class ItemNotaLinha {
    var descricao by mutableStateOf("")
    var unidade by mutableStateOf("")
    var quantidade by mutableStateOf("")
    // v410 (mockup v405) -- valor e fazenda do item.
    var valor by mutableStateOf("")
    var fazenda by mutableStateOf("")
}

/** "1.200,50" ou "1200.50" -> 1200.5 */
private fun valorBr(s: String): Double {
    val t = s.trim().replace("R$", "").trim()
    return (if (t.contains(',')) t.replace(".", "").replace(",", ".") else t).toDoubleOrNull() ?: 0.0
}

class FinanceiroItensInlineViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = NotaMultiItemRepository(app)
    private val configRepository = ConfigRepository(app)

    var itensOptions = mutableStateOf<List<LookupEntity>>(emptyList())
        private set
    var unidadesOptions = mutableStateOf<List<LookupEntity>>(emptyList())
        private set

    val linhas = mutableStateListOf(ItemNotaLinha())
    // v410 (mockup v405) -- fazenda: a mesma em todos (Local) ou uma por item.
    var porItem by mutableStateOf(false)

    var pending = mutableStateOf(false)
        private set
    var errorMessage = mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch {
            itensOptions.value = configRepository.lookupsByCategory("itens_estoque").sortedBy { it.label }
            unidadesOptions.value = configRepository.lookupsByCategory("unidades").sortedBy { it.label }
        }
    }

    /** "+Criar" (Task #869) -- mesmo motor quick-create-lookup usado em
     * Prescrição/Pedidos/Cotações, aplicado a Item/Unidade desta seção
     * embutida. */
    fun quickCreateLookupOption(category: String, rawValue: String, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            val result = com.bragro.mobile.data.repo.ModuleActionsRepository(getApplication()).run(
                "quick-create-lookup", domainId = "financeiro", category = category, rawValue = rawValue,
            )
            val value = (result?.get("value") as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
            if (!value.isNullOrBlank()) {
                fun merge(current: List<LookupEntity>) =
                    if (current.any { it.value == value }) current
                    else (current + LookupEntity(category, value, value, current.size)).sortedBy { it.label }
                when (category) {
                    "itens_estoque" -> itensOptions.value = merge(itensOptions.value)
                    "unidades" -> unidadesOptions.value = merge(unidadesOptions.value)
                }
                onDone(value)
            } else {
                onDone(null)
            }
        }
    }

    fun addLinha() {
        linhas.add(ItemNotaLinha())
    }

    fun removeLinha(i: Int) {
        if (linhas.size > 1) linhas.removeAt(i)
    }

    fun linhasValidas() = linhas.filter { it.descricao.isNotBlank() && it.quantidade.isNotBlank() }

    fun comValor(): Boolean = linhasValidas().let { v -> v.isNotEmpty() && v.all { valorBr(it.valor) > 0 } }

    /** Total da nota: soma dos valores dos itens; sem valor nos itens, o campo Bruto (R$). */
    // v414 -- contas em ParcelasCalc (testadas em ParcelasCalcTest.kt).
    private fun itensCalc(local: String) = linhasValidas().map { ParcelasCalc.ItemCalc(fazDe(it, local), parseDecimalLocal(it.quantidade), valorBr(it.valor)) }

    fun total(bruto: String): Double =
        ParcelasCalc.totalNota(itensCalc(""), bruto.toDoubleOrNull() ?: valorBr(bruto))

    fun fazDe(l: ItemNotaLinha, local: String): String = if (porItem && l.fazenda.isNotBlank()) l.fazenda else local

    fun porFazenda(local: String, bruto: String): Map<String, Double> =
        ParcelasCalc.porFazenda(itensCalc(local), bruto.toDoubleOrNull() ?: valorBr(bruto))

    fun podeSalvar(docNf: String, data: String, local: String, entidade: String, bruto: String): Boolean =
        docNf.isNotBlank() && data.isNotBlank() && entidade.isNotBlank() &&
            total(bruto) > 0 && linhasValidas().isNotEmpty() && linhasValidas().all { fazDe(it, local).isNotBlank() }

    fun submit(
        docNf: String,
        data: String,
        local: String,
        entidade: String,
        safra: String,
        cultura: String,
        setor: String,
        banco: String,
        formaPgto: String,
        periodo: String,
        bruto: String,
        plano: String?,
        onDone: () -> Unit,
        ajuste: String = "",
    ) {
        val validas = linhasValidas()
        val brutoNum = total(bruto)
        val comV = comValor()
        // v410 -- parcelas do bloco unificado (mesmo JSON do formulário).
        val planoLista = plano?.let { p ->
            runCatching {
                val arr = org.json.JSONArray(p)
                (0 until arr.length()).map { k ->
                    val o = arr.getJSONObject(k)
                    com.bragro.mobile.data.model.PlanoParcelaData(
                        vcto = o.optString("vcto").takeIf { it.isNotBlank() },
                        local = o.optString("local").takeIf { it.isNotBlank() },
                        ajuste = if (o.has("ajuste")) o.optDouble("ajuste") else null,
                    )
                }
            }.getOrNull()
        }
        if (!podeSalvar(docNf, data, local, entidade, bruto)) {
            errorMessage.value = "Preencha Doc/NF, Data, Local (ou a fazenda de cada item), Entidade e o valor, e adicione ao menos 1 item com Quantidade."
            return
        }
        pending.value = true
        errorMessage.value = null
        viewModelScope.launch {
            val resultado = repository.criar(
                numero = docNf.trim(),
                serie = null,
                emitenteNome = entidade.trim(),
                // "data" chega em DD/MM/AAAA (mesmo padrão de todo campo
                // "date" do formulário genérico, ver DomainFormScreen.kt) --
                // só vira ISO aqui, na hora de montar o corpo pro servidor.
                dataEmissao = brDateToIso(data),
                fazendaDestino = local.ifBlank { validas.firstOrNull()?.let { fazDe(it, local) } ?: "" },
                periodo = periodo.ifBlank { "À VISTA" },
                safra = safra.ifBlank { null },
                cultura = cultura.ifBlank { null },
                setor = setor.ifBlank { null },
                banco = banco.ifBlank { null },
                formaPgto = formaPgto.ifBlank { null },
                bruto = brutoNum,
                itens = validas.map {
                    NotaMultiItemItemData(
                        descricao = it.descricao,
                        quantidade = parseDecimalLocal(it.quantidade),
                        unidade = it.unidade.ifBlank { null },
                        fazenda = if (porItem) it.fazenda.ifBlank { null } else null,
                        valor = if (comV) valorBr(it.valor) else null,
                    )
                },
                plano = planoLista,
                ajuste = ajuste.toDoubleOrNull()?.takeIf { it != 0.0 },
            )
            pending.value = false
            if (resultado == null || !resultado.ok) {
                errorMessage.value = resultado?.error ?: "Erro ao lançar a nota."
                return@launch
            }
            // Mesmo motor do site (criarNotaComItensAction): já grava
            // Estoque + Financeiro juntos -- fecha a tela igual um Salvar
            // normal bem-sucedido, sem precisar do botão "Salvar" genérico
            // logo abaixo (que criaria um SEGUNDO registro vazio).
            onDone()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItemStringDropdown(
    label: String,
    value: String?,
    options: List<String>,
    placeholder: String,
    onSelect: (String) -> Unit,
    // "+Criar" (Task #869) -- paridade com o onCreate de SearchableDropdownField.
    onCreate: ((String) -> Unit)? = null,
) {
    SearchableDropdownField(
        value = value ?: "",
        label = label,
        options = remember(options) { options.map { it to it } },
        onSelect = onSelect,
        placeholder = placeholder,
        // Campo obrigatório em ambos os call sites (Item/Unidade) -- não faz
        // sentido oferecer "(nenhuma)" pra esvaziar, mesmo comportamento de
        // antes (readOnly não tinha opção de limpar).
        emptyOptionLabel = null,
        onCreate = onCreate,
    )
}

// Bloco individual por campo -- pedido do usuário ("crie blocos
// individuais" nos itens; screenshot mostrando "Itens da nota" com campos
// soltos sem nenhum bloco visível, mesmo tendo o contorno externo da linha
// inteira). Scrim (onSurface alpha baixo) em vez de um papel de cor fixo:
// cria contraste com QUALQUER fundo por trás, mesmo critério já usado em
// CotacaoMultiItemScreen.kt/PedidoMultiItemScreen.kt.
// alpha 0.05f -> 0.12f: 5% era imperceptível num display real (usuário
// reinstalou o 1.2.47 do zero e ainda não enxergou o bloco) -- mesmo ajuste
// aplicado nos outros dois arquivos.
@Composable
private fun ItemFieldBlock(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    com.bragro.mobile.ui.theme.StandardFieldBlock(modifier = modifier, content = content)
}

@Composable
private fun ItemNotaLinhaRow(
    linha: ItemNotaLinha,
    itensOptions: List<LookupEntity>,
    unidadesOptions: List<LookupEntity>,
    viewModel: FinanceiroItensInlineViewModel,
    showRemove: Boolean,
    onRemove: () -> Unit,
    locais: List<String> = emptyList(),
    localPadrao: String = "",
) {
    // Sem fundo/borda próprios: os campos já são blocos padrão dentro do
    // bloco externo (mesmo fundo nos dois, sem faixa de tom diferente).
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Item fica sozinho na própria linha (dropdown de pesquisa, precisa
        // de largura pra mostrar nomes longos); Unidade + Quantidade dividem
        // a linha de baixo -- pedido do usuário ("divida os campos"), em vez
        // de cada campo numa linha própria.
        ItemFieldBlock {
            ItemStringDropdown(
                label = "Item *",
                value = itensOptions.firstOrNull { it.value == linha.descricao }?.label ?: linha.descricao.ifBlank { null },
                options = itensOptions.map { it.label },
                placeholder = "",
                onSelect = { picked -> linha.descricao = itensOptions.firstOrNull { it.label == picked }?.value ?: picked },
                onCreate = { raw -> viewModel.quickCreateLookupOption("itens_estoque", raw) { value -> value?.let { linha.descricao = it } } },
            )
        }
        // v219 (mockup aprovado) -- um campo por linha.
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ItemFieldBlock(modifier = Modifier.fillMaxWidth()) {
                ItemStringDropdown(
                    label = "Unidade",
                    value = unidadesOptions.firstOrNull { it.value == linha.unidade }?.label ?: linha.unidade.ifBlank { null },
                    options = unidadesOptions.map { it.label },
                    placeholder = "",
                    onSelect = { picked -> linha.unidade = unidadesOptions.firstOrNull { it.label == picked }?.value ?: picked },
                    onCreate = { raw -> viewModel.quickCreateLookupOption("unidades", raw) { value -> value?.let { linha.unidade = it } } },
                )
            }
            ItemFieldBlock(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = linha.quantidade,
                    onValueChange = { linha.quantidade = it },
                    label = { Text("Quantidade *") },

                    modifier = Modifier.fillMaxWidth(),
                    colors = appFieldColors(),
                )
            }
            // v410 (mockup v405) -- Valor do item (o total da nota é a soma).
            ItemFieldBlock(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = linha.valor,
                    onValueChange = { linha.valor = it.filter { c -> c.isDigit() || c == ',' || c == '.' } },
                    label = { Text("Valor") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    colors = appFieldColors(),
                )
            }
            if (viewModel.porItem) {
                ItemFieldBlock(modifier = Modifier.fillMaxWidth()) {
                    ItemStringDropdown(
                        label = "Fazenda *",
                        value = linha.fazenda.ifBlank { localPadrao }.ifBlank { null }?.let { com.bragro.mobile.ui.domain.exibirTexto(it, true) },
                        options = locais.map { com.bragro.mobile.ui.domain.exibirTexto(it, true) },
                        placeholder = "",
                        onSelect = { picked -> linha.fazenda = locais.firstOrNull { com.bragro.mobile.ui.domain.exibirTexto(it, true) == picked } ?: picked },
                    )
                }
            }
            if (showRemove) {
                IconButton(onClick = onRemove, modifier = Modifier.size(30.dp).align(androidx.compose.ui.Alignment.End)) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Remover item", tint = com.bragro.mobile.ui.domain.corAtencao(), modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

/** v410 (mockup v405 aprovado) -- bloco único "Lançar nota com itens e
 * parcelas", no FIM do formulário de Financeiro (regra: blocos internos ficam
 * no final). Itens em cima (cada um com Valor e, se "Por item", Fazenda) e
 * as Parcelas embaixo (slot [parcelas]). Campos do cabeçalho vêm AO VIVO do
 * formulário genérico. */
@Composable
fun FinanceiroItensInlineSection(
    docNf: String,
    data: String,
    local: String,
    entidade: String,
    safra: String,
    cultura: String,
    setor: String,
    banco: String,
    formaPgto: String,
    periodo: String,
    bruto: String,
    onDone: () -> Unit,
    viewModel: FinanceiroItensInlineViewModel = viewModel(),
    locais: List<String> = emptyList(),
    plano: String? = null,
    ajuste: String = "",
    parcelas: @Composable () -> Unit = {},
) {
    val itensOptions by viewModel.itensOptions
    val unidadesOptions by viewModel.unidadesOptions
    val pending by viewModel.pending
    val errorMessage by viewModel.errorMessage
    val total = viewModel.total(bruto)
    val podeSalvar = viewModel.podeSalvar(docNf, data, local, entidade, bruto)
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
    val sectionBg = MaterialTheme.colorScheme.surface
    val verde = MaterialTheme.colorScheme.primary
    @Composable
    fun opcao(rotulo: String, on: Boolean, onClick: () -> Unit) {
        Text(
            rotulo,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 2.dp, vertical = 4.dp)
                .linhaEmbaixo(if (on) verde else androidx.compose.ui.graphics.Color.Transparent),
        )
    }
    androidx.compose.runtime.CompositionLocalProvider(com.bragro.mobile.ui.theme.LocalBlocoItens provides true) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(sectionBg, shape)
            .border(com.bragro.mobile.ui.theme.thinGreenBorder(), shape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("Itens e parcelas", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            com.bragro.mobile.ui.theme.DicaInfo(
                "Itens entram no Estoque (cada um na sua fazenda); o total, no Financeiro, um lançamento por parcela.\n" +
                    "Doc/NF: ${docNf.ifBlank { "—" }} · Data: ${data.ifBlank { "—" }} · Fornecedor: ${entidade.ifBlank { "—" }}",
            )
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            val n = viewModel.linhasValidas().size
            com.bragro.mobile.ui.domain.SalvarIcone(
                onClick = { viewModel.submit(docNf, data, local, entidade, safra, cultura, setor, banco, formaPgto, periodo, bruto, plano, onDone, ajuste) },
                enabled = podeSalvar,
                loading = pending,
                descricao = if (n > 0) "Lançar $n item(ns)" else "Lançar itens",
            )
        }
        if (errorMessage != null) {
            Text(errorMessage ?: "", color = com.bragro.mobile.ui.domain.corAtencao(), style = MaterialTheme.typography.bodySmall)
        }
        if (locais.isNotEmpty()) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Fazenda:", style = MaterialTheme.typography.labelMedium)
                opcao("Mesma em todos", !viewModel.porItem) { viewModel.porItem = false }
                opcao("Por item", viewModel.porItem) { viewModel.porItem = true }
            }
        }
        viewModel.linhas.forEachIndexed { i, linha ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(com.bragro.mobile.ui.theme.thinGreenBorder(), shape)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("Item ${i + 1}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                    IconButton(onClick = { viewModel.addLinha() }) {
                        Icon(Icons.Outlined.Add, contentDescription = "Adicionar item")
                    }
                    if (viewModel.linhas.size > 1) {
                        IconButton(onClick = { viewModel.removeLinha(i) }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Remover item", tint = com.bragro.mobile.ui.domain.corAtencao(), modifier = Modifier.size(20.dp))
                        }
                    }
                }
                ItemNotaLinhaRow(
                    linha = linha,
                    itensOptions = itensOptions,
                    unidadesOptions = unidadesOptions,
                    viewModel = viewModel,
                    showRemove = false,
                    onRemove = { viewModel.removeLinha(i) },
                    locais = locais,
                    localPadrao = local,
                )
            }
        }
        val porFaz = viewModel.porFazenda(local, bruto)
        Text(
            "Total da nota: ${formatMoneyBrlLocal(total)}" +
                if (viewModel.porItem && porFaz.size > 1) " (" + porFaz.entries.joinToString(" · ") { "${com.bragro.mobile.ui.domain.exibirTexto(it.key, true)} ${formatMoneyBrlLocal(it.value)}" } + ")" else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        parcelas()
    }
    }
}

private fun Modifier.linhaEmbaixo(cor: androidx.compose.ui.graphics.Color): Modifier = this.then(
    Modifier.drawBehind {
        drawLine(cor, androidx.compose.ui.geometry.Offset(0f, size.height), androidx.compose.ui.geometry.Offset(size.width, size.height), 1.5.dp.toPx())
    },
)
