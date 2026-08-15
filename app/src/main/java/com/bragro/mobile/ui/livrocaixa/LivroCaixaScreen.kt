package com.bragro.mobile.ui.livrocaixa

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.bragro.mobile.ui.theme.Card
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.KeyboardDoubleArrowDown
import androidx.compose.material.icons.filled.KeyboardDoubleArrowUp
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.bragro.mobile.data.model.LivroCaixaData
import com.bragro.mobile.data.model.LivroCaixaLancamentoData
import com.bragro.mobile.data.repo.LivroCaixaRepository
import com.bragro.mobile.ui.domain.FarmSelectorButton
import com.bragro.mobile.ui.domain.LabeledIconButton
import com.bragro.mobile.ui.domain.exportXlsx
import com.bragro.mobile.ui.print.HtmlPrinter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

// Livro Caixa do Produtor Rural pro app nativo (Task #58 -- o modulo ja
// existia no site, ver src/app/(app)/livro-caixa/, e ficou faltando aqui).
// Espelha livro-caixa-client.tsx: Totais do ano, resumo por conta (campo
// "banco" do lancamento de Financeiro) e o extrato cronologico (Data,
// Historico, Entrada, Saida, Saldo) -- tudo via /api/mobile/livro-caixa, que
// reaproveita a MESMA getLivroCaixaData() (regra de regime de caixa,
// entrada/saida) que a pagina web usa. Mesmo padrao geral de DreScreen.kt
// (blob JSON cacheado offline, barra oval Dados/Operacoes/Arquivos, export
// Excel/PDF/Imprimir reaproveitando a infra generica dos 16 modulos).
class LivroCaixaViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = LivroCaixaRepository(app)

    var resultado = mutableStateOf<LivroCaixaData?>(null)
        private set
    var loading = mutableStateOf(false)
        private set
    var offline = mutableStateOf(false)
        private set
    var ano = mutableStateOf(java.util.Calendar.getInstance().get(java.util.Calendar.YEAR))
        private set
    var banco = mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch {
            repository.observeCached().collectLatest { entity ->
                if (entity != null) resultado.value = repository.parse(entity)
            }
        }
        refresh()
    }

    fun setAno(value: Int) {
        ano.value = value
        refresh()
    }

    fun setBanco(value: String?) {
        banco.value = value
        refresh()
    }

    fun refresh() {
        if (loading.value) return
        loading.value = true
        viewModelScope.launch {
            // Saldo inicial fica sempre 0 aqui (mesmo padrao default do
            // site) -- o usuario que precisar de um saldo de abertura
            // diferente ajusta na tela web, que tem esse campo explicito.
            val ok = repository.refresh(ano.value, 0.0, banco.value)
            offline.value = !ok
            loading.value = false
        }
    }
}

private fun formatMoneyBrl(value: Double): String =
    NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(value)

private fun formatDataBr(iso: String): String = try {
    val d = java.time.OffsetDateTime.parse(iso)
    "%02d/%02d/%04d".format(d.dayOfMonth, d.monthValue, d.year)
} catch (e: Exception) {
    iso.take(10)
}

private val LIVRO_CAIXA_EXPORT_COLUMNS = listOf(
    ColumnConfig(key = "data", label = "Data", type = "text"),
    ColumnConfig(key = "historico", label = "Histórico", type = "text"),
    ColumnConfig(key = "entrada", label = "Entrada", type = "number", money = true),
    ColumnConfig(key = "saida", label = "Saída", type = "number", money = true),
    ColumnConfig(key = "saldo", label = "Saldo", type = "number", money = true),
)

private fun livroCaixaExportConfig(): DomainConfig = DomainConfig(id = "livrocaixa", label = "Livro Caixa", columns = LIVRO_CAIXA_EXPORT_COLUMNS)

private fun livroCaixaExportRecords(lancamentos: List<LivroCaixaLancamentoData>): List<Map<String, String?>> =
    lancamentos.map { l ->
        mapOf(
            "data" to formatDataBr(l.data),
            "historico" to l.historico,
            "entrada" to (if (l.entrada > 0) l.entrada.toString() else null),
            "saida" to (if (l.saida > 0) l.saida.toString() else null),
            "saldo" to l.saldo.toString(),
        )
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnoDropdown(ano: Int, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val anoAtual = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
    val anos = (anoAtual downTo anoAtual - 6).toList()
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = ano.toString(),
            onValueChange = {},
            readOnly = true,
            label = { Text("Ano") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for (a in anos) {
                DropdownMenuItem(text = { Text(a.toString()) }, onClick = { onSelect(a); expanded = false })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BancoDropdown(banco: String?, opcoes: List<String>, onSelect: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = banco ?: "Todas as contas",
            onValueChange = {},
            readOnly = true,
            label = { Text("Conta") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("Todas as contas") }, onClick = { onSelect(null); expanded = false })
            for (opt in opcoes) {
                DropdownMenuItem(text = { Text(opt) }, onClick = { onSelect(opt); expanded = false })
            }
        }
    }
}

// Mesmo padrão de bloco/barra oval de DreScreen.kt (DreBlockSpec/
// DreCategoryTabs) -- duplicado aqui em vez de compartilhado, mesmo
// critério já usado entre os módulos (evitar risco de mexer em código
// usado por telas que já funcionam).
private data class LivroCaixaBlockSpec(val title: String, val content: @Composable () -> Unit)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun LivroCaixaCategoryTabs(blocks: List<LivroCaixaBlockSpec>, modifier: Modifier = Modifier) {
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

@Composable
private fun LancamentoRow(l: LivroCaixaLancamentoData) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.weight(2f)) {
            Text(formatDataBr(l.data), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            Text(l.historico, style = MaterialTheme.typography.bodySmall)
        }
        Column(modifier = Modifier.weight(1f), horizontalAlignment = androidx.compose.ui.Alignment.End) {
            if (l.entrada > 0) Text("+ ${formatMoneyBrl(l.entrada)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            if (l.saida > 0) Text("- ${formatMoneyBrl(l.saida)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            Text(formatMoneyBrl(l.saldo), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
        }
    }
    HorizontalDivider()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LivroCaixaScreen(onBack: () -> Unit, viewModel: LivroCaixaViewModel = viewModel()) {
    val resultado by viewModel.resultado
    val loading by viewModel.loading
    val offline by viewModel.offline
    val ano by viewModel.ano
    val banco by viewModel.banco
    val context = LocalContext.current
    var filtrosOpen by remember { mutableStateOf(false) }
    var contentExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Livro Caixa", color = MaterialTheme.colorScheme.primary)
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
                            if (resultado != null && resultado!!.lancamentos.isNotEmpty()) {
                                IconButton(onClick = { HtmlPrinter.printList(context, livroCaixaExportConfig(), livroCaixaExportRecords(resultado!!.lancamentos)) }) {
                                    Icon(Icons.Filled.Print, contentDescription = "Imprimir", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        val data = resultado
        val temRegistros = data != null && data.lancamentos.isNotEmpty()
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "livro-caixa-icon-row") {
                val dadosBlock = LivroCaixaBlockSpec("Dados") {
                    LabeledIconButton(
                        icon = Icons.Filled.FilterAlt,
                        label = "Filtros",
                        tint = if (filtrosOpen) MaterialTheme.colorScheme.primary else androidx.compose.material3.LocalContentColor.current,
                        onClick = { filtrosOpen = !filtrosOpen },
                    )
                    if (temRegistros) {
                        LabeledIconButton(
                            icon = if (contentExpanded) Icons.Filled.KeyboardDoubleArrowUp else Icons.Filled.KeyboardDoubleArrowDown,
                            label = if (contentExpanded) "Recolher" else "Expandir",
                            onClick = { contentExpanded = !contentExpanded },
                        )
                    }
                }
                val operacoesBlock = LivroCaixaBlockSpec("Operações") {
                    LabeledIconButton(icon = Icons.Filled.Refresh, label = "Atualizar", loading = loading, onClick = { viewModel.refresh() })
                }
                val arquivosBlock = LivroCaixaBlockSpec("Arquivos") {
                    if (temRegistros) {
                        LabeledIconButton(icon = Icons.Filled.GridOn, label = "Excel", onClick = { exportXlsx(context, "Livro Caixa", LIVRO_CAIXA_EXPORT_COLUMNS, livroCaixaExportRecords(data!!.lancamentos)) })
                        LabeledIconButton(icon = Icons.Filled.PictureAsPdf, label = "PDF", onClick = { HtmlPrinter.exportPdfDirect(context, livroCaixaExportConfig(), livroCaixaExportRecords(data!!.lancamentos)) })
                    }
                }
                LivroCaixaCategoryTabs(listOf(dadosBlock, operacoesBlock, arquivosBlock), modifier = Modifier.fillMaxWidth())
            }
            if (filtrosOpen) {
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(modifier = Modifier.weight(1f)) { AnoDropdown(ano) { viewModel.setAno(it) } }
                        Column(modifier = Modifier.weight(1f)) {
                            BancoDropdown(banco, data?.contas?.map { it.banco }.orEmpty()) { viewModel.setBanco(it) }
                        }
                    }
                }
            }
            if (offline) {
                item { Text(NetworkStatus.failureMessage(context), style = MaterialTheme.typography.bodySmall) }
            }
            if (data == null) {
                item { Text(if (loading) "Carregando..." else "Sem dados ainda. Conecte-se à internet e atualize.") }
            } else if (contentExpanded) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Totais de $ano", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Row { Text("Entradas: "); Text(formatMoneyBrl(data.totalEntradas), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
                            Row { Text("Saídas: "); Text(formatMoneyBrl(data.totalSaidas), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) }
                            Row { Text("Saldo final: "); Text(formatMoneyBrl(data.saldoFinal), fontWeight = FontWeight.Bold) }
                        }
                    }
                }
                if (data.contas.isNotEmpty()) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Resumo por conta", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Column(modifier = Modifier.padding(top = 6.dp)) {
                                    data.contas.forEach { c ->
                                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                                            Text(c.banco, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                            Text(formatMoneyBrl(c.saldo), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    Text("Extrato (${data.lancamentos.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                }
                items(data.lancamentos, key = { it.id }) { l -> LancamentoRow(l) }
            }
        }
    }
}
