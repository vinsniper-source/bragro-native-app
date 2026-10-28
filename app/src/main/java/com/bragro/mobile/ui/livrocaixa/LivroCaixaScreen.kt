package com.bragro.mobile.ui.livrocaixa

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.MenuBook
import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.Box
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
import com.bragro.mobile.ui.theme.SearchableDropdownField
import com.bragro.mobile.ui.theme.appFieldColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.KeyboardDoubleArrowDown
import androidx.compose.material.icons.outlined.KeyboardDoubleArrowUp
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.ViewAgenda
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
import androidx.compose.material3.Surface
import com.bragro.mobile.ui.i18n.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.bragro.mobile.data.local.AppDatabase
import com.bragro.mobile.data.repo.LivroCaixaRepository
import com.bragro.mobile.data.repo.ProdutorRuralRepository
import com.bragro.mobile.ui.domain.EqualWidthBlockRow
import com.bragro.mobile.ui.domain.FarmSelectorButton
import com.bragro.mobile.ui.domain.LabeledIconButton
import com.bragro.mobile.ui.domain.RecordTableHeader
import com.bragro.mobile.ui.domain.RecordTableRow
import com.bragro.mobile.ui.domain.brDateToIso
import com.bragro.mobile.ui.domain.exportXlsx
import com.bragro.mobile.ui.domain.isoDateToBr
import com.bragro.mobile.ui.print.HtmlPrinter
import com.bragro.mobile.ui.util.shareTextFile
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

    // Exportação do .txt do LCDPR (pedido do usuário: "botão Exportar LCDPR
    // (.txt) adicionado na toolbar de Livro Caixa, ao lado de
    // Imprimir/Excel", réplica do site, Task #645). Mesma validação do
    // botão onExportarLcdpr() em livro-caixa-client.tsx: exige CPF ou CNPJ
    // do produtor rural preenchido antes de gerar o arquivo. Carrega a
    // config sob demanda se o card "Produtor Rural" ainda não tiver sido
    // aberto -- diferente do site (que já tem tudo carregado na página),
    // aqui o botão precisa funcionar mesmo que o usuário nunca tenha
    // aberto aquele card.
    fun exportarLcdpr(context: android.content.Context, onError: (String) -> Unit) {
        val data = resultado.value
        if (data == null || data.lancamentos.isEmpty()) {
            onError("Sem registros no período pra exportar.")
            return
        }
        viewModelScope.launch {
            var pr = produtorRural.value
            if (pr == null) {
                produtorRuralLoading.value = true
                pr = produtorRuralRepository.fetch()
                produtorRural.value = pr
                produtorRuralLoading.value = false
            }
            if (pr == null || (pr.cpfProdutorRural.isBlank() && pr.cnpj.isBlank())) {
                onError("Preencha o CPF do produtor rural no card \"Produtor Rural\" antes de exportar o LCDPR.")
                return@launch
            }
            val orgName = AppDatabase.get(getApplication()).sessionDao().get()?.orgName ?: "BRAgro"
            val declarante = LcdprDeclarante(
                nome = orgName,
                cpf = pr.cpfProdutorRural,
                cnpj = pr.cnpj,
                inscricaoEstadual = pr.inscricaoEstadualProdutor,
            )
            val conteudo = gerarLcdprTxt(declarante, data)
            shareTextFile(context, "LCDPR-${data.ano}.txt", "text/plain", conteudo)
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

// v34 (item 8): Ano, Conta e Imóvel são listas suspensas de toque único,
// sem digitação (campo somente leitura + lista ao tocar).
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun CampoListaSuspensa(label: String, valorTexto: String, opcoes: List<Pair<String, String>>, onSelect: (String) -> Unit) {
    var aberto by remember { mutableStateOf(false) }
    androidx.compose.material3.ExposedDropdownMenuBox(expanded = aberto, onExpandedChange = { aberto = it }) {
        androidx.compose.material3.OutlinedTextField(
            value = valorTexto,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            singleLine = true,
            trailingIcon = { androidx.compose.material3.ExposedDropdownMenuDefaults.TrailingIcon(expanded = aberto) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            colors = appFieldColors(),
        )
        androidx.compose.material3.DropdownMenu(expanded = aberto, onDismissRequest = { aberto = false }) {
            opcoes.forEach { (v, l) ->
                androidx.compose.material3.DropdownMenuItem(text = { Text(l) }, onClick = { onSelect(v); aberto = false })
            }
        }
    }
}

@Composable
private fun AnoDropdown(ano: Int, onSelect: (Int) -> Unit) {
    val anoAtual = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
    val anos = (anoAtual downTo anoAtual - 6).toList()
    CampoListaSuspensa(
        label = "Ano",
        valorTexto = ano.toString(),
        opcoes = remember(anos) { anos.map { it.toString() to it.toString() } },
    ) { picked -> picked.toIntOrNull()?.let(onSelect) }
}

@Composable
private fun BancoDropdown(banco: String?, opcoes: List<String>, onSelect: (String?) -> Unit) {
    CampoListaSuspensa(
        label = "Conta",
        valorTexto = banco ?: "Todas as contas",
        opcoes = remember(opcoes) { listOf("" to "Todas as contas") + opcoes.map { it to it } },
    ) { picked -> onSelect(picked.ifEmpty { null }) }
}

// Filtro de imóvel rural (COD_IMOVEL do LCDPR) -- pedido do usuário
// ("implemente tudo que falta ainda para o app native da plataforma"): já
// existia na tela web (livro-caixa-client.tsx), faltava no app.
@Composable
private fun ImovelDropdown(imovel: String?, opcoes: List<String>, onSelect: (String?) -> Unit) {
    CampoListaSuspensa(
        label = "Imóvel",
        valorTexto = imovel ?: "Todos os imóveis",
        opcoes = remember(opcoes) { listOf("" to "Todos os imóveis") + opcoes.map { it to it } },
    ) { picked -> onSelect(picked.ifEmpty { null }) }
}

// Tipo do certificado digital (A1 = arquivo, A3 = token/cartão) -- mesmas
// opções do card no site (produtor-rural-card.tsx).
@Composable
private fun CertificadoTipoDropdown(tipo: String, onSelect: (String) -> Unit) {
    val opcoes = remember { listOf("" to "Não informado", "A1" to "A1 (arquivo)", "A3" to "A3 (token/cartão)") }
    SearchableDropdownField(
        value = tipo,
        label = "Tipo",
        options = opcoes,
        onSelect = onSelect,
        emptyOptionLabel = null,
    )
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
            colors = appFieldColors(),
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun LivroCaixaCategoryTabs(blocks: List<LivroCaixaBlockSpec>, modifier: Modifier = Modifier) {
    var selected by remember { mutableStateOf(0) }
    val safeSelected = selected.coerceIn(0, blocks.size - 1)
    Column(modifier = modifier) {
        com.bragro.mobile.ui.domain.AbasEncaixadas(blocks.map { it.title }, safeSelected, onSelect = { selected = it })
        Spacer(modifier = Modifier.height(4.dp))
        // EqualWidthBlockRow (ui/domain/ModuleIconRow.kt) -- achado de
        // auditoria: células de mesma largura, borda vertical entre elas,
        // numa linha só (ellipsis se não couber), em vez do FlowRow antigo.
        EqualWidthBlockRow { blocks[safeSelected].content() }
    }
}

@Composable
private fun ExtratoBloco(nome: String, lancamentos: List<LivroCaixaLancamentoData>) {
    var aberto by androidx.compose.runtime.saveable.rememberSaveable(nome) { androidx.compose.runtime.mutableStateOf(false) }
    com.bragro.mobile.ui.theme.Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Column(modifier = Modifier.fillMaxWidth().clickable { aberto = !aberto }) {
                Text(nome, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    "${lancamentos.size} registro${if (lancamentos.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (aberto) lancamentos.forEach { LancamentoRow(it) }
        }
        com.bragro.mobile.ui.domain.CantoL(aberto, onClick = { aberto = !aberto })
    }
}

@Composable
private fun LancamentoRow(l: LivroCaixaLancamentoData) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(modifier = Modifier.weight(1f)) {
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
        // v37 -- a coluna dos valores recebe a largura que o número precisa
        // (wrapContentWidth), em vez de um peso fixo que espremia o texto.
        Column(modifier = Modifier.wrapContentWidth(), horizontalAlignment = androidx.compose.ui.Alignment.End) {
            if (l.entrada > 0) Text("+ ${formatMoneyBrl(l.entrada)}", style = MaterialTheme.typography.bodySmall, softWrap = false)
            if (l.saida > 0) Text("- ${formatMoneyBrl(l.saida)}", style = MaterialTheme.typography.bodySmall, softWrap = false)
            Text(formatMoneyBrl(l.saldo), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, softWrap = false)
        }
    }
    HorizontalDivider()
}

// Bloco individual por campo -- pedido do usuário (screenshot circulando o
// card inteiro Produtor Rural/IRPF, "são esses que preciso que modifique"):
// os campos (CNPJ, CPF, Inscrição Estadual, Emissor, Validade, Conta
// padrão) viviam soltos dentro do Card branco, sem nenhuma separação
// visual entre um e outro -- mesmo padrão de blocos individuais já usado
// em CotacaoMultiItemScreen.kt/PedidoMultiItemScreen.kt/
// FinanceiroItensInline.kt/ProviderIntegrationCard.kt/BaseDeDadosScreen.kt
// (scrim onSurface em vez de borda, respeitando a regra "sem bordas" do
// app; alpha 0.12f porque 0.05f já se mostrou imperceptível num display
// real nos outros arquivos).
@Composable
private fun ItemFieldBlock(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    com.bragro.mobile.ui.theme.StandardFieldBlock(modifier = modifier, content = content)
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
    // Certificado digital estruturado (tipo/emissor/validade) -- espelha a
    // mudança do site (produtor-rural-card.tsx): antes um único texto livre
    // (certificadoDigitalRef), agora com validade de verdade pra alertar
    // vencimento (mesmo padrão dos outros campos "AAAA-MM-DD" do app).
    var certTipo by remember(config) { mutableStateOf(config?.certificadoDigitalTipo ?: "") }
    var certEmissor by remember(config) { mutableStateOf(config?.certificadoDigitalEmissor ?: "") }
    // Editado/exibido em DD/MM/AAAA (padrão BR do resto do app) -- pedido do
    // usuário ("campo validade incorreto o correto é dd/mm/aaaa"): o servidor
    // continua guardando/recebendo AAAA-MM-DD (ver ProdutorRuralConfigData/
    // route.ts), só a tela mudou -- mesma conversão isoDateToBr/brDateToIso
    // (StatusStyle.kt) já usada nos outros campos de data do app.
    var certValidade by remember(config) { mutableStateOf(isoDateToBr(config?.certificadoDigitalValidade ?: "")) }
    var contaIrpf by remember(config) { mutableStateOf(config?.contaIrpfPadrao ?: "") }

    com.bragro.mobile.ui.theme.FormBlockCard(modifier = Modifier.fillMaxWidth()) {
        var aberto by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            com.bragro.mobile.ui.domain.TituloRecolhivel("Produtor Rural / IRPF", aberto) { aberto = !aberto }
            if (aberto) {
            Text(
                "Dados usados na apuração do Livro Caixa da Atividade Rural (LCDPR) -- confira com seu contador antes de declarar.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ItemFieldBlock(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(value = cnpj, onValueChange = { cnpj = it }, label = { Text("CNPJ") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = appFieldColors())
            }
            ItemFieldBlock(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(value = cpf, onValueChange = { cpf = it }, label = { Text("CPF do produtor") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = appFieldColors())
            }
            ItemFieldBlock(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(value = ie, onValueChange = { ie = it }, label = { Text("Inscrição Estadual") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = appFieldColors())
            }
            Text("Certificado digital (e-CAC / LCDPR)", style = MaterialTheme.typography.labelLarge)
            ItemFieldBlock(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CertificadoTipoDropdown(tipo = certTipo, onSelect = { certTipo = it })
                    OutlinedTextField(value = certEmissor, onValueChange = { certEmissor = it }, label = { Text("Emissor (AC)") }, placeholder = { Text("Ex.: Serasa, Certisign") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = appFieldColors())
                    OutlinedTextField(value = certValidade, onValueChange = { certValidade = it }, label = { Text("Validade (DD/MM/AAAA)") }, placeholder = { Text("DD/MM/AAAA") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = appFieldColors())
                }
            }
            Text(
                "Só metadado p/ controle de vencimento -- o arquivo .pfx/.p12 nunca é enviado aqui.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ItemFieldBlock(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(value = contaIrpf, onValueChange = { contaIrpf = it }, label = { Text("Conta padrão para IRPF") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = appFieldColors())
            }
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                androidx.compose.material3.Button(
                    enabled = !loading,
                    onClick = {
                        onSave(
                            ProdutorRuralConfigData(
                                cnpj = cnpj,
                                cpfProdutorRural = cpf,
                                inscricaoEstadualProdutor = ie,
                                certificadoDigitalTipo = certTipo,
                                certificadoDigitalEmissor = certEmissor,
                                // Volta pra AAAA-MM-DD antes de enviar -- o
                                // servidor guarda/espera ISO, só a tela edita
                                // em DD/MM/AAAA.
                                certificadoDigitalValidade = brDateToIso(certValidade),
                                contaIrpfPadrao = contaIrpf,
                            ),
                        )
                    },
                ) { Text(if (loading) "Salvando..." else "Salvar") }
            }
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
        var aberto by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
        Column(modifier = Modifier.padding(12.dp)) {
            com.bragro.mobile.ui.domain.TituloRecolhivel("Resumo por imóvel rural", aberto) { aberto = !aberto }
            if (aberto) {
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
                            Text("${i.quantidade} registro(s)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(formatMoneyBrl(i.saldo), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                    HorizontalDivider()
                }
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
        var aberto by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
        Column(modifier = Modifier.padding(12.dp)) {
            com.bragro.mobile.ui.domain.TituloRecolhivel("Resumo mensal", aberto) { aberto = !aberto }
            if (aberto) {
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
    var extratoAberto by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(true) }
    val context = LocalContext.current
    var filtrosOpen by remember { mutableStateOf(false) }
    var contentExpanded by remember { mutableStateOf(false) }
    // Tabela/Coluna -- mesmo botão único (achado de auditoria, pedido do
    // usuário: "coloque em todos os módulos"). Reaproveita a mesma
    // exportação (LIVRO_CAIXA_EXPORT_COLUMNS/livroCaixaExportRecords) já
    // usada em Excel/PDF/Imprimir; sem ações (Ver/Editar/Excluir) porque os
    // registros aqui vêm do razão computado (LancamentoRow também não
    // tem ação nenhuma hoje, ver mais abaixo).
    var tableView by remember { mutableStateOf(false) }
    val tableHScroll = remember { androidx.compose.foundation.ScrollState(0) }
    var produtorRuralOpen by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(produtorRuralOpen) {
        if (produtorRuralOpen && produtorRural == null) viewModel.loadProdutorRural()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        com.bragro.mobile.ui.domain.BackNavButton()
                    }
                },
                title = { androidx.compose.material3.ProvideTextStyle(androidx.compose.material3.LocalTextStyle.current.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)) {
                    // v21 -- ícone verde do módulo ao lado do título.
                    androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
                        com.bragro.mobile.ui.domain.IconeNegrito(androidx.compose.material.icons.Icons.Outlined.MenuBook, contentDescription = null, tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurface, size = 22.dp, modifier = androidx.compose.ui.Modifier.padding(end = 8.dp, bottom = 3.dp))

                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Livro Caixa", color = MaterialTheme.colorScheme.onSurface)
                    }
                
                    }
                } },
                actions = {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        com.bragro.mobile.ui.domain.HomeNavButton()
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
            // Painel de monitoramento (padrão da plataforma).
            if (data != null && data.lancamentos.isNotEmpty()) {
                item(key = "painel") {
                    val nEnt = data.lancamentos.count { it.entrada > 0 }
                    val nSai = data.lancamentos.count { it.saida > 0 }
                    com.bragro.mobile.ui.domain.PainelMonitoramento(
                        com.bragro.mobile.ui.domain.PainelModulo(
                            titulo = "Livro Caixa · $ano",
                            subtitulo = "${data.lancamentos.size} registro(s) · ${data.contas.size} conta(s)" + (banco?.let { " · $it" } ?: ""),
                            kpis = listOf(
                                com.bragro.mobile.ui.domain.PainelKpi("Entradas", data.totalEntradas, "moeda", null, "$nEnt registro(s)", "Extrato"),
                                com.bragro.mobile.ui.domain.PainelKpi("Saídas", data.totalSaidas, "moeda", null, "$nSai registro(s)", "Extrato"),
                                com.bragro.mobile.ui.domain.PainelKpi("Saldo do ano", data.saldoFinal, "moeda", null, "saldo inicial ${formatMoneyBrl(data.saldoInicial)}", "Extrato", tom = if (data.saldoFinal >= 0) "abaixo" else "acima"),
                                com.bragro.mobile.ui.domain.PainelKpi("Contas", data.contas.size.toDouble(), "numero", null, "bancos com movimento", "Resumo por conta"),
                            ),
                            barras = listOfNotNull(
                                data.totalEntradas.takeIf { it > 0 }?.let {
                                    com.bragro.mobile.ui.domain.PainelBarra("Saídas sobre as entradas", Math.round(data.totalSaidas / it * 1000) / 10.0, "${formatMoneyBrl(data.totalSaidas)} / ${formatMoneyBrl(it)}", neutra = true)
                                },
                            ),
                        ),
                    )
                }
            }
            item(key = "livro-caixa-icon-row") { com.bragro.mobile.ui.domain.BlocoComFaixa("Ações") { Column(modifier = Modifier.fillMaxWidth()) {
                val dadosBlock = LivroCaixaBlockSpec("Dados") {
                    LabeledIconButton(
                        icon = com.bragro.mobile.ui.domain.IconesVazados.FilterAlt,
                        label = "Filtros",
                        // Sem tint proprio -- herda onSurface (preto/branco),
                        // pedido do usuario ("tire o fundo verde de todos os
                        // blocos individuais").
                        onClick = { filtrosOpen = !filtrosOpen },
                    )
                    if (temRegistros) {
                        LabeledIconButton(
                            icon = com.bragro.mobile.ui.domain.IconesVazados.ViewList,
                            label = "Registros",
                            active = contentExpanded,
                            onClick = { contentExpanded = !contentExpanded },
                        )
                        LabeledIconButton(
                            icon = if (tableView) Icons.Outlined.ViewAgenda else com.bragro.mobile.ui.domain.IconesVazados.TableChart,
                            label = if (tableView) "Bloco" else "Tabela",
                            onClick = { tableView = !tableView },
                        )
                    }
                    // Produtor Rural / IRPF -- pedido do usuário ("implemente
                    // tudo que falta ainda para o app native da
                    // plataforma"): atalho pro card que faltava por completo.
                    LabeledIconButton(
                        icon = Icons.Outlined.Badge,
                        label = "Produtor Rural",
                        tint = if (produtorRuralOpen) MaterialTheme.colorScheme.primary else androidx.compose.material3.LocalContentColor.current,
                        onClick = { produtorRuralOpen = !produtorRuralOpen },
                    )
                }
                val operacoesBlock = LivroCaixaBlockSpec("Operações") {
                    LabeledIconButton(icon = Icons.Outlined.Refresh, label = "Atualizar", loading = loading, onClick = { viewModel.refresh() })
                    com.bragro.mobile.ui.domain.NuvemButton() // label = "Nuvem"
                }
                val arquivosBlock = LivroCaixaBlockSpec("Arquivos") {
                    LabeledIconButton(icon = com.bragro.mobile.ui.domain.IconesVazados.Print, label = "Imprimir", onClick = { if (resultado != null && resultado!!.lancamentos.isNotEmpty()) HtmlPrinter.printList(context, livroCaixaExportConfig(), livroCaixaExportRecords(resultado!!.lancamentos)) })
                    if (temRegistros) {
                        LabeledIconButton(icon = Icons.Outlined.GridOn, label = "Excel", onClick = { exportXlsx(context, "Livro Caixa", LIVRO_CAIXA_EXPORT_COLUMNS, livroCaixaExportRecords(data!!.lancamentos)) })
                        LabeledIconButton(icon = com.bragro.mobile.ui.domain.IconesVazados.PictureAsPdf, label = "PDF", onClick = { HtmlPrinter.exportPdfDirect(context, livroCaixaExportConfig(), livroCaixaExportRecords(data!!.lancamentos)) })
                        // Exportação LCDPR (.txt) -- pedido do usuário
                        // ("botão adicionado na toolbar de Livro Caixa, ao
                        // lado de Imprimir/Excel"), réplica do site (Task
                        // #645/lcdpr-txt.ts).
                        LabeledIconButton(
                            icon = Icons.Outlined.Description,
                            label = "LCDPR",
                            onClick = {
                                viewModel.exportarLcdpr(context) { erro ->
                                    android.widget.Toast.makeText(context, erro, android.widget.Toast.LENGTH_LONG).show()
                                }
                            },
                        )
                    }
                }
                LivroCaixaCategoryTabs(listOf(dadosBlock, operacoesBlock, arquivosBlock), modifier = Modifier.fillMaxWidth())
            } } }
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
            } else if (tableView) {
                item(key = "table-header") { RecordTableHeader(LIVRO_CAIXA_EXPORT_COLUMNS, tableHScroll, showActions = false) }
                items(livroCaixaExportRecords(data.lancamentos), key = { it.hashCode() }) { row ->
                    RecordTableRow(columns = LIVRO_CAIXA_EXPORT_COLUMNS, record = row, domainId = "", hScroll = tableHScroll)
                }
            } else if (contentExpanded) {
                // "Totais" saiu daqui -- já estão no Painel de monitoramento.
                if (data.contas.isNotEmpty()) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            var aberto by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                            Column(modifier = Modifier.padding(12.dp)) {
                                com.bragro.mobile.ui.domain.TituloRecolhivel("Resumo por conta", aberto) { aberto = !aberto }
                                if (aberto) {
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
                }
                if (data.imoveis.isNotEmpty()) {
                    item { ResumoPorImovelCard(data.imoveis) { imovelSelecionado -> viewModel.setImovel(imovelSelecionado) } }
                }
                if (data.porMes.any { it.entradas > 0 || it.saidas > 0 }) {
                    item { ResumoMensalCard(data.porMes) }
                }
                // v36 -- um bloco por extrato; cresce conforme os registros e abre/
                // recolhe pelo canto curvo na borda inferior direita.
                val gruposExtrato = data.lancamentos.groupBy { it.tipoDocumento?.takeIf { d -> d.isNotBlank() } ?: "Sem extrato" }
                gruposExtrato.forEach { (nomeExtrato, lista) ->
                    item(key = "extrato-$nomeExtrato") { ExtratoBloco(nomeExtrato, lista) }
                }
            }
        }
    }
}
