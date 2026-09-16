package com.bragro.mobile.ui.dossie

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bragro.mobile.data.model.DossieContratoData
import com.bragro.mobile.data.model.DossieDreFazendaData
import com.bragro.mobile.data.model.DossiePatrimonioItemData
import com.bragro.mobile.data.model.DossieResponse
import com.bragro.mobile.data.repo.DossieRepository
import com.bragro.mobile.ui.theme.Card
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

/** Dossiê Bancário (Task #599/#615, paridade com dossie-client.tsx do
 * site): relatório de LEITURA pura que junta DRE + Livro Caixa + Contratos
 * ativos + Patrimônio numa única tela, pra anexar num pedido de crédito
 * rural ou apresentar a um investidor -- busca em /api/mobile/dossie
 * (mesma função de serviço do site, getDossieData, nenhum cálculo refeito
 * aqui). Sem cache no Room de propósito (ver DossieRepository) -- é um
 * relatório ponto-no-tempo. Sem edição nenhuma -- os dados se editam nos
 * módulos de origem (DRE/Livro Caixa/Contratos/Inventário). */
class DossieViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = DossieRepository(app)

    var carregando = mutableStateOf(true)
        private set
    var erro = mutableStateOf<String?>(null)
        private set
    var dados = mutableStateOf<DossieResponse?>(null)
        private set
    var ano = mutableStateOf(java.util.Calendar.getInstance().get(java.util.Calendar.YEAR))
        private set

    init { carregar() }

    fun setAno(value: Int) {
        ano.value = value
        carregar()
    }

    fun carregar() {
        carregando.value = true
        erro.value = null
        viewModelScope.launch {
            val resultado = repository.fetch(ano.value)
            carregando.value = false
            if (resultado == null) {
                erro.value = "Sem conexão -- não foi possível carregar o dossiê agora."
                return@launch
            }
            dados.value = resultado
        }
    }
}

private fun formatMoneyBrl(value: Double): String =
    NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(value)

private fun formatArea(value: Double): String {
    val nf = NumberFormat.getNumberInstance(Locale("pt", "BR"))
    nf.maximumFractionDigits = 1
    return "${nf.format(value)} ha"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnoDropdown(ano: Int, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val anoAtual = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
    val anos = (anoAtual downTo anoAtual - 4).toList()
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = Modifier.width(120.dp)) {
        OutlinedTextField(
            value = ano.toString(),
            onValueChange = {},
            readOnly = true,
            label = { Text("Ano") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth(),
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
fun DossieScreen(onBack: () -> Unit, viewModel: DossieViewModel = viewModel()) {
    val carregando by viewModel.carregando
    val erro by viewModel.erro
    val dados by viewModel.dados
    val ano by viewModel.ano

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dossiê Bancário", color = MaterialTheme.colorScheme.primary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = MaterialTheme.colorScheme.primary)
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
        if (erro != null || dados == null) {
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(erro ?: "Sem dados.", style = MaterialTheme.typography.bodyMedium)
            }
            return@Scaffold
        }

        val d = dados!!
        val dre = d.dre
        val livroCaixa = d.livroCaixa
        val patrimonio = d.patrimonio

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Relatório único pra anexar num pedido de crédito rural ou apresentar a um investidor.",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                    )
                    AnoDropdown(ano) { viewModel.setAno(it) }
                }
            }

            // Resumo Executivo
            if (dre != null) {
                item {
                    SecaoCard(titulo = "Resumo Executivo") {
                        LinhaResumo("Área total", formatArea(dre.totais.areaHa))
                        LinhaResumo("Receita total", formatMoneyBrl(dre.totais.receitaTotal))
                        LinhaResumo("Custo total", formatMoneyBrl(dre.totais.custoTotal))
                        LinhaResumo("Margem", formatMoneyBrl(dre.totais.margem), destaquePositivo = dre.totais.margem >= 0)
                        LinhaResumo("Custo/ha", formatMoneyBrl(dre.totais.custoPorHa))
                    }
                }

                // DRE por fazenda
                item { Text("Resultado por Fazenda (DRE)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
                if (dre.porFazenda.isEmpty()) {
                    item { Text("Nenhuma fazenda cadastrada.", style = MaterialTheme.typography.bodyMedium) }
                } else {
                    items(dre.porFazenda) { f -> FazendaDreCard(f) }
                }
            }

            // Livro Caixa
            if (livroCaixa != null) {
                item {
                    SecaoCard(titulo = "Livro Caixa -- $ano") {
                        LinhaResumo("Entradas", formatMoneyBrl(livroCaixa.totalEntradas), destaquePositivo = true)
                        LinhaResumo("Saídas", formatMoneyBrl(livroCaixa.totalSaidas), destaquePositivo = false)
                        LinhaResumo("Saldo do ano", formatMoneyBrl(livroCaixa.saldoFinal), destaquePositivo = livroCaixa.saldoFinal >= 0)
                    }
                }
            }

            // Contratos ativos
            item {
                Text(
                    "Contratos Ativos -- ${d.contratosAtivos.size} contrato(s), total ${formatMoneyBrl(d.contratosValorTotal)}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (d.contratosAtivos.isEmpty()) {
                item { Text("Nenhum contrato ativo cadastrado.", style = MaterialTheme.typography.bodyMedium) }
            } else {
                items(d.contratosAtivos) { c -> ContratoCard(c) }
            }

            // Patrimônio
            if (patrimonio != null) {
                item {
                    SecaoCard(titulo = "Patrimônio (Ativos) -- ${patrimonio.totalItens} item(ns)") {
                        LinhaResumo("Valor de aquisição (total)", formatMoneyBrl(patrimonio.valorAquisicaoTotal))
                        LinhaResumo("Depreciação acumulada", formatMoneyBrl(patrimonio.deprecAcumTotal))
                        LinhaResumo("Valor contábil (atual)", formatMoneyBrl(patrimonio.valorContabilTotal), destaquePositivo = true)
                    }
                }
                if (patrimonio.principais.isNotEmpty()) {
                    items(patrimonio.principais) { p -> PatrimonioItemCard(p) }
                    if (patrimonio.totalItens > patrimonio.principais.size) {
                        item {
                            Text(
                                "Mostrando os ${patrimonio.principais.size} itens de maior valor contábil. Lista completa em Inventário (Ativos).",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SecaoCard(titulo: String, conteudo: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(titulo, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            HorizontalDivider()
            conteudo()
        }
    }
}

@Composable
private fun LinhaResumo(label: String, valor: String, destaquePositivo: Boolean? = null) {
    val cor = when (destaquePositivo) {
        true -> MaterialTheme.colorScheme.primary
        false -> MaterialTheme.colorScheme.error
        null -> MaterialTheme.colorScheme.onSurface
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(valor, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = cor)
    }
}

@Composable
private fun FazendaDreCard(f: DossieDreFazendaData) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(f.farmName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column { Text("Área", style = MaterialTheme.typography.labelSmall); Text(formatArea(f.areaHa), style = MaterialTheme.typography.bodyMedium) }
                Column { Text("Custo", style = MaterialTheme.typography.labelSmall); Text(formatMoneyBrl(f.custoTotal), style = MaterialTheme.typography.bodyMedium) }
                Column { Text("Receita", style = MaterialTheme.typography.labelSmall); Text(formatMoneyBrl(f.receitaTotal), style = MaterialTheme.typography.bodyMedium) }
                Column {
                    Text("Margem", style = MaterialTheme.typography.labelSmall)
                    Text(
                        formatMoneyBrl(f.margem),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (f.margem >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun ContratoCard(c: DossieContratoData) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(c.descricao, style = MaterialTheme.typography.bodyLarge)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(c.tipo ?: "—", style = MaterialTheme.typography.labelSmall)
                Text(c.contraparte ?: "—", style = MaterialTheme.typography.labelSmall)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatMoneyBrl(c.valorR), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    c.vencimento?.let { it.substring(0, 10).split("-").reversed().joinToString("/") } ?: "Vitalício/sem vencimento",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun PatrimonioItemCard(p: DossiePatrimonioItemData) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(p.descricao, style = MaterialTheme.typography.bodyMedium)
                if (!p.categoria.isNullOrBlank()) Text(p.categoria, style = MaterialTheme.typography.labelSmall)
            }
            Text(formatMoneyBrl(p.valorContabil), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}
