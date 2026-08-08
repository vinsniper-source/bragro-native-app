package com.bragro.mobile.ui.dre

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.KeyboardDoubleArrowDown
import androidx.compose.material.icons.filled.KeyboardDoubleArrowUp
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TableChart
import com.bragro.mobile.ui.theme.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bragro.mobile.data.model.ColumnConfig
import com.bragro.mobile.data.model.DomainConfig
import com.bragro.mobile.data.model.DreCategoriaData
import com.bragro.mobile.data.model.DreData
import com.bragro.mobile.data.model.DreFazendaData
import com.bragro.mobile.data.model.DreRamoItemData
import com.bragro.mobile.data.repo.DreRepository
import com.bragro.mobile.ui.domain.exportCsv
import com.bragro.mobile.ui.print.HtmlPrinter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

// Fase 2 do app nativo (Task #32/#33/#34): DRE consolidado por fazenda +
// arvore de custos (Financeiro por categoria, Frota por maquina, Safra
// por talhao/item, ao expandir o card de cada fazenda) + composicao de
// custo por categoria (barra horizontal, sem biblioteca de graficos),
// espelhando src/app/(app)/dre/dre-client.tsx no site -- via
// /api/mobile/dre, que reaproveita as MESMAS getDreConsolidado()/
// getDreArvoresPorFazendas()/getDreComposicaoPorCategoria() do site (mesmo
// motor de rateio/custo por hectare/saca).
class DreViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = DreRepository(app)

    var dre = mutableStateOf<DreData?>(null)
        private set
    var loading = mutableStateOf(false)
        private set
    var offline = mutableStateOf(false)
        private set
    var safra = mutableStateOf<String?>(null)
        private set
    var cultura = mutableStateOf<String?>(null)
        private set
    // Fase 2 (Task #33): quais fazendas estao com a arvore de custos aberta
    // -- guardado no ViewModel (nao no Composable) pra sobreviver a
    // recomposicao/rotacao de tela.
    var expandedFarmIds = mutableStateOf<Set<String>>(emptySet())
        private set

    fun toggleFarm(farmId: String) {
        expandedFarmIds.value = if (farmId in expandedFarmIds.value) expandedFarmIds.value - farmId else expandedFarmIds.value + farmId
    }

    // Ícone "recolher/expandir todos" no bloco Dados (mesmo padrão dos
    // outros módulos) -- atua em todos os cards de fazenda de uma vez, em
    // vez de precisar abrir um por um.
    fun expandAllFarms(farmIds: Set<String>) {
        expandedFarmIds.value = farmIds
    }

    fun collapseAllFarms() {
        expandedFarmIds.value = emptySet()
    }

    init {
        viewModelScope.launch {
            repository.observeCached().collectLatest { entity ->
                if (entity != null) dre.value = repository.parse(entity)
            }
        }
        refresh()
    }

    fun setSafra(value: String?) {
        safra.value = value
        refresh()
    }

    fun setCultura(value: String?) {
        cultura.value = value
        refresh()
    }

    fun refresh() {
        if (loading.value) return
        loading.value = true
        viewModelScope.launch {
            val ok = repository.refresh(safra.value, cultura.value)
            offline.value = !ok
            loading.value = false
        }
    }
}

private fun formatMoneyBrl(value: Double): String =
    NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(value)

// Exportação CSV/PDF/Imprimir do DRE -- pedido do usuário ("construir
// CSV/PDF/Imprimir também" pra DRE/Análises). DRE já tem uma lista plana
// por fazenda (porFazenda), então em vez de escrever exportação nova do
// zero, montamos um DomainConfig/ColumnConfig sintético (não vem de
// nenhum registro/backend, só serve pra reaproveitar exportCsv/
// HtmlPrinter -- mesma infra usada nos 16 módulos genéricos).
private val DRE_EXPORT_COLUMNS = listOf(
    ColumnConfig(key = "farmName", label = "Fazenda", type = "text"),
    ColumnConfig(key = "areaHa", label = "Área (ha)", type = "number"),
    ColumnConfig(key = "custoTotal", label = "Custo Total", type = "number", money = true),
    ColumnConfig(key = "custoPorHa", label = "Custo/ha", type = "number", money = true),
    ColumnConfig(key = "custoPorSc", label = "Custo/sc", type = "number", money = true),
    ColumnConfig(key = "receitaTotal", label = "Receita Total", type = "number", money = true),
    ColumnConfig(key = "margem", label = "Margem", type = "number", money = true),
    ColumnConfig(key = "margemPorHa", label = "Margem/ha", type = "number", money = true),
    ColumnConfig(key = "totalSacas", label = "Sacas", type = "number"),
)

private fun dreExportConfig(): DomainConfig = DomainConfig(id = "dre", label = "DRE", columns = DRE_EXPORT_COLUMNS)

private fun dreExportRecords(dre: DreData): List<Map<String, String?>> {
    val linhas = dre.porFazenda.map { f ->
        mapOf(
            "farmName" to f.farmName,
            "areaHa" to f.areaHa.toString(),
            "custoTotal" to f.custoTotal.toString(),
            "custoPorHa" to f.custoPorHa.toString(),
            "custoPorSc" to f.custoPorSc?.toString(),
            "receitaTotal" to f.receitaTotal.toString(),
            "margem" to f.margem.toString(),
            "margemPorHa" to f.margemPorHa.toString(),
            "totalSacas" to f.totalSacas?.toString(),
        )
    }
    val totalizacao = mapOf(
        "farmName" to "Total",
        "areaHa" to dre.totais.areaHa.toString(),
        "custoTotal" to dre.totais.custoTotal.toString(),
        "custoPorHa" to dre.totais.custoPorHa.toString(),
        "custoPorSc" to dre.totais.custoPorSc?.toString(),
        "receitaTotal" to dre.totais.receitaTotal.toString(),
        "margem" to dre.totais.margem.toString(),
        "margemPorHa" to null,
        "totalSacas" to dre.totais.totalSacas.toString(),
    )
    return linhas + totalizacao
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterDropdown(label: String, value: String?, options: List<String>, onSelect: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = value ?: "Todos",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("Todos") }, onClick = { onSelect(null); expanded = false })
            for (opt in options) {
                DropdownMenuItem(text = { Text(opt) }, onClick = { onSelect(opt); expanded = false })
            }
        }
    }
}

// Fase 2 (Task #33): um no da arvore de custos (Financeiro/Frota/Safra),
// renderizado recursivamente com identacao por profundidade -- mesma
// estrutura em arvore de DreRamoItem (dre.ts), so que aqui achatada em
// linhas indentadas em vez do componente de arvore expansivel da pagina
// web (mais simples de navegar com o dedo numa tela pequena).
@Composable
private fun DreTreeNode(item: DreRamoItemData, depth: Int) {
    Row(modifier = Modifier.padding(start = (depth * 16).dp, top = 2.dp, bottom = 2.dp)) {
        Text(
            item.label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (depth == 0) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        Text(formatMoneyBrl(item.valor), style = MaterialTheme.typography.bodySmall)
    }
    item.filhos?.forEach { filho -> DreTreeNode(filho, depth + 1) }
}

// Fase 2 (Task #34): "grafico" de composicao de custo por categoria (todas
// as fazendas juntas) -- barra horizontal proporcional ao maior valor da
// lista em vez de um grafico de pizza (Compose nao tem biblioteca de
// graficos embutida; uma dependencia so pra isso nao valeria a pena agora).
// Mesmos dados de getDreComposicaoPorCategoria (dre.ts), ja ordenados do
// maior pro menor pelo backend.
@Composable
private fun CategoriaBarRow(item: DreCategoriaData, maxValor: Double) {
    val fracao = if (maxValor > 0) (item.valor / maxValor).toFloat().coerceIn(0f, 1f) else 0f
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(item.categoria, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            Text(formatMoneyBrl(item.valor), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .padding(top = 3.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fracao)
                    .height(8.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)),
            )
        }
    }
}

// Espelho de ModuleBlockSpec/ModuleCategoryBlock (DomainListScreen.kt) --
// mesmo padrão de bloco com título + Card, duplicado aqui em vez de
// compartilhado pra não arriscar mexer nos módulos que já estão
// funcionando (mesma decisão já tomada nos outros arquivos).
private data class DreBlockSpec(
    val title: String,
    val vertical: Boolean,
    val content: @Composable () -> Unit,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DreCategoryBlock(spec: DreBlockSpec, modifier: Modifier = Modifier, fillHeight: Boolean = false) {
    Column(modifier = modifier) {
        Text(
            spec.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
        )
        Card(modifier = Modifier.fillMaxWidth().let { if (fillHeight) it.weight(1f) else it }) {
            if (spec.vertical) {
                Column(
                    modifier = Modifier.fillMaxWidth().fillMaxHeight().padding(8.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) { spec.content() }
            } else {
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) { spec.content() }
            }
        }
    }
}

@Composable
private fun FarmCard(f: DreFazendaData, arvore: List<DreRamoItemData>, expanded: Boolean, onToggle: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(f.farmName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("${f.areaHa} ha", style = MaterialTheme.typography.bodySmall)
                }
                if (arvore.isNotEmpty()) {
                    IconButton(onClick = onToggle, modifier = Modifier.size(24.dp)) {
                        Icon(
                            if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = if (expanded) "Recolher detalhamento de custos" else "Ver detalhamento de custos",
                        )
                    }
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
            Row { Text("Custo total: "); Text(formatMoneyBrl(f.custoTotal), fontWeight = FontWeight.Bold) }
            Row { Text("Custo/ha: "); Text(formatMoneyBrl(f.custoPorHa), fontWeight = FontWeight.Bold) }
            if (f.custoPorSc != null) {
                Row { Text("Custo/sc: "); Text(formatMoneyBrl(f.custoPorSc), fontWeight = FontWeight.Bold) }
            }
            Row { Text("Receita total: "); Text(formatMoneyBrl(f.receitaTotal), fontWeight = FontWeight.Bold) }
            Row { Text("Margem: "); Text(formatMoneyBrl(f.margem), fontWeight = FontWeight.Bold) }
            if (expanded && arvore.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                Text("Detalhamento de custos", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Column(modifier = Modifier.padding(top = 4.dp)) {
                    arvore.forEach { ramo -> DreTreeNode(ramo, 0) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DreScreen(onBack: () -> Unit, viewModel: DreViewModel = viewModel()) {
    val dre by viewModel.dre
    val loading by viewModel.loading
    val offline by viewModel.offline
    val safra by viewModel.safra
    val cultura by viewModel.cultura
    val expandedFarmIds by viewModel.expandedFarmIds
    val context = LocalContext.current
    // Filtros Safra/Cultura viram ícone (Filtro), mesmo padrão dos outros
    // módulos -- pedido do usuário ("dre, análises... transforme os
    // filtros em ícone").
    var filtrosOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("DRE")
                    }
                },
                navigationIcon = {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                        }
                    }
                },
            )
        },
    ) { padding ->
        val data = dre
        val allFarmIds = data?.porFazenda?.map { it.farmId }?.toSet().orEmpty()
        val allFarmsExpanded = allFarmIds.isNotEmpty() && expandedFarmIds.containsAll(allFarmIds)
        val temRegistros = data != null && data.porFazenda.isNotEmpty()
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "dre-icon-row") {
                val dadosBlock = DreBlockSpec("Dados", vertical = false) {
                    IconButton(onClick = { filtrosOpen = !filtrosOpen }) {
                        Icon(
                            Icons.Filled.FilterAlt,
                            contentDescription = "Filtros",
                            tint = if (filtrosOpen) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                        )
                    }
                    if (allFarmIds.isNotEmpty()) {
                        IconButton(onClick = {
                            if (allFarmsExpanded) viewModel.collapseAllFarms() else viewModel.expandAllFarms(allFarmIds)
                        }) {
                            Icon(
                                if (allFarmsExpanded) Icons.Filled.KeyboardDoubleArrowUp else Icons.Filled.KeyboardDoubleArrowDown,
                                contentDescription = if (allFarmsExpanded) "Recolher todas as fazendas" else "Expandir todas as fazendas",
                            )
                        }
                    }
                }
                val registrosBlock = DreBlockSpec("", vertical = false) {
                    IconButton(onClick = {
                        val msg = if (offline) "Sem conexão -- mostrando o último resultado salvo neste aparelho." else "Conectado -- dados sincronizados com o servidor."
                        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(if (offline) Icons.Filled.CloudOff else Icons.Filled.Cloud, contentDescription = "Armazenamento")
                    }
                }
                val operacoesBlock = DreBlockSpec("Operações", vertical = false) {
                    IconButton(onClick = { viewModel.refresh() }) {
                        if (loading) CircularProgressIndicator(modifier = Modifier.padding(4.dp).size(20.dp))
                        else Icon(Icons.Filled.Refresh, contentDescription = "Atualizar")
                    }
                }
                val arquivosBlock = DreBlockSpec("Arquivos", vertical = false) {
                    if (temRegistros) {
                        IconButton(onClick = { exportCsv(context, "DRE", DRE_EXPORT_COLUMNS, dreExportRecords(data!!)) }) {
                            Icon(Icons.Filled.TableChart, contentDescription = "Exportar CSV")
                        }
                        IconButton(onClick = { HtmlPrinter.exportPdfDirect(context, dreExportConfig(), dreExportRecords(data!!)) }) {
                            Icon(Icons.Filled.PictureAsPdf, contentDescription = "Exportar PDF")
                        }
                    }
                }
                val distribuicaoBlock = DreBlockSpec("", vertical = true) {
                    if (temRegistros) {
                        IconButton(onClick = { HtmlPrinter.printList(context, dreExportConfig(), dreExportRecords(data!!)) }) {
                            Icon(Icons.Filled.Print, contentDescription = "Imprimir")
                        }
                    }
                }
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DreCategoryBlock(dadosBlock, modifier = Modifier.weight(3f).fillMaxHeight(), fillHeight = true)
                        DreCategoryBlock(registrosBlock, modifier = Modifier.weight(1f).fillMaxHeight(), fillHeight = true)
                    }
                    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DreCategoryBlock(operacoesBlock, modifier = Modifier.weight(2f).fillMaxHeight(), fillHeight = true)
                        DreCategoryBlock(arquivosBlock, modifier = Modifier.weight(2f).fillMaxHeight(), fillHeight = true)
                        DreCategoryBlock(distribuicaoBlock, modifier = Modifier.weight(1f).fillMaxHeight(), fillHeight = true)
                    }
                }
            }
            if (filtrosOpen) {
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            FilterDropdown("Safra", safra, data?.safrasDisponiveis.orEmpty()) { viewModel.setSafra(it) }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            FilterDropdown("Cultura", cultura, data?.culturasDisponiveis.orEmpty()) { viewModel.setCultura(it) }
                        }
                    }
                }
            }
            if (offline) {
                item {
                    Text(
                        "Sem conexão -- mostrando o último resultado salvo neste aparelho.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (data == null) {
                item {
                    Text(if (loading) "Carregando..." else "Sem dados ainda. Conecte-se à internet e atualize.")
                }
            } else {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Totais", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Row { Text("Área total: "); Text("${data.totais.areaHa} ha", fontWeight = FontWeight.Bold) }
                            Row { Text("Custo total: "); Text(formatMoneyBrl(data.totais.custoTotal), fontWeight = FontWeight.Bold) }
                            Row { Text("Custo/ha: "); Text(formatMoneyBrl(data.totais.custoPorHa), fontWeight = FontWeight.Bold) }
                            if (data.totais.custoPorSc != null) {
                                Row { Text("Custo/sc: "); Text(formatMoneyBrl(data.totais.custoPorSc), fontWeight = FontWeight.Bold) }
                            }
                            Row { Text("Receita total: "); Text(formatMoneyBrl(data.totais.receitaTotal), fontWeight = FontWeight.Bold) }
                            Row { Text("Margem: "); Text(formatMoneyBrl(data.totais.margem), fontWeight = FontWeight.Bold) }
                        }
                    }
                }
                if (data.composicaoPorCategoria.isNotEmpty()) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Composição de custo por categoria", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                val maxValor = data.composicaoPorCategoria.maxOf { it.valor }
                                Column(modifier = Modifier.padding(top = 6.dp)) {
                                    data.composicaoPorCategoria.forEach { cat -> CategoriaBarRow(cat, maxValor) }
                                }
                            }
                        }
                    }
                }
                items(data.porFazenda, key = { it.farmId }) { f ->
                    FarmCard(
                        f = f,
                        arvore = data.arvores[f.farmId].orEmpty(),
                        expanded = f.farmId in expandedFarmIds,
                        onToggle = { viewModel.toggleFarm(f.farmId) },
                    )
                }
            }
        }
    }
}
