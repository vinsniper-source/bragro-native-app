package com.bragro.mobile.ui.domain

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.bragro.mobile.ui.theme.appFieldColors
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

// "Lançar nota com itens" EMBUTIDO na sequência de campos do Novo Lançamento
// de Financeiro -- pedido do usuário (achado de auditoria: "não foi
// inserido no native como está na plataforma o módulo lançamentos, está
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
    NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(value)

/** Uma linha de item -- só Item/Unidade/Quantidade (SEM valor unitário: a
 * nota inteira usa o total do campo "Bruto (R$)" do formulário acima, ver
 * comentário no topo do arquivo e criarNotaComItensAction no servidor, que
 * distribui esse total pelos itens proporcional à quantidade). */
class ItemNotaLinha {
    var descricao by mutableStateOf("")
    var unidade by mutableStateOf("")
    var quantidade by mutableStateOf("")
}

class FinanceiroItensInlineViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = NotaMultiItemRepository(app)
    private val configRepository = ConfigRepository(app)

    var itensOptions = mutableStateOf<List<LookupEntity>>(emptyList())
        private set
    var unidadesOptions = mutableStateOf<List<LookupEntity>>(emptyList())
        private set

    val linhas = mutableStateListOf(ItemNotaLinha())

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

    fun addLinha() {
        linhas.add(ItemNotaLinha())
    }

    fun removeLinha(i: Int) {
        if (linhas.size > 1) linhas.removeAt(i)
    }

    fun linhasValidas() = linhas.filter { it.descricao.isNotBlank() && it.quantidade.isNotBlank() }

    fun podeSalvar(docNf: String, data: String, local: String, entidade: String, bruto: String): Boolean =
        docNf.isNotBlank() && data.isNotBlank() && local.isNotBlank() && entidade.isNotBlank() &&
            (bruto.toDoubleOrNull() ?: 0.0) > 0 && linhasValidas().isNotEmpty()

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
        onDone: () -> Unit,
    ) {
        val validas = linhasValidas()
        val brutoNum = bruto.toDoubleOrNull() ?: 0.0
        if (!podeSalvar(docNf, data, local, entidade, bruto)) {
            errorMessage.value = "Preencha Doc/NF, Data, Local, Entidade e Bruto (R$) acima, e adicione ao menos 1 item com Quantidade."
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
                fazendaDestino = local,
                periodo = periodo.ifBlank { "A VISTA" },
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
                    )
                },
            )
            pending.value = false
            if (resultado == null || !resultado.ok) {
                errorMessage.value = resultado?.error ?: "Erro ao lançar a nota."
                return@launch
            }
            // Mesmo motor do site (criarNotaComItensAction): já grava
            // Estoque + Financeiro juntos -- fecha a tela igual um Salvar
            // normal bem-sucedido, sem precisar do botão "Salvar" genérico
            // logo abaixo (que criaria um SEGUNDO lançamento vazio).
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
            for (opt in options) {
                DropdownMenuItem(text = { Text(opt, maxLines = 1, overflow = TextOverflow.Ellipsis) }, onClick = { onSelect(opt); expanded = false })
            }
        }
    }
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

@Composable
private fun ItemNotaLinhaRow(
    linha: ItemNotaLinha,
    itensOptions: List<LookupEntity>,
    unidadesOptions: List<LookupEntity>,
    showRemove: Boolean,
    onRemove: () -> Unit,
) {
    val borderColor = darkerBorderColor(MaterialTheme.colorScheme.surface)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, borderColor)
            .padding(10.dp),
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
                placeholder = "Selecione o item",
                onSelect = { picked -> linha.descricao = itensOptions.firstOrNull { it.label == picked }?.value ?: picked },
            )
        }
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ItemFieldBlock(modifier = Modifier.weight(1f)) {
                ItemStringDropdown(
                    label = "Unidade",
                    value = unidadesOptions.firstOrNull { it.value == linha.unidade }?.label ?: linha.unidade.ifBlank { null },
                    options = unidadesOptions.map { it.label },
                    placeholder = "Opcional",
                    onSelect = { picked -> linha.unidade = unidadesOptions.firstOrNull { it.label == picked }?.value ?: picked },
                )
            }
            ItemFieldBlock(modifier = Modifier.weight(1f)) {
                OutlinedTextField(
                    value = linha.quantidade,
                    onValueChange = { linha.quantidade = it },
                    label = { Text("Quantidade *") },
                    placeholder = { Text("0") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = appFieldColors(),
                )
            }
            if (showRemove) {
                IconButton(onClick = onRemove, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Filled.Delete, contentDescription = "Remover item", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

/** Seção embutida "Lançar nota com itens" -- só chamada pelo
 * DomainFormScreen.kt, logo após o campo Doc/NF, quando domainId ==
 * "financeiro" e recordId == null (criando, não editando). Todos os
 * parâmetros de texto vêm AO VIVO do mesmo DomainFormViewModel.fields do
 * formulário genérico (mesmo critério do site, ver comentário no topo do
 * arquivo). */
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
) {
    val itensOptions by viewModel.itensOptions
    val unidadesOptions by viewModel.unidadesOptions
    val pending by viewModel.pending
    val errorMessage by viewModel.errorMessage
    val brutoNum = bruto.toDoubleOrNull() ?: 0.0
    val podeSalvar = viewModel.podeSalvar(docNf, data, local, entidade, bruto)

    val sectionBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    val sectionBorder = darkerBorderColor(MaterialTheme.colorScheme.surfaceVariant)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(sectionBg)
            .border(1.dp, sectionBorder)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Aviso encurtado + tira label:valor (pedido do usuário: "seja mais
        // direto, TEXTO LONGO") -- mesmo texto/formato já usado no site
        // (nota-multi-item-button.tsx, "Sétima rodada"): o parágrafo cru foi
        // reduzido e a frase corrida "Usa Doc/NF X, Data Y..." virou uma
        // tira curta "Doc/NF: X · Data: Y · Local: Z · Entidade: W", mais
        // rápida de ler igual num resumo de pedido.
        Text("Lançar nota com itens", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        // Aviso resumido de novo -- pedido do usuário ("resuma os avisos e
        // divida os campos"): a versão anterior já tinha sido encurtada uma
        // vez (ver comentário acima), mas ainda cabia num só sentido direto.
        Text(
            "Só pra compras com produto/Estoque. Cada item vira entrada; o total vira Financeiro (não relance).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "Doc/NF: ${docNf.ifBlank { "—" }} · Data: ${data.ifBlank { "—" }} · Local: ${local.ifBlank { "—" }} · Entidade: ${entidade.ifBlank { "—" }}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (errorMessage != null) {
            Text(errorMessage ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Text("Itens da nota *", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        viewModel.linhas.forEachIndexed { i, linha ->
            ItemNotaLinhaRow(
                linha = linha,
                itensOptions = itensOptions,
                unidadesOptions = unidadesOptions,
                showRemove = viewModel.linhas.size > 1,
                onRemove = { viewModel.removeLinha(i) },
            )
        }
        OutlinedButton(onClick = { viewModel.addLinha() }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.padding(end = 6.dp).size(16.dp))
            Text("Adicionar item")
        }

        Text(
            "Total da nota: ${formatMoneyBrlLocal(brutoNum)} (usa o campo Bruto (R$) preenchido mais abaixo)",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )

        Button(
            onClick = {
                viewModel.submit(docNf, data, local, entidade, safra, cultura, setor, banco, formaPgto, periodo, bruto, onDone)
            },
            enabled = !pending && podeSalvar,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (pending) {
                CircularProgressIndicator(modifier = Modifier.padding(4.dp).size(18.dp))
            } else {
                val n = viewModel.linhasValidas().size
                Text(if (n > 0) "Lançar $n item(ns)" else "Lançar itens")
            }
        }
    }
}
