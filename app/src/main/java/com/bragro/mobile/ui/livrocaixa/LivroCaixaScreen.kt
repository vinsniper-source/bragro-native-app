package com.bragro.mobile.ui.livrocaixa

import android.app.Application
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Badge
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
import com.bragro.mobile.data.model.ProdutorRuralConfigData
import com.bragro.mobile.data.repo.LivroCaixaRepository
import com.bragro.mobile.data.repo.ProdutorRuralRepository
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
    private val produtorRuralRepository = ProdutorRuralRepository(app)

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
    // imovel/saldoInicial -- pedido do usuário ("implemente tudo que falta
    // ainda para o app native da plataforma"): os dois já existiam no site
    // (livro-caixa-client.tsx) mas faltavam por completo no app.
    var imovel = mutableStateOf<String?>(null)
        private set
    var saldoInicial = mutableStateOf(0.0)
        private set

    // Config Produtor Rural / IRPF -- sem cache local de propósito (ver
    // comentário em ProdutorRuralRepository.kt), busca sob demanda quando o
    // card é aberto.
    var produtorRural = mutableStateOf<ProdutorRuralConfigData?>(null)
        private set
    var produtorRuralLoading = mutableStateOf(false)
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

    fun setImovel(value: String?) {
        imovel.value = value
        refresh()
    }

    fun setSaldoInicial(value: Double) {
        saldoInicial.value = value
        refresh()
    }

    fun refresh() {
        if (loading.value) return
        loading.value = true
        viewModelScope.launch {
            val ok = repository.refresh(ano.value, saldoInicial.value, banco.value, imovel.value)
            offline.value = !ok
            loading.value = false
        }
    }

    fun loadProdutorRural() {
        if (produtorRuralLoading.value) return
        produtorRuralLoading.value = true
        viewModelScope.launch {
            produtorRural.value = produtorRuralRepository.fetch()
            produtorRuralLoading.value = false
        }
    }

    fun saveProdutorRural(config: ProdutorRuralConfigData, onDone: (Boolean) -> Unit) {
        produtorRuralLoading.value = true
        viewModelScope.launch {
            val result = produtorRuralRepository.save(config)
            if (result != null) produtorRural.value = result
            produtorRuralLoading.value = false
            onDone(result != null)
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

// Imóvel + Doc. adicionadas -- pedido do usuário ("implemente tudo que
// falta ainda para o app native da plataforma"): as 2 colunas já existiam
// na exportação/impressão web (livro-caixa-client.tsx) e nos dados que a
// rota mobile já mandava (tipoDocumento/banco já estavam no modelo, imovel
// era o único campo realmente ausente até agora).
private val LIVRO_CAIXA_EXPORT_COLUMNS = listOf(
    ColumnConfig(key = "data", label = "Data", type = "text"),
    ColumnConfig(key = "imovel", label = "Imóvel", type = "text"),
    ColumnConfig(key = "historico", label = "Histórico", type = "text"),
    ColumnConfig(key = "tipoDocumento", label = "Doc.", type = "text"),
    ColumnConfig(key = "entrada", label = "Entrada", type = "number", money = true),
    ColumnConfig(key = "saida", label = "Saída", type = "number", money = true),
    ColumnConfig(key = "saldo", label = "Saldo", type = "number", money = true),
)

private fun livroCaixaExportConfig(): DomainConfig = DomainConfig(id = "livrocaixa", label = "Livro Caixa", columns = LIVRO_CAIXA_EXPORT_COLUMNS)

private fun livroCaixaExportRecords(lancamentos: List<LivroCaixaLancamentoData>): List<Map<String, String?>> =
    lancamentos.map { l ->
        mapOf(
            "data" to formatDataBr(l.data),
            "imovel" to l.imovel,
            "historico" to l.historico,
            "tipoDocumento" to l.tipoDocumento,
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

// Filtro de imóvel rural (COD_IMOVEL do LCDPR) -- pedido do usuário
// ("implemente tudo que falta ainda para o app native da plataforma"): já
// existia na tela web (livro-caixa-client.tsx), faltava no app.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImovelDropdown(imovel: String?, opcoes: List<String>, onSelect: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = imovel ?: "Todos os imóveis",
            onValueChange = {},
            readOnly = true,
            label = { Text("Imóvel") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("Todos os imóveis") }, onClick = { onSelect(null); expanded = false })
            for (opt in opcoes) {
                DropdownMenuItem(text = { Text(opt) }, onClick = { onSelect(opt); expanded = false })
            }
        }
    }
}

// Saldo inicial editável -- pedido do usuário ("implemente tudo que falta
// ainda para o app native da plataforma"): antes fixo em 0 no app (ver
// comentário removido do ViewModel), já existia como campo editável na tela
// web (input + botão "Aplicar", livro-caixa-client.tsx).
@Composable
private fun SaldoInicialField(saldoInicial: Double, onApply: (Double) -> Unit) {
    var texto by remember(saldoInicial) { mutableStateOf(if (saldoInicial != 0.0) saldoInicial.toString() else "") }
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = texto,
            onValueChange = { texto = it },
            label = { Text("Saldo inicial (R$)") },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
            modifier = Modifier.weight(1f),
        )
        androidx.compose.material3.TextButton(onClick = {
            val normalizado = texto.trim().replace(",", ".")
            onApply(normalizado.toDoubleOrNull() ?: 0.0)
        }) { Text("Aplicar") }
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
            // Imóvel + Doc. -- pedido do usuário ("implemente tudo que falta
            // ainda para o app native da plataforma"): colunas que a tela
            // web mostra (livro-caixa-client.tsx) e o app não mostrava.
            val detalhe = listOfNotNull(
                l.imovel.takeIf { it.isNotBlank() },
                l.tipoDocumento?.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            if (detalhe.isNotBlank()) {
                Text(detalhe, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(modifier = Modifier.weight(1f), horizontalAlignment = androidx.compose.ui.Alignment.End) {
            if (l.entrada > 0) Text("+ ${formatMoneyBrl(l.entrada)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            if (l.saida > 0) Text("- ${formatMoneyBrl(l.saida)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            Text(formatMoneyBrl(l.saldo), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
        }
    }
    HorizontalDivider()
}

// Card Produtor Rural / IRPF -- pedido do usuário ("implemente tudo que
// falta ainda para o app native da plataforma"): 0% presente no app antes
// (nem rota, nem tela); já existia no site (produtor-rural-card.tsx),
// preenchendo Organization.cnpj/cpfProdutorRural/inscricaoEstadualProdutor/
// certificadoDigitalRef/contaIrpfPadrao. Mesmos campos, mesma validação
// leve (CPF/CNPJ só dígitos, resto texto livre) -- a validação "de verdade"
// (14/11 dígitos) é refeita no servidor (route.ts), aqui só formata a
// entrada.
@Composable
private fun ProdutorRuralCard(
    config: ProdutorRuralConfigData?,
    loading: Boolean,
    onSave: (ProdutorRuralConfigData) -> Unit,
) {
    var cnpj by remember(config) { mutableStateOf(config?.cnpj ?: "") }
    var cpf by remember(config) { mutableStateOf(config?.cpfProdutorRural ?: "") }
    var ie by remember(config) { mutableStateOf(config?.inscricaoEstadualProdutor ?: "") }
    var certificado by remember(config) { mutableStateOf(config?.certificadoDigitalRef ?: "") }
    var contaIrpf by remember(config) { mutableStateOf(config?.contaIrpfPadrao ?: "") }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Produtor Rural / IRPF", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Dados usados na apuração do Livro Caixa da Atividade Rural (LCDPR) -- confira com seu contador antes de declarar.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(value = cnpj, onValueChange = { cnpj = it }, label = { Text("CNPJ") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = cpf, onValueChange = { cpf = it }, label = { Text("CPF do produtor") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = ie, onValueChange = { ie = it }, label = { Text("Inscrição Estadual") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = certificado, onValueChange = { certificado = it }, label = { Text("Referência do certificado digital") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = contaIrpf, onValueChange = { contaIrpf = it }, label = { Text("Conta padrão para IRPF") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                androidx.compose.material3.Button(
                    enabled = !loading,
                    onClick = {
                        onSave(
                            ProdutorRuralConfigData(
                                cnpj = cnpj,
                                cpfProdutorRural = cpf,
                                inscricaoEstadualProdutor = ie,
                                certificadoDigitalRef = certificado,
                                contaIrpfPadrao = contaIrpf,
                            ),
                        )
                    },
                ) { Text(if (loading) "Salvando..." else "Salvar") }
            }
        }
    }
}

// Resumo por imóvel -- pedido do usuário ("implemente tudo que falta ainda
// para o app native da plataforma"): mesma tabela clicável (filtra ao
// tocar) que já existia na tela web, ausente no app.
@Composable
private fun ResumoPorImovelCard(imoveis: List<com.bragro.mobile.data.model.LivroCaixaImovelResumoData>, onSelect: (String) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Resumo por imóvel rural", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Column(modifier = Modifier.padding(top = 6.dp)) {
                imoveis.forEach { i ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(i.imovel) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(i.imovel, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                            Text("${i.quantidade} lançamento(s)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(formatMoneyBrl(i.saldo), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

// Resumo mensal -- pedido do usuário ("implemente tudo que falta ainda para
// o app native da plataforma"): tabela (mês, entradas, saídas, saldo
// acumulado) que já existia na web (`porMes`, já vinha na resposta da rota
// mobile, mas nunca era exibida na tela).
@Composable
private fun ResumoMensalCard(porMes: List<com.bragro.mobile.data.model.LivroCaixaMesData>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Resumo mensal", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Column(modifier = Modifier.padding(top = 6.dp)) {
                porMes.forEach { m ->
                    if (m.entradas > 0 || m.saidas > 0) {
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                            Text(m.label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                                Text(formatMoneyBrl(m.saldoFinal), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LivroCaixaScreen(onBack: () -> Unit, viewModel: LivroCaixaViewModel = viewModel()) {
    val resultado by viewModel.resultado
    val loading by viewModel.loading
    val offline by viewModel.offline
    val ano by viewModel.ano
    val banco by viewModel.banco
    // imovel/saldoInicial/produtorRural -- pedido do usuário ("implemente
    // tudo que falta ainda para o app native da plataforma").
    val imovel by viewModel.imovel
    val saldoInicialAtual by viewModel.saldoInicial
    val produtorRural by viewModel.produtorRural
    val produtorRuralLoading by viewModel.produtorRuralLoading
    val context = LocalContext.current
    var filtrosOpen by remember { mutableStateOf(false) }
    var contentExpanded by remember { mutableStateOf(false) }
    var produtorRuralOpen by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(produtorRuralOpen) {
        if (produtorRuralOpen && produtorRural == null) viewModel.loadProdutorRural()
    }

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
                    // Produtor Rural / IRPF -- pedido do usuário ("implemente
                    // tudo que falta ainda para o app native da
                    // plataforma"): atalho pro card que faltava por completo.
                    LabeledIconButton(
                        icon = Icons.Filled.Badge,
                        label = "Produtor Rural",
                        tint = if (produtorRuralOpen) MaterialTheme.colorScheme.primary else androidx.compose.material3.LocalContentColor.current,
                        onClick = { produtorRuralOpen = !produtorRuralOpen },
                    )
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
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(modifier = Modifier.weight(1f)) { AnoDropdown(ano) { viewModel.setAno(it) } }
                            Column(modifier = Modifier.weight(1f)) {
                                BancoDropdown(banco, data?.contas?.map { it.banco }.orEmpty()) { viewModel.setBanco(it) }
                            }
                        }
                        // Imóvel + saldo inicial -- pedido do usuário
                        // ("implemente tudo que falta ainda para o app
                        // native da plataforma"): já existiam na tela web.
                        ImovelDropdown(imovel, data?.imoveis?.map { it.imovel }.orEmpty()) { viewModel.setImovel(it) }
                        SaldoInicialField(saldoInicialAtual) { viewModel.setSaldoInicial(it) }
                    }
                }
            }
            if (produtorRuralOpen) {
                item {
                    ProdutorRuralCard(produtorRural, produtorRuralLoading) { config -> viewModel.saveProdutorRural(config) {} }
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
                                        // Clicável (filtra por essa conta) +
                                        // quantidade/entradas/saídas + badge
                                        // "Conta IRPF" -- pedido do usuário
                                        // ("implemente tudo que falta ainda
                                        // para o app native da plataforma"):
                                        // a tela web já mostrava tudo isso
                                        // (livro-caixa-client.tsx), o app só
                                        // mostrava banco+saldo.
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { viewModel.setBanco(c.banco) }
                                                .padding(vertical = 4.dp),
                                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                                    Text(c.banco, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                                    val pr = produtorRural
                                                    if (pr != null && c.banco == pr.contaIrpfPadrao && pr.contaIrpfPadrao.isNotBlank()) {
                                                        Text(
                                                            " · Conta IRPF",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.primary,
                                                            fontWeight = FontWeight.Bold,
                                                        )
                                                    }
                                                }
                                                Text(
                                                    "${c.quantidade} lanç. · +${formatMoneyBrl(c.entradas)} / -${formatMoneyBrl(c.saidas)}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                            Text(formatMoneyBrl(c.saldo), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                        }
                                        HorizontalDivider()
                                    }
                                }
                            }
                        }
                    }
                }
                if (data.imoveis.isNotEmpty()) {
                    item { ResumoPorImovelCard(data.imoveis) { imovelSelecionado -> viewModel.setImovel(imovelSelecionado) } }
                }
                if (data.porMes.any { it.entradas > 0 || it.saidas > 0 }) {
                    item { ResumoMensalCard(data.porMes) }
                }
                item {
                    Text("Extrato (${data.lancamentos.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                }
                items(data.lancamentos, key = { it.id }) { l -> LancamentoRow(l) }
            }
        }
    }
}
