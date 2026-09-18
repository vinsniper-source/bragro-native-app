package com.bragro.mobile.ui.estoque

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReconciliacaoEstoqueScreen(onBack: () -> Unit, viewModel: ReconciliacaoEstoqueViewModel = viewModel()) {
    val context = LocalContext.current
    val carregando by viewModel.carregando
    val erro by viewModel.erro
    val itens by viewModel.itens
    val totalItensComDivergencia by viewModel.totalItensComDivergencia
    val offline by viewModel.offline
    var soDivergencia by remember { mutableStateOf(true) }

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
                navigationIcon = {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
                // Ícones Imprimir + Nuvem na mesma altura do título -- pedido
                // do usuário, mesmo padrão dos demais módulos.
                actions = {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row {
                            IconButton(onClick = {
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
                            }) {
                                Icon(Icons.Filled.Print, contentDescription = "Imprimir", tint = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = {
                                val msg = if (offline) NetworkStatus.failureMessage(context) else "Conectado -- dados sincronizados com o servidor."
                                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(
                                    if (offline) Icons.Filled.CloudOff else Icons.Filled.Cloud,
                                    contentDescription = "Nuvem",
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
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

        val visiveis = if (soDivergencia) itens.filter { kotlin.math.abs(it.diferenca) > TOLERANCIA } else itens

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(
                                if (totalItensComDivergencia > 0) Icons.Filled.Warning else Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = if (totalItensComDivergencia > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            )
                            Column {
                                Text(
                                    "$totalItensComDivergencia ${if (totalItensComDivergencia == 1) "item com diferença" else "itens com diferença"}",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text("de ${itens.size} com movimento registrado", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Só divergências", style = MaterialTheme.typography.labelSmall)
                            Switch(checked = soDivergencia, onCheckedChange = { soDivergencia = it })
                        }
                    }
                }
            }
            if (visiveis.isEmpty()) {
                item {
                    Text(
                        if (soDivergencia) "Nenhuma divergência encontrada -- todo o estoque com nota fiscal bate com o físico."
                        else "Nenhum item com movimento registrado ainda.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
            items(visiveis) { i -> ReconciliacaoItemCard(i) }
        }
    }
}

@Composable
private fun ReconciliacaoItemCard(i: ReconciliacaoEstoqueItemData) {
    val divergente = kotlin.math.abs(i.diferenca) > TOLERANCIA
    val corDiferenca = if (divergente) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(i.item, style = MaterialTheme.typography.bodyLarge)
                    if (!i.categoria.isNullOrBlank()) Text(i.categoria, style = MaterialTheme.typography.labelSmall)
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Saldo Fiscal", style = MaterialTheme.typography.labelSmall)
                    Text("${formatoNumero(i.saldoFiscal)}${i.unidade?.let { " $it" } ?: ""}", style = MaterialTheme.typography.bodyMedium)
                }
                Column {
                    Text("Saldo Real", style = MaterialTheme.typography.labelSmall)
                    Text("${formatoNumero(i.saldoFisico)}${i.unidade?.let { " $it" } ?: ""}", style = MaterialTheme.typography.bodyMedium)
                }
                Column {
                    Text("Diferença", style = MaterialTheme.typography.labelSmall)
                    Text("${formatoNumero(i.diferenca)}${i.unidade?.let { " $it" } ?: ""}", style = MaterialTheme.typography.bodyMedium, color = corDiferenca)
                }
            }
            if (i.origensNaoFiscais.isNotEmpty()) {
                Text(
                    i.origensNaoFiscais.joinToString("  •  ") { "${it.origem}: ${formatoNumero(it.liquido)}" },
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}
