package com.bragro.mobile.ui.estoque

import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.foundation.clickable
import android.app.Application
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import com.bragro.mobile.ui.i18n.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bragro.mobile.data.NetworkStatus
import com.bragro.mobile.data.model.ReconciliacaoEstoqueItemData
import com.bragro.mobile.data.repo.ReconciliacaoEstoqueRepository
import com.bragro.mobile.ui.print.HtmlPrinter
import com.bragro.mobile.ui.theme.Card
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

/** "Reconciliação Físico x Fiscal" (Task #603/#607, paridade com
 * reconciliacao-estoque-client.tsx do site): relatório de LEITURA pura,
 * sem input do usuário -- busca em /api/mobile/reconciliacao-estoque
 * (mesma função de serviço do site, nenhum cálculo refeito aqui) e mostra
 * por item o saldo fiscal (só notas fiscais importadas), o saldo físico
 * (todos os lançamentos) e a diferença entre os dois, com um toggle pra
 * ver só os itens divergentes ou a lista completa -- mesmo critério do
 * site. Sem cache no Room de propósito (ver ReconciliacaoEstoqueRepository).
 */
class ReconciliacaoEstoqueViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = ReconciliacaoEstoqueRepository(app)

    var carregando = mutableStateOf(true)
        private set
    var erro = mutableStateOf<String?>(null)
        private set
    var itens = mutableStateOf<List<ReconciliacaoEstoqueItemData>>(emptyList())
        private set
    var totalItensComDivergencia = mutableStateOf(0)
        private set
    // Ícone Nuvem no cabeçalho (mesmo padrão de DomainListScreen/Prescrição)
    // -- true quando o último fetch falhou.
    var offline = mutableStateOf(false)
        private set

    init { carregar() }

    fun carregar() {
        carregando.value = true
        erro.value = null
        viewModelScope.launch {
            val resultado = repository.fetch()
            carregando.value = false
            offline.value = resultado == null
            if (resultado == null) {
                erro.value = "Sem conexão -- não foi possível carregar a reconciliação agora."
                return@launch
            }
            itens.value = resultado.itens
            totalItensComDivergencia.value = resultado.totalItensComDivergencia
        }
    }
}

private val TOLERANCIA = 0.01

// Saldos aqui são QUANTIDADES de estoque (litros, kg, unidades -- ver
// ReconciliacaoEstoqueItemData.unidade), não valores monetários -- por
// isso número puro (2 casas), nunca "R$", mesmo critério do site.
private fun formatoNumero(valor: Double): String {
    val nf = NumberFormat.getNumberInstance(Locale("pt", "BR"))
    nf.minimumFractionDigits = 2
    nf.maximumFractionDigits = 2
    return nf.format(valor)
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ReconciliacaoEstoqueScreen(onBack: () -> Unit, viewModel: ReconciliacaoEstoqueViewModel = viewModel()) {
    val context = LocalContext.current
    val carregando by viewModel.carregando
    val erro by viewModel.erro
    val itens by viewModel.itens
    val totalItensComDivergencia by viewModel.totalItensComDivergencia
    val offline by viewModel.offline
    var soDivergencia by remember { mutableStateOf(true) }
    var fCategoria by remember { mutableStateOf<String?>(null) }
    var catMenu by remember { mutableStateOf(false) }
    var listaAberta by remember { mutableStateOf(false) }
    val imprimir: () -> Unit = {
        val headers = listOf("Item", "Categoria", "Saldo Fiscal", "Saldo Real", "Diferença")
        val rows = itens.map {
            listOf(
                it.item, it.categoria ?: "",
                "${formatoNumero(it.saldoFiscal)}${it.unidade?.let { u -> " $u" } ?: ""}",
                "${formatoNumero(it.saldoFisico)}${it.unidade?.let { u -> " $u" } ?: ""}",
                "${formatoNumero(it.diferenca)}${it.unidade?.let { u -> " $u" } ?: ""}",
            )
        }
        HtmlPrinter.printSimpleTable(context, "Reconciliação Físico x Fiscal", headers, rows)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                // Título desce uma linha, mesmo ajuste já aplicado nos
                // demais módulos -- pedido do usuário ("abaixe o título do
                // módulo e deixe na mesma altura dos modulos mais antigos").
                title = {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Reconciliação Físico x Fiscal", maxLines = 1, overflow = TextOverflow.Clip, color = MaterialTheme.colorScheme.primary, modifier = Modifier.basicMarquee())
                    }
                },
                // Ícones Imprimir + Nuvem na mesma altura do título -- pedido
                // do usuário, mesmo padrão dos demais módulos.
                actions = {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row {
                            IconButton(onClick = {
                                val msg = if (offline) NetworkStatus.failureMessage(context) else "Conectado -- dados sincronizados com o servidor."
                                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(
                                    if (offline) Icons.Filled.CloudOff else Icons.Filled.Cloud,
                                    contentDescription = "Nuvem",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        com.bragro.mobile.ui.domain.HomeNavButton()
                    }
                },
            )
        },
    ) { padding ->
        if (carregando) {
            Column(modifier = Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        if (erro != null) {
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(erro ?: "", style = MaterialTheme.typography.bodyMedium)
            }
            return@Scaffold
        }

        val divergentes = itens.filter { kotlin.math.abs(it.diferenca) > TOLERANCIA }
        val visiveis = (if (soDivergencia) divergentes else itens).filter { fCategoria == null || it.categoria == fCategoria }
        val categorias = itens.mapNotNull { it.categoria?.takeIf { c -> c.isNotBlank() } }.distinct().sorted()
        val maior = divergentes.maxByOrNull { kotlin.math.abs(it.diferenca) }
        fun contaOrigem(o: String) = itens.count { i -> i.origensNaoFiscais.any { it.origem.uppercase().contains(o) } }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "painel") {
                val conferidos = itens.size - divergentes.size
                com.bragro.mobile.ui.domain.PainelMonitoramento(
                    com.bragro.mobile.ui.domain.PainelModulo(
                        titulo = "Reconciliação físico x fiscal",
                        subtitulo = "${itens.size} itens com movimento · ${divergentes.size} com diferença",
                        kpis = listOf(
                            com.bragro.mobile.ui.domain.PainelKpi("Com movimento", itens.size.toDouble(), "numero", null, "todas as origens", "Todos"),
                            com.bragro.mobile.ui.domain.PainelKpi("Com diferença", divergentes.size.toDouble(), "numero", null, "sem nota por trás", "Divergências", tom = if (divergentes.isNotEmpty()) "acima" else null),
                            com.bragro.mobile.ui.domain.PainelKpi("Maior diferença", maior?.let { kotlin.math.abs(it.diferenca) } ?: 0.0, "numero", maior?.unidade, maior?.item ?: "nenhuma", "Divergências"),
                            com.bragro.mobile.ui.domain.PainelKpi("Sem nota (manual)", contaOrigem("MANUAL").toDouble(), "numero", null, "origem manual", "Todos"),
                            com.bragro.mobile.ui.domain.PainelKpi("Ajustes", contaOrigem("AJUSTE").toDouble(), "numero", null, "origem ajuste", "Todos"),
                        ),
                        barras = if (itens.isEmpty()) emptyList() else listOf(
                            com.bragro.mobile.ui.domain.PainelBarra("Conferidos", Math.round(conferidos * 1000.0 / itens.size) / 10.0, "$conferidos/${itens.size}"),
                        ),
                    ),
                    onKpi = { k -> soDivergencia = k.destino == "Divergências"; listaAberta = true },
                )
            }
            item(key = "acoes") {
                com.bragro.mobile.ui.domain.AcoesEmAbas(
                    dados = {
                        com.bragro.mobile.ui.domain.LabeledIconButton(icon = Icons.Filled.Warning, label = "Divergências", active = soDivergencia, onClick = { soDivergencia = true; listaAberta = true })
                        com.bragro.mobile.ui.domain.LabeledIconButton(icon = Icons.Filled.ViewList, label = "Todos", active = !soDivergencia, onClick = { soDivergencia = false; listaAberta = true })
                        androidx.compose.foundation.layout.Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
                            com.bragro.mobile.ui.domain.LabeledIconButton(icon = Icons.Filled.FilterAlt, label = fCategoria ?: "Categoria", active = fCategoria != null, onClick = { catMenu = true })
                            androidx.compose.material3.DropdownMenu(expanded = catMenu, onDismissRequest = { catMenu = false }) {
                                androidx.compose.material3.DropdownMenuItem(text = { Text("Todas") }, onClick = { fCategoria = null; catMenu = false })
                                categorias.forEach { c ->
                                    androidx.compose.material3.DropdownMenuItem(text = { Text(c) }, onClick = { fCategoria = c; catMenu = false; listaAberta = true })
                                }
                            }
                        }
                    },
                    operacoes = {
                        com.bragro.mobile.ui.domain.LabeledIconButton(icon = Icons.Filled.Refresh, label = "Atualizar", onClick = { viewModel.carregar() })
                    },
                    arquivos = {
                        com.bragro.mobile.ui.domain.ArquivosTabela("Reconciliação Físico x Fiscal", listOf("Item", "Categoria", "Saldo Fiscal", "Saldo Real", "Diferença")) {
                            itens.map { listOf(it.item, it.categoria ?: "", formatoNumero(it.saldoFiscal) + (it.unidade?.let { u -> " $u" } ?: ""), formatoNumero(it.saldoFisico) + (it.unidade?.let { u -> " $u" } ?: ""), formatoNumero(it.diferenca) + (it.unidade?.let { u -> " $u" } ?: "")) }
                        }
                    },
                )
            }
            item(key = "lista-header") {
                com.bragro.mobile.ui.domain.CabecalhoLancamentos(
                    total = visiveis.size,
                    aberto = listaAberta,
                    titulo = if (soDivergencia) "Itens com diferença" else "Itens",
                ) { listaAberta = !listaAberta }
            }
            if (listaAberta && visiveis.isEmpty()) {
                item {
                    Text(
                        if (soDivergencia) "Nenhuma divergência encontrada -- todo o estoque com nota fiscal bate com o físico."
                        else "Nenhum item com movimento registrado ainda.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
            if (listaAberta) items(visiveis) { i -> ReconciliacaoItemCard(i) }
        }
    }
}

@Composable
private fun ReconciliacaoItemCard(i: ReconciliacaoEstoqueItemData) {
    val divergente = kotlin.math.abs(i.diferenca) > TOLERANCIA
    val cor = if (divergente) com.bragro.mobile.ui.domain.corAtencao() else com.bragro.mobile.ui.domain.corSucesso()
    val un = i.unidade?.let { " $it" } ?: ""
    var aberto by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth().clickable { aberto = !aberto }, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(i.item, style = MaterialTheme.typography.bodyLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (!i.categoria.isNullOrBlank()) Text(i.categoria, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    if (divergente) (if (i.diferenca > 0) "+" else "") + formatoNumero(i.diferenca) + un else "confere",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold,
                    color = cor,
                    modifier = Modifier.background(cor.copy(alpha = 0.15f), androidx.compose.foundation.shape.RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 2.dp),
                )
                Icon(if (aberto) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = if (aberto) "Recolher" else "Expandir")
            }
            if (aberto) {
                @Composable
                fun linha(rotulo: String, valor: String, c: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(rotulo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(valor, style = MaterialTheme.typography.bodySmall, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = c)
                    }
                }
                linha("Saldo fiscal", formatoNumero(i.saldoFiscal) + un)
                linha("Saldo real", formatoNumero(i.saldoFisico) + un)
                linha("Diferença", formatoNumero(i.diferenca) + un, cor)
                if (i.origensNaoFiscais.isNotEmpty()) {
                    linha("Sem nota", i.origensNaoFiscais.joinToString(" · ") { "${it.origem.lowercase()} ${formatoNumero(it.liquido)}" })
                }
            }
        }
    }
}
