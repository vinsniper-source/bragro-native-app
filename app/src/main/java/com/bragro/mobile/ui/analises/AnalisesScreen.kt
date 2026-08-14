package com.bragro.mobile.ui.analises

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.KeyboardDoubleArrowDown
import androidx.compose.material.icons.filled.KeyboardDoubleArrowUp
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.GridOn
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bragro.mobile.data.NetworkStatus
import com.bragro.mobile.data.model.ColumnConfig
import com.bragro.mobile.data.model.DomainConfig
import com.bragro.mobile.data.repo.AnalisesRepository
import com.bragro.mobile.ui.domain.FarmSelectorButton
import com.bragro.mobile.ui.domain.LabeledIconButton
import com.bragro.mobile.ui.domain.exportXlsx
import com.bragro.mobile.ui.print.HtmlPrinter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// Fase 2 do app nativo (Task #36): Analises cruzadas entre modulos,
// espelhando src/app/(app)/analises/analises-client.tsx no site (15
// cruzamentos: Planejado x Realizado x Pago, Custo/ha por fonte, Pedido x
// Recebimento, Consumo de Estoque, Clima x Produtividade, Pragas x
// Produtividade, Folha x Custo, Eficiencia de maquina etc.) -- via
// /api/mobile/analises, que reaproveita a MESMA getAnalisesCruzadas() do
// site. Renderizacao GENERICA (cada chave do JSON vira uma secao de
// cards com os campos brutos) em vez de modelar 15 formatos de linha
// diferentes em Kotlin -- mesmo principio do motor generico de
// lista/formulario ja usado nos 16 modulos (DomainListScreen/
// DomainFormScreen, guiados por DomainConfig em vez de 16 telas escritas
// a mao).
class AnalisesViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = AnalisesRepository(app)

    var analises = mutableStateOf<JsonObject?>(null)
        private set
    var safrasDisponiveis = mutableStateOf<List<String>>(emptyList())
        private set
    // Filtro de Cultura ao lado do de Safra -- pedido do usuário ("análises
    // coloque filtro cultura, dividindo a mesma linha com o filtro safra"),
    // mesmo padrão já usado no DRE (DreScreen.kt).
    var culturasDisponiveis = mutableStateOf<List<String>>(emptyList())
        private set
    var loading = mutableStateOf(false)
        private set
    var offline = mutableStateOf(false)
        private set
    var safra = mutableStateOf<String?>(null)
        private set
    var cultura = mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch {
            repository.observeCached().collectLatest { entity ->
                if (entity != null) {
                    analises.value = repository.parse(entity)
                    safrasDisponiveis.value = repository.safras(entity)
                    culturasDisponiveis.value = repository.culturas(entity)
                }
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

/** Rotulo de secao mais legivel: "planejadoVsRealizado" -> "Planejado Vs
 * Realizado". So cosmetico -- nao muda a chave usada para nada alem de
 * exibicao. */
private fun tituloSecao(chave: String): String {
    val comEspacos = chave.replace(Regex("([a-z0-9])([A-Z])"), "$1 $2")
    return comEspacos.replaceFirstChar { it.uppercase() }
}

private fun valorParaTexto(el: JsonElement): String = when (el) {
    is JsonNull -> "—"
    is JsonArray -> "${el.size} item(ns)"
    is JsonObject -> "${el.size} campo(s)"
    is JsonPrimitive -> el.content.ifBlank { "—" }
    else -> "—"
}

// Exportação CSV/PDF/Imprimir -- pedido do usuário ("construir CSV/PDF/
// Imprimir também" pra DRE/Análises). Análises não tem uma tabela única
// (é um JSON genérico com 15 cruzamentos diferentes, cada um com campos
// próprios -- ver comentário da AnalisesViewModel), então em vez de
// modelar 15 formatos de exportação diferentes, a exportação também é
// GENÉRICA: uma tabela "longa" (Seção / Item / Campo / Valor), uma linha
// por campo de cada card já mostrado na tela (mesmo texto de
// valorParaTexto usado no ObjetoCard) -- mesmo princípio da renderização.
private val ANALISES_EXPORT_COLUMNS = listOf(
    ColumnConfig(key = "secao", label = "Seção", type = "text"),
    ColumnConfig(key = "item", label = "Item", type = "text"),
    ColumnConfig(key = "campo", label = "Campo", type = "text"),
    ColumnConfig(key = "valor", label = "Valor", type = "text"),
)

private fun analisesExportConfig(): DomainConfig = DomainConfig(id = "analises", label = "Análises", columns = ANALISES_EXPORT_COLUMNS)

private fun analisesExportRecords(data: JsonObject): List<Map<String, String?>> {
    val linhas = mutableListOf<Map<String, String?>>()
    data.entries.forEach { (chave, valor) ->
        val secao = tituloSecao(chave)
        when (valor) {
            is JsonArray -> valor.forEachIndexed { indice, item ->
                if (item is JsonObject) {
                    item.entries.forEach { (campo, v) ->
                        linhas.add(mapOf("secao" to secao, "item" to (indice + 1).toString(), "campo" to campo, "valor" to valorParaTexto(v)))
                    }
                }
            }
            is JsonObject -> valor.entries.forEach { (campo, v) ->
                linhas.add(mapOf("secao" to secao, "item" to "", "campo" to campo, "valor" to valorParaTexto(v)))
            }
            else -> linhas.add(mapOf("secao" to secao, "item" to "", "campo" to "", "valor" to valorParaTexto(valor)))
        }
    }
    return linhas
}

// Espelho de ModuleBlockSpec/ModuleCategoryBlock (DomainListScreen.kt) --
// mesmo padrão de bloco com título + Card, duplicado aqui em vez de
// compartilhado pra não arriscar mexer nos módulos que já estão
// funcionando (mesma decisão já tomada nos outros arquivos).
private data class AnalisesBlockSpec(
    val title: String,
    val vertical: Boolean,
    val content: @Composable () -> Unit,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AnalisesCategoryBlock(spec: AnalisesBlockSpec, modifier: Modifier = Modifier, fillHeight: Boolean = false) {
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

// Barra oval (pill) alternando entre categorias (Dados/Operações/Arquivos),
// mostrando só os ícones da categoria selecionada -- mesmo padrão de
// ModuleCategoryTabs (DomainListScreen.kt)/DreCategoryTabs (DreScreen.kt),
// duplicado aqui pelo mesmo motivo de AnalisesCategoryBlock (evitar mexer
// em código compartilhado entre os módulos).
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AnalisesCategoryTabs(blocks: List<AnalisesBlockSpec>, modifier: Modifier = Modifier) {
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
        val active = blocks[safeSelected]
        if (active.vertical) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { active.content() }
        } else {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) { active.content() }
        }
    }
}

@Composable
private fun ObjetoCard(obj: JsonObject) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            obj.entries.forEach { (campo, valor) ->
                Row {
                    Text("$campo: ", style = MaterialTheme.typography.bodySmall)
                    Text(valorParaTexto(valor), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun SecaoAnalise(chave: String, valor: JsonElement) {
    Column(modifier = Modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(tituloSecao(chave), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        when (valor) {
            is JsonArray -> {
                if (valor.isEmpty()) {
                    Text("Sem dados.", style = MaterialTheme.typography.bodySmall)
                } else {
                    valor.forEach { item ->
                        if (item is JsonObject) ObjetoCard(item)
                    }
                }
            }
            is JsonObject -> ObjetoCard(valor)
            else -> Text(valorParaTexto(valor), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterDropdown(label: String, value: String?, options: List<String>, onSelect: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = value ?: "Todas",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("Todas") }, onClick = { onSelect(null); expanded = false })
            for (opt in options) {
                DropdownMenuItem(text = { Text(opt) }, onClick = { onSelect(opt); expanded = false })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalisesScreen(onBack: () -> Unit, viewModel: AnalisesViewModel = viewModel()) {
    val analises by viewModel.analises
    val safrasDisponiveis by viewModel.safrasDisponiveis
    val culturasDisponiveis by viewModel.culturasDisponiveis
    val loading by viewModel.loading
    val offline by viewModel.offline
    val safra by viewModel.safra
    val cultura by viewModel.cultura
    val context = LocalContext.current
    // Filtros Safra/Cultura viram ícone (Filtro), mesmo padrão dos outros
    // módulos -- pedido do usuário ("dre, análises... transforme os
    // filtros em ícone").
    var filtrosOpen by remember { mutableStateOf(false) }
    // Recolher/expandir as seções de análise de uma vez -- pedido do usuário
    // ("insira o ícone recolher/expandir os blocos em dre e análises"),
    // mesmo padrão do allExpanded já usado nas listas de lançamentos
    // (DomainListScreen/FinanceiroScreen) e agora também no DRE. Começa
    // FECHADO -- pedido do usuário ("sempre aparecer a tela vazia, só
    // expandir quando clicar no ícone").
    var contentExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Análises", color = MaterialTheme.colorScheme.primary)
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
                // Nuvem/Imprimir no canto superior direito -- mesmo padrão
                // já aplicado em todos os outros módulos (inclusive DRE
                // acima), pedido do usuário ("aplique o mesmo padrão... da
                // aba lançamentos").
                actions = {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row {
                            // Fazendas/Nuvem/Imprimir, nessa ordem -- pedido
                            // do usuário ("em todos os módulos... na ordem
                            // fazendas, nuvem e imprimir").
                            FarmSelectorButton()
                            IconButton(onClick = {
                                val msg = if (offline) NetworkStatus.failureMessage(context) else "Conectado -- dados sincronizados com o servidor."
                                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(if (offline) Icons.Filled.CloudOff else Icons.Filled.Cloud, contentDescription = "Nuvem", tint = MaterialTheme.colorScheme.primary)
                            }
                            if (analises != null && analises!!.entries.isNotEmpty()) {
                                IconButton(onClick = { HtmlPrinter.printList(context, analisesExportConfig(), analisesExportRecords(analises!!)) }) {
                                    Icon(Icons.Filled.Print, contentDescription = "Imprimir", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        val data = analises
        val temRegistros = data != null && data.entries.isNotEmpty()
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item(key = "analises-icon-row") {
                val dadosBlock = AnalisesBlockSpec("Dados", vertical = false) {
                    LabeledIconButton(
                        icon = Icons.Filled.FilterAlt,
                        label = "Filtros",
                        tint = if (filtrosOpen) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                        onClick = { filtrosOpen = !filtrosOpen },
                    )
                    // Ícone de recolher/expandir movido pra dentro de Dados,
                    // junto do Filtro -- mesmo ajuste do DRE (DreScreen.kt),
                    // pedido do usuário ("no módulo análises aplique
                    // exatamente o que aplicou em dre").
                    if (temRegistros) {
                        LabeledIconButton(
                            icon = if (contentExpanded) Icons.Filled.KeyboardDoubleArrowUp else Icons.Filled.KeyboardDoubleArrowDown,
                            label = if (contentExpanded) "Recolher" else "Expandir",
                            onClick = { contentExpanded = !contentExpanded },
                        )
                    }
                }
                val operacoesBlock = AnalisesBlockSpec("Operações", vertical = false) {
                    LabeledIconButton(
                        icon = Icons.Filled.Refresh,
                        label = "Atualizar",
                        loading = loading,
                        onClick = { viewModel.refresh() },
                    )
                }
                val arquivosBlock = AnalisesBlockSpec("Arquivos", vertical = false) {
                    if (temRegistros) {
                        LabeledIconButton(
                            icon = Icons.Filled.GridOn,
                            label = "Excel",
                            onClick = { exportXlsx(context, "Análises", ANALISES_EXPORT_COLUMNS, analisesExportRecords(data!!)) },
                        )
                        LabeledIconButton(
                            icon = Icons.Filled.PictureAsPdf,
                            label = "PDF",
                            onClick = { HtmlPrinter.exportPdfDirect(context, analisesExportConfig(), analisesExportRecords(data!!)) },
                        )
                    }
                }
                // Nuvem/Imprimir saíram daqui -- foram promovidos pro canto
                // superior direito da TopAppBar (ver Scaffold acima), mesmo
                // padrão já usado em todos os outros módulos (inclusive
                // DRE). Dados/Operações/Arquivos viraram uma barra oval
                // (pill) que alterna entre categorias -- pedido do usuário
                // ("aplique o mesmo padrão... da aba lançamentos com a
                // barra oval e as categorias os ícones no topo").
                AnalisesCategoryTabs(listOf(dadosBlock, operacoesBlock, arquivosBlock), modifier = Modifier.fillMaxWidth())
            }
            if (filtrosOpen) {
                item {
                    // Cultura ao lado de Safra, mesma linha, blocos separados --
                    // pedido do usuário ("análises coloque filtro cultura,
                    // dividindo a mesma linha com o filtro safra, blocos
                    // separados"), mesmo padrão do DRE (DreScreen.kt).
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            FilterDropdown("Safra", safra, safrasDisponiveis) { viewModel.setSafra(it) }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            FilterDropdown("Cultura", cultura, culturasDisponiveis) { viewModel.setCultura(it) }
                        }
                    }
                }
            }
            if (offline) {
                item {
                    Text(
                        NetworkStatus.failureMessage(context),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (data == null) {
                item {
                    Text(if (loading) "Carregando..." else "Sem dados ainda. Conecte-se à internet e atualize.")
                }
            } else if (contentExpanded) {
                data.entries.forEachIndexed { index, (chave, valor) ->
                    item(key = chave) { SecaoAnalise(chave, valor) }
                    if (index < data.entries.size - 1) {
                        item { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp)) }
                    }
                }
            }
        }
    }
}
