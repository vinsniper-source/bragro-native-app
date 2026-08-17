package com.bragro.mobile.ui.insumos

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.KeyboardDoubleArrowDown
import androidx.compose.material.icons.filled.KeyboardDoubleArrowUp
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.WarningAmber
import com.bragro.mobile.ui.theme.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bragro.mobile.data.NetworkStatus
import com.bragro.mobile.data.model.ColumnConfig
import com.bragro.mobile.data.model.ControleInsumosResponse
import com.bragro.mobile.data.model.DomainConfig
import com.bragro.mobile.data.model.InsumoItemSituacaoData
import com.bragro.mobile.data.model.InsumoSaldoData
import com.bragro.mobile.data.model.InsumosRamoItemData
import com.bragro.mobile.data.repo.ControleInsumosRepository
import com.bragro.mobile.ui.domain.BarSeries
import com.bragro.mobile.ui.domain.FarmSelectorButton
import com.bragro.mobile.ui.domain.LabeledIconButton
import com.bragro.mobile.ui.domain.SimpleBarChart
import com.bragro.mobile.ui.domain.exportXlsx
import com.bragro.mobile.ui.print.HtmlPrinter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

// Painel "Controle de Insumos" (gap encontrado na auditoria módulo-a-módulo
// contra o site, pedido do usuário "implemente tudo que falta ainda para o
// app native da plataforma") -- SOMENTE-LEITURA, réplica de
// src/app/(app)/controle-de-insumos/page.tsx: situação consolidada por item
// (Pedido → Entrega → Estoque → Aplicação, agrupada por Safra/Frota/ADM),
// itens críticos com previsão de ruptura, árvore de Controle Interno
// (EPI/uniforme/ferramenta) e saldo top-10 (gráfico). Via
// /api/mobile/controle-insumos, que reaproveita as MESMAS funções de
// lib/services/insumos-arvore.ts que o site usa -- nada recalculado em
// Kotlin.
class ControleInsumosViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = ControleInsumosRepository(app)

    var data = mutableStateOf<ControleInsumosResponse?>(null)
        private set
    var loading = mutableStateOf(false)
        private set
    var offline = mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            repository.observeCached().collectLatest { entity ->
                if (entity != null) data.value = repository.parse(entity)
            }
        }
        refresh()
    }

    fun refresh() {
        if (loading.value) return
        loading.value = true
        viewModelScope.launch {
            val ok = repository.refresh(null)
            offline.value = !ok
            loading.value = false
        }
    }
}

private val PT_BR = Locale("pt", "BR")
private fun fmtNum(v: Double): String = NumberFormat.getNumberInstance(PT_BR).apply { maximumFractionDigits = 2 }.format(v)

private val CATEGORIA_LABEL = mapOf("SAFRA" to "Safra", "FROTA" to "Frota", "ADM" to "ADM")
private val CATEGORIAS_ORDEM = listOf("SAFRA", "FROTA", "ADM")

// Espelho local de STATUS_VARIANT (insumos-situacao-client.tsx/
// insumos-arvore-client.tsx no site) -- duplicado em vez de estender o
// dicionário genérico STATUS_TONE (StatusStyle.kt, usado por ~18 módulos)
// porque CRITICO/FALTA/EM_USO/EM_TRANSITO só existem neste painel, mesmo
// critério que o site já usa (esses componentes têm seu PRÓPRIO
// STATUS_VARIANT local, não o sistema genérico de badge por texto).
private enum class InsumoTone { GOOD, WARN, BAD, NEUTRAL }

private val INSUMO_STATUS_LABEL = mapOf(
    "OK" to "OK", "ATENCAO" to "Atenção", "CRITICO" to "Crítico",
    "FALTA" to "Falta", "EM_USO" to "Em uso", "EM_TRANSITO" to "Em trânsito",
)
private val INSUMO_STATUS_TONE = mapOf(
    "OK" to InsumoTone.GOOD, "ATENCAO" to InsumoTone.WARN, "CRITICO" to InsumoTone.BAD,
    "FALTA" to InsumoTone.BAD, "EM_USO" to InsumoTone.WARN, "EM_TRANSITO" to InsumoTone.NEUTRAL,
)

@Composable
private fun StatusPill(status: String) {
    val tone = INSUMO_STATUS_TONE[status] ?: InsumoTone.NEUTRAL
    val (bg, fg) = when (tone) {
        InsumoTone.GOOD -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) to MaterialTheme.colorScheme.primary
        InsumoTone.WARN -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        InsumoTone.BAD -> MaterialTheme.colorScheme.error.copy(alpha = 0.15f) to MaterialTheme.colorScheme.error
        InsumoTone.NEUTRAL -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        INSUMO_STATUS_LABEL[status] ?: status,
        style = MaterialTheme.typography.labelSmall,
        color = fg,
        modifier = Modifier
            .background(bg, RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

private val INSUMOS_EXPORT_COLUMNS = listOf(
    ColumnConfig(key = "item", label = "Item", type = "text"),
    ColumnConfig(key = "categoria", label = "Categoria", type = "text"),
    ColumnConfig(key = "unidade", label = "Un", type = "text"),
    ColumnConfig(key = "qtdPedida", label = "Pedida", type = "number"),
    ColumnConfig(key = "entregue", label = "Entregue", type = "number"),
    ColumnConfig(key = "aReceber", label = "A receber", type = "number"),
    ColumnConfig(key = "emEstoque", label = "Em estoque", type = "number"),
    ColumnConfig(key = "totalAplicado", label = "Aplicado", type = "number"),
    ColumnConfig(key = "percentualAplicado", label = "% Aplicado", type = "text"),
    ColumnConfig(key = "status", label = "Status", type = "text"),
)

private fun insumosExportConfig(): DomainConfig = DomainConfig(id = "controle-insumos", label = "Controle de Insumos", columns = INSUMOS_EXPORT_COLUMNS)

private fun insumosExportRecords(data: ControleInsumosResponse): List<Map<String, String?>> {
    val itens = CATEGORIAS_ORDEM.flatMap { data.situacaoConsolidada?.porCategoria?.get(it).orEmpty() }
    return itens.map {
        mapOf(
            "item" to it.item,
            "categoria" to (CATEGORIA_LABEL[it.categoria] ?: it.categoria),
            "unidade" to (it.unidade ?: ""),
            "qtdPedida" to it.qtdPedida.toString(),
            "entregue" to it.entregue.toString(),
            "aReceber" to it.aReceber.toString(),
            "emEstoque" to it.emEstoque.toString(),
            "totalAplicado" to it.totalAplicado.toString(),
            "percentualAplicado" to (it.percentualAplicado?.let { p -> "${fmtNum(p)}%" } ?: "—"),
            "status" to (INSUMO_STATUS_LABEL[it.status] ?: it.status),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InsumoItemRow(i: InsumoItemSituacaoData) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                i.item,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            StatusPill(i.status)
        }
        FlowRow(modifier = Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Pedida: ${fmtNum(i.qtdPedida)}${i.unidade?.let { " $it" } ?: ""}", style = MaterialTheme.typography.labelSmall)
            Text("Entregue: ${fmtNum(i.entregue)}", style = MaterialTheme.typography.labelSmall)
            Text("A receber: ${fmtNum(i.aReceber)}", style = MaterialTheme.typography.labelSmall)
            Text(
                "Em estoque: ${fmtNum(i.emEstoque)}",
                style = MaterialTheme.typography.labelSmall,
                color = if (i.emEstoque < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            Text("Aplicado: ${fmtNum(i.totalAplicado)}", style = MaterialTheme.typography.labelSmall)
            Text(
                "% Aplicado: ${i.percentualAplicado?.let { "${fmtNum(it)}%" } ?: "—"}",
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun CategoriaBlock(categoria: String, itens: List<InsumoItemSituacaoData>, aberto: Boolean, onToggle: () -> Unit) {
    val emFalta = itens.count { it.status == "FALTA" }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 8.dp, horizontal = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Icon(
                if (aberto) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (aberto) "Recolher" else "Expandir",
                modifier = Modifier.size(18.dp),
            )
            Text(
                CATEGORIA_LABEL[categoria] ?: categoria,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
            if (emFalta > 0) {
                StatusPill("FALTA")
                Spacer(modifier = Modifier.size(6.dp))
            }
            Text("${itens.size} item(ns)", style = MaterialTheme.typography.labelSmall)
        }
        if (aberto) {
            HorizontalDivider(modifier = Modifier.padding(top = 6.dp))
            Column(modifier = Modifier.padding(start = 22.dp)) {
                itens.forEachIndexed { idx, i ->
                    InsumoItemRow(i)
                    if (idx < itens.size - 1) HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun ItemCriticoCard(i: InsumoSaldoData, onPedidoRapido: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(i.item, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                StatusPill(i.status)
            }
            Row { Text("Saldo: ", style = MaterialTheme.typography.labelSmall); Text(fmtNum(i.saldo), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }
            Row { Text("Mínimo: ", style = MaterialTheme.typography.labelSmall); Text(fmtNum(i.minimo), style = MaterialTheme.typography.labelSmall) }
            Text(
                if (i.diasRestantes == null) "Sem consumo recente p/ estimar" else "Previsão de ruptura: ~${i.diasRestantes} dia(s)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onPedidoRapido, modifier = Modifier.padding(top = 4.dp)) {
                Icon(Icons.Filled.ShoppingCart, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.size(4.dp))
                Text("Pedido rápido", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun RamoNode(item: InsumosRamoItemData, depth: Int) {
    Row(modifier = Modifier.padding(start = (depth * 16).dp, top = 3.dp, bottom = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            item.label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (depth == 0) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (item.status != null && item.status != "OK") {
            StatusPill(item.status)
            Spacer(modifier = Modifier.size(6.dp))
        }
        Text(
            "${fmtNum(item.qtd)}${item.unidade?.let { " $it" } ?: ""}",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
    }
    item.filhos?.forEach { filho -> RamoNode(filho, depth + 1) }
}

private data class InsumosBlockSpec(val title: String, val content: @Composable () -> Unit)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun InsumosCategoryTabs(blocks: List<InsumosBlockSpec>, modifier: Modifier = Modifier) {
    var selected by remember { mutableStateOf(0) }
    val safeSelected = selected.coerceIn(0, blocks.size - 1)
    Column(modifier = modifier) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            blocks.forEachIndexed { index, block ->
                SegmentedButton(
                    selected = safeSelected == index,
                    onClick = { selected = index },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = blocks.size),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = MaterialTheme.colorScheme.primary,
                        activeContentColor = MaterialTheme.colorScheme.onPrimary,
                        inactiveContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    ),
                    label = { Text(block.title) },
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) { blocks[safeSelected].content() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControleInsumosScreen(onBack: () -> Unit, onPedidoRapido: (String) -> Unit, viewModel: ControleInsumosViewModel = viewModel()) {
    val data by viewModel.data
    val loading by viewModel.loading
    val offline by viewModel.offline
    val context = LocalContext.current
    var contentExpanded by remember { mutableStateOf(false) }
    var categoriaAberta by remember { mutableStateOf<String?>(null) }
    var controleInternoAberto by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Controle de Insumos", color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                        Row {
                            FarmSelectorButton()
                            IconButton(onClick = {
                                val msg = if (offline) NetworkStatus.failureMessage(context) else "Conectado -- dados sincronizados com o servidor."
                                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(if (offline) Icons.Filled.CloudOff else Icons.Filled.Cloud, contentDescription = "Nuvem", tint = MaterialTheme.colorScheme.primary)
                            }
                            if (data?.situacaoConsolidada != null) {
                                IconButton(onClick = { HtmlPrinter.printList(context, insumosExportConfig(), insumosExportRecords(data!!)) }) {
                                    Icon(Icons.Filled.Print, contentDescription = "Imprimir", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        val d = data
        val temDados = d?.situacaoConsolidada != null
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "insumos-icon-row") {
                val dadosBlock = InsumosBlockSpec("Dados") {
                    if (temDados) {
                        LabeledIconButton(
                            icon = if (contentExpanded) Icons.Filled.KeyboardDoubleArrowUp else Icons.Filled.KeyboardDoubleArrowDown,
                            label = if (contentExpanded) "Recolher" else "Expandir",
                            onClick = { contentExpanded = !contentExpanded },
                        )
                    }
                }
                val operacoesBlock = InsumosBlockSpec("Operações") {
                    LabeledIconButton(icon = Icons.Filled.Refresh, label = "Atualizar", loading = loading, onClick = { viewModel.refresh() })
                }
                val arquivosBlock = InsumosBlockSpec("Arquivos") {
                    if (temDados) {
                        LabeledIconButton(
                            icon = Icons.Filled.GridOn,
                            label = "Excel",
                            onClick = { exportXlsx(context, "controle-de-insumos", INSUMOS_EXPORT_COLUMNS, insumosExportRecords(d!!)) },
                        )
                        LabeledIconButton(
                            icon = Icons.Filled.PictureAsPdf,
                            label = "PDF",
                            onClick = { HtmlPrinter.exportPdfDirect(context, insumosExportConfig(), insumosExportRecords(d!!)) },
                        )
                    }
                }
                InsumosCategoryTabs(listOf(dadosBlock, operacoesBlock, arquivosBlock), modifier = Modifier.fillMaxWidth())
            }
            if (offline) {
                item { Text(NetworkStatus.failureMessage(context), style = MaterialTheme.typography.bodySmall) }
            }
            if (d == null) {
                item { Text(if (loading) "Carregando..." else "Sem dados ainda. Conecte-se à internet e atualize.") }
            } else if (contentExpanded) {
                val sit = d.situacaoConsolidada
                if (sit != null) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("Resumo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Row { Text("Itens únicos: "); Text(sit.itensUnicos.toString(), fontWeight = FontWeight.Bold) }
                                Row { Text("Total pedido: "); Text(fmtNum(sit.totalPedido), fontWeight = FontWeight.Bold) }
                                Row { Text("Total em estoque: "); Text(fmtNum(sit.totalEmEstoque), fontWeight = FontWeight.Bold) }
                                Row { Text("Total aplicado: "); Text(fmtNum(sit.totalAplicado), fontWeight = FontWeight.Bold) }
                                if (sit.itensAtencao > 0) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                        Text(" ${sit.itensAtencao} item(ns) em falta", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
                if (d.saldoTop10.isNotEmpty()) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Saldo por item (top 10)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(6.dp))
                                SimpleBarChart(
                                    categories = d.saldoTop10.map { it.item },
                                    series = listOf(BarSeries("Saldo", d.saldoTop10.map { it.saldo }, MaterialTheme.colorScheme.primary)),
                                    isMoney = false,
                                )
                            }
                        }
                    }
                }
                if (sit != null) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Situação consolidada por item", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    "Cruza Pedido → Entrega → Estoque → Aplicação por item. Toque numa categoria para expandir.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                val algumItem = CATEGORIAS_ORDEM.any { sit.porCategoria[it]?.isNotEmpty() == true }
                                if (!algumItem) {
                                    Text(
                                        "Nenhum item lançado ainda em Pedidos, Safra, Frota ou Controle Interno.",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(vertical = 8.dp),
                                    )
                                } else {
                                    CATEGORIAS_ORDEM.forEach { cat ->
                                        val itens = sit.porCategoria[cat].orEmpty()
                                        if (itens.isNotEmpty()) {
                                            CategoriaBlock(
                                                categoria = cat,
                                                itens = itens,
                                                aberto = categoriaAberta == cat,
                                                onToggle = { categoriaAberta = if (categoriaAberta == cat) null else cat },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (d.itensCriticos.isNotEmpty()) {
                    item {
                        Text(
                            "Itens críticos (${d.itensCriticos.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    items(d.itensCriticos, key = { it.item }) { i ->
                        ItemCriticoCard(i, onPedidoRapido = { onPedidoRapido(i.item) })
                    }
                }
                if (d.arvoreControleInterno.isNotEmpty()) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(onClick = { controleInternoAberto = !controleInternoAberto })
                                    .padding(12.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    Icon(
                                        if (controleInternoAberto) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Text(
                                        "Controle Interno (EPI, uniforme, ferramenta...)",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f).padding(start = 4.dp),
                                    )
                                }
                                if (controleInternoAberto) {
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                                    Column(modifier = Modifier.padding(start = 4.dp)) {
                                        d.arvoreControleInterno.forEach { ramo -> RamoNode(ramo, 0) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
