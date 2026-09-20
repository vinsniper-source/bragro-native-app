package com.bragro.mobile.ui.orcamento

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bragro.mobile.data.model.OrcamentoData
import com.bragro.mobile.data.repo.OrcamentoRepository
import com.bragro.mobile.ui.domain.LabeledIconButton
import com.bragro.mobile.ui.domain.ModuleIconButton
import com.bragro.mobile.ui.domain.ModuleIconItem
import com.bragro.mobile.ui.domain.PeriodoCategoria
import com.bragro.mobile.ui.domain.genericPeriodoRange
import com.bragro.mobile.ui.domain.isoDateOnly
import com.bragro.mobile.ui.domain.isoDateToBr
import com.bragro.mobile.ui.print.HtmlPrinter
import com.bragro.mobile.ui.theme.Card
import com.bragro.mobile.ui.theme.appFieldColors
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

/**
 * Lista/histórico de Orçamentos (Task #710, achado de auditoria de paridade
 * site-vs-native: só existia a tela "Novo Orçamento" -- OrcamentoScreen.kt --
 * o usuário conseguia lançar (inclusive via OCR) mas não via/filtrava/
 * conferia orçamentos antigos pelo app, só pelo site, ver orcamento-client.tsx).
 * Reaproveita OrcamentoRepository.listar() (já existia, usado só por
 * "Copiar último lançamento" dentro do form) e o mesmo padrão de
 * filtro/período/alternância Tabela-Bloco/impressão do resto do app (ver
 * DomainListScreen.kt) -- só que numa tela própria, já que Orçamentos não é
 * um domínio genérico do registry (tabela própria Orcamento/OrcamentoItem).
 *
 * Sem cache offline de propósito, mesmo critério do resto do módulo (ver
 * OrcamentoRepository.kt): recarrega do servidor sempre que a tela abre.
 *
 * "Fornecedor" não aparece como coluna/filtro aqui -- o payload de
 * listOrcamentosAction() (site) só devolve fornecedorId (FK crua, sem
 * include do Partner), e não existe hoje nenhum endpoint mobile que resolva
 * esse id pro nome (o dropdown de Fornecedor do form usa texto livre da
 * categoria "entidades_financeiro", não os ids de Partner já gravados). Sem
 * essa peça de infraestrutura, mostrar um ID cru seria pior que omitir.
 */
class OrcamentoListViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = OrcamentoRepository(app)

    var orcamentos = mutableStateOf<List<OrcamentoData>>(emptyList())
        private set
    var carregando = mutableStateOf(true)
        private set
    var erro = mutableStateOf<String?>(null)
        private set

    init { carregar() }

    fun carregar() {
        carregando.value = true
        erro.value = null
        viewModelScope.launch {
            val resposta = repository.listar()
            carregando.value = false
            if (resposta == null || !resposta.ok) {
                erro.value = resposta?.error ?: "Sem conexão -- não foi possível carregar os orçamentos agora."
                return@launch
            }
            orcamentos.value = resposta.orcamentos ?: emptyList()
        }
    }
}

private val STATUS_FILTRO_OPTIONS = listOf("PENDENTE_NF" to "Pendente NF", "FATURADO" to "Faturado")

private fun formatoMoeda(valor: Double): String =
    NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(valor)

private fun totalOrcamento(o: OrcamentoData): Double = o.itens.sumOf { it.quantidade * it.valorUnitario }

private fun formatQtdSimples(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString().replace(".", ",")

// Mesmo padrão de dropdown por texto usado no form (OrcamentoScreen.kt) --
// cópia file-private, sem conflito de nome (top-level "private" em Kotlin é
// visível só dentro do próprio arquivo).
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
private fun OrcamentoStatusBadge(status: String) {
    val faturado = status == "FATURADO"
    val bg = if (faturado) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.secondaryContainer
    val fg = if (faturado) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSecondaryContainer
    Text(
        if (faturado) "Faturado" else "Pendente NF",
        style = MaterialTheme.typography.labelMedium,
        color = fg,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

@Composable
private fun OrcamentoCard(o: OrcamentoData, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    o.numeroOrcamento?.let { "Orçamento $it" } ?: "Orçamento sem número",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 6.dp),
                )
                OrcamentoStatusBadge(o.status)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(isoDateToBr(isoDateOnly(o.data)), style = MaterialTheme.typography.bodySmall)
                Text(formatoMoeda(totalOrcamento(o)), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            }
            if (!o.requisicao.isNullOrBlank()) {
                Text("Requisição: ${o.requisicao}", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (!o.autorizadoPorNome.isNullOrBlank()) {
                Text("Autorizado por: ${o.autorizadoPorNome}", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("${o.itens.size} item(ns)", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun TableCell(text: String, width: Dp, bold: Boolean = false) {
    Text(
        text,
        modifier = Modifier.width(width).padding(horizontal = 4.dp),
        style = MaterialTheme.typography.bodySmall,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

// Vista "Tabela" -- linhas mais compactas que os cards de bloco, mesmo par de
// alternância (ícone+rótulo Tabela/Bloco) já usado no resto do app (ver
// DomainListScreen.kt). Rolagem em 2 eixos (fillMaxSize horizontal + vertical
// dentro) porque as 7 colunas não cabem na largura de um aparelho -- lista já
// vem limitada a 200 registros do servidor (listOrcamentosAction), então não
// compensa a complexidade de um LazyColumn com ScrollState horizontal
// compartilhado só pra esse volume.
@Composable
private fun OrcamentoTable(orcamentos: List<OrcamentoData>, onClick: (OrcamentoData) -> Unit) {
    Box(modifier = Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            Row(modifier = Modifier.padding(vertical = 6.dp)) {
                TableCell("Data", 84.dp, bold = true)
                TableCell("Nº Orçamento", 110.dp, bold = true)
                TableCell("Requisição", 100.dp, bold = true)
                TableCell("Autorizado por", 130.dp, bold = true)
                TableCell("Itens", 50.dp, bold = true)
                TableCell("Total", 110.dp, bold = true)
                TableCell("Status", 110.dp, bold = true)
            }
            HorizontalDivider()
            orcamentos.forEach { o ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onClick(o) }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TableCell(isoDateToBr(isoDateOnly(o.data)), 84.dp)
                    TableCell(o.numeroOrcamento ?: "—", 110.dp)
                    TableCell(o.requisicao ?: "—", 100.dp)
                    TableCell(o.autorizadoPorNome ?: "—", 130.dp)
                    TableCell(o.itens.size.toString(), 50.dp)
                    TableCell(formatoMoeda(totalOrcamento(o)), 110.dp)
                    Box(modifier = Modifier.width(110.dp)) { OrcamentoStatusBadge(o.status) }
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun OrcamentoViewDialog(o: OrcamentoData, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(o.numeroOrcamento?.let { "Orçamento $it" } ?: "Orçamento sem número") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Data: ${isoDateToBr(isoDateOnly(o.data))}", style = MaterialTheme.typography.bodySmall)
                if (!o.requisicao.isNullOrBlank()) Text("Requisição: ${o.requisicao}", style = MaterialTheme.typography.bodySmall)
                if (!o.autorizadoPorNome.isNullOrBlank()) Text("Autorizado por: ${o.autorizadoPorNome}", style = MaterialTheme.typography.bodySmall)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Status", style = MaterialTheme.typography.bodySmall)
                    OrcamentoStatusBadge(o.status)
                }
                HorizontalDivider()
                o.itens.forEach { item ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            "${item.item}${if (!item.unidade.isNullOrBlank()) " (${item.unidade})" else ""} — ${formatQtdSimples(item.quantidade)}x",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(end = 6.dp),
                        )
                        Text(formatoMoeda(item.quantidade * item.valorUnitario), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                }
                HorizontalDivider()
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total", fontWeight = FontWeight.Bold)
                    Text(formatoMoeda(totalOrcamento(o)), fontWeight = FontWeight.Bold)
                }
                if (!o.observacoes.isNullOrBlank()) {
                    Text(o.observacoes, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } },
    )
}

@Composable
private fun FiltroDialog(statusAtual: String?, onDismiss: () -> Unit, onAplicar: (String?) -> Unit) {
    var status by remember { mutableStateOf(statusAtual) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filtros") },
        text = {
            StringDropdown(
                label = "Status",
                value = STATUS_FILTRO_OPTIONS.firstOrNull { it.first == status }?.second,
                options = STATUS_FILTRO_OPTIONS.map { it.second },
                placeholder = "Todos",
                allowEmpty = true,
                onSelect = { picked -> status = STATUS_FILTRO_OPTIONS.firstOrNull { it.second == picked }?.first },
            )
        },
        confirmButton = { TextButton(onClick = { onAplicar(status) }) { Text("Aplicar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

// Mesmas categorias rápidas do Período do site (PERIODOS em
// orcamento-client.tsx), calculadas em cima de genericPeriodoRange
// (ui/domain/PeriodoFilters.kt) -- reaproveitado em vez de duplicar a lógica
// de janela de datas.
private val PERIODO_RAPIDO = listOf(
    PeriodoCategoria.DIARIO to "Hoje",
    PeriodoCategoria.SEMANAL to "7 dias",
    PeriodoCategoria.QUINZENAL to "15 dias",
    PeriodoCategoria.MENSAL to "Este mês",
    PeriodoCategoria.TRIMESTRAL to "3 meses",
    PeriodoCategoria.ANUAL to "1 ano",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PeriodoDialog(from: String, to: String, onDismiss: () -> Unit, onAplicar: (String, String) -> Unit) {
    var deInput by remember { mutableStateOf(from) }
    var ateInput by remember { mutableStateOf(to) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Período") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Filtra pela Data do orçamento.", style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    PERIODO_RAPIDO.forEach { (categoria, label) ->
                        TextButton(onClick = {
                            val (f, t) = genericPeriodoRange(categoria)
                            deInput = f
                            ateInput = t
                        }) { Text(label, style = MaterialTheme.typography.labelSmall) }
                    }
                }
                OutlinedTextField(
                    value = deInput,
                    onValueChange = { deInput = it },
                    label = { Text("De") },
                    placeholder = { Text("AAAA-MM-DD") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = appFieldColors(),
                )
                OutlinedTextField(
                    value = ateInput,
                    onValueChange = { ateInput = it },
                    label = { Text("Até") },
                    placeholder = { Text("AAAA-MM-DD") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = appFieldColors(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onAplicar(deInput.trim(), ateInput.trim()) }) { Text("Aplicar") } },
        dismissButton = {
            Row {
                TextButton(onClick = { onAplicar("", "") }) { Text("Limpar") }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrcamentoListScreen(onBack: () -> Unit, onNovo: () -> Unit, viewModel: OrcamentoListViewModel = viewModel()) {
    val orcamentos by viewModel.orcamentos
    val carregando by viewModel.carregando
    val erro by viewModel.erro
    val context = LocalContext.current

    var statusFiltro by remember { mutableStateOf<String?>(null) }
    var dateFrom by remember { mutableStateOf("") }
    var dateTo by remember { mutableStateOf("") }
    var tableView by remember { mutableStateOf(false) }
    var showFiltroDialog by remember { mutableStateOf(false) }
    var showPeriodoDialog by remember { mutableStateOf(false) }
    var viewingOrcamento by remember { mutableStateOf<OrcamentoData?>(null) }

    val filtered = remember(orcamentos, statusFiltro, dateFrom, dateTo) {
        orcamentos.filter { o ->
            if (statusFiltro != null && o.status != statusFiltro) return@filter false
            val dia = isoDateOnly(o.data).take(10)
            if (dateFrom.isNotBlank() && dia < dateFrom) return@filter false
            if (dateTo.isNotBlank() && dia > dateTo) return@filter false
            true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Orçamentos", maxLines = 1, overflow = TextOverflow.Clip, color = MaterialTheme.colorScheme.primary)
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
                        IconButton(onClick = {
                            val headers = listOf("Data", "Nº Orçamento", "Requisição", "Autorizado por", "Itens", "Total", "Status")
                            val rows = filtered.map { o ->
                                listOf(
                                    isoDateToBr(isoDateOnly(o.data)),
                                    o.numeroOrcamento ?: "",
                                    o.requisicao ?: "",
                                    o.autorizadoPorNome ?: "",
                                    o.itens.size.toString(),
                                    formatoMoeda(totalOrcamento(o)),
                                    if (o.status == "FATURADO") "Faturado" else "Pendente NF",
                                )
                            }
                            HtmlPrinter.printSimpleTable(context, "Orçamentos", headers, rows)
                        }) {
                            Icon(Icons.Filled.Print, contentDescription = "Imprimir", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNovo,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) { Icon(Icons.Filled.Add, contentDescription = "Novo orçamento") }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ModuleIconButton(
                    ModuleIconItem("filtros", Icons.Filled.FilterAlt, "Filtros", active = statusFiltro != null, badgeCount = if (statusFiltro != null) 1 else 0),
                ) { showFiltroDialog = true }
                ModuleIconButton(
                    ModuleIconItem("periodo", Icons.Filled.CalendarMonth, "Período", active = dateFrom.isNotBlank() || dateTo.isNotBlank()),
                ) { showPeriodoDialog = true }
                LabeledIconButton(icon = Icons.Filled.Refresh, label = "Atualizar", loading = carregando, onClick = { viewModel.carregar() })
                LabeledIconButton(
                    icon = if (tableView) Icons.Filled.ViewAgenda else Icons.Filled.TableChart,
                    label = if (tableView) "Bloco" else "Tabela",
                    onClick = { tableView = !tableView },
                )
            }
            Text(
                "${filtered.size} orçamento(s)${if (filtered.size != orcamentos.size) " de ${orcamentos.size}" else ""}. Pendente NF aguarda a nota mãe pra conciliação.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
            if (erro != null) {
                Text(erro ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 12.dp))
            }
            when {
                carregando && orcamentos.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
                erro != null && orcamentos.isEmpty() -> {
                    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        OutlinedButton(onClick = { viewModel.carregar() }) { Text("Tentar novamente") }
                    }
                }
                filtered.isEmpty() -> {
                    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (orcamentos.isEmpty()) "Nenhum orçamento lançado ainda." else "Nenhum orçamento encontrado para esse filtro.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                tableView -> {
                    OrcamentoTable(filtered) { viewingOrcamento = it }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(filtered, key = { it.id }) { o ->
                            OrcamentoCard(o) { viewingOrcamento = o }
                        }
                    }
                }
            }
        }
    }

    if (showFiltroDialog) {
        FiltroDialog(
            statusAtual = statusFiltro,
            onDismiss = { showFiltroDialog = false },
            onAplicar = { statusFiltro = it; showFiltroDialog = false },
        )
    }
    if (showPeriodoDialog) {
        PeriodoDialog(
            from = dateFrom,
            to = dateTo,
            onDismiss = { showPeriodoDialog = false },
            onAplicar = { f, t -> dateFrom = f; dateTo = t; showPeriodoDialog = false },
        )
    }
    val viewing = viewingOrcamento
    if (viewing != null) {
        OrcamentoViewDialog(viewing) { viewingOrcamento = null }
    }
}
