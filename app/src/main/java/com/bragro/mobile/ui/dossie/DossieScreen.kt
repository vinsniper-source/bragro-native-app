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
import androidx.compose.material.icons.filled.Print
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
import androidx.compose.ui.platform.LocalContext
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
import com.bragro.mobile.ui.print.HtmlPrinter
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

private fun escapeHtml(value: String): String =
    value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

/** Monta o HTML impresso do Dossiê Bancário a partir dos MESMOS dados já
 * exibidos em Compose acima (nenhum cálculo novo) -- mesmas seções: Resumo
 * Executivo, Resultado por Fazenda (DRE), Livro Caixa, Contratos Ativos e
 * Patrimônio. Retrato (ver HtmlPrinter.printHtml) por ser um documento pra
 * anexar num pedido de crédito rural ou apresentar a um investidor, não uma
 * tabela larga de lista. */
private fun buildDossieHtml(d: DossieResponse, ano: Int): String {
    val geradoEm = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).format(java.util.Date())
    val dre = d.dre
    val livroCaixa = d.livroCaixa
    val patrimonio = d.patrimonio

    val resumoSecao = if (dre != null) {
        val resumoRows = """
            <tr><td>Área total</td><td>${escapeHtml(formatArea(dre.totais.areaHa))}</td></tr>
            <tr><td>Receita total</td><td>${escapeHtml(formatMoneyBrl(dre.totais.receitaTotal))}</td></tr>
            <tr><td>Custo total</td><td>${escapeHtml(formatMoneyBrl(dre.totais.custoTotal))}</td></tr>
            <tr><td>Margem</td><td>${escapeHtml(formatMoneyBrl(dre.totais.margem))}</td></tr>
            <tr><td>Custo/ha</td><td>${escapeHtml(formatMoneyBrl(dre.totais.custoPorHa))}</td></tr>
        """.trimIndent()
        val dreFazendaRows = dre.porFazenda.joinToString("") { f ->
            """
                <tr>
                  <td>${escapeHtml(f.farmName)}</td>
                  <td>${escapeHtml(formatArea(f.areaHa))}</td>
                  <td>${escapeHtml(formatMoneyBrl(f.custoTotal))}</td>
                  <td>${escapeHtml(formatMoneyBrl(f.receitaTotal))}</td>
                  <td>${escapeHtml(formatMoneyBrl(f.margem))}</td>
                </tr>
            """.trimIndent()
        }
        val dreFazendaTabela = if (dre.porFazenda.isEmpty()) {
            "<p>Nenhuma fazenda cadastrada.</p>"
        } else {
            "<table><thead><tr><th>Fazenda</th><th>Área</th><th>Custo</th><th>Receita</th><th>Margem</th></tr></thead><tbody>$dreFazendaRows</tbody></table>"
        }
        """
            <h2>Resumo Executivo</h2>
            <table><tbody>$resumoRows</tbody></table>
            <h2>Resultado por Fazenda (DRE)</h2>
            $dreFazendaTabela
        """.trimIndent()
    } else ""

    val livroCaixaSecao = if (livroCaixa != null) {
        """
            <h2>Livro Caixa -- $ano</h2>
            <table><tbody>
              <tr><td>Entradas</td><td>${escapeHtml(formatMoneyBrl(livroCaixa.totalEntradas))}</td></tr>
              <tr><td>Saídas</td><td>${escapeHtml(formatMoneyBrl(livroCaixa.totalSaidas))}</td></tr>
              <tr><td>Saldo do ano</td><td>${escapeHtml(formatMoneyBrl(livroCaixa.saldoFinal))}</td></tr>
            </tbody></table>
        """.trimIndent()
    } else ""

    val contratoRows = d.contratosAtivos.joinToString("") { c ->
        val vencimento = c.vencimento?.let { it.substring(0, minOf(10, it.length)).split("-").reversed().joinToString("/") }
            ?: "Vitalício/sem vencimento"
        """
            <tr>
              <td>${escapeHtml(c.descricao)}</td>
              <td>${escapeHtml(c.tipo ?: "—")}</td>
              <td>${escapeHtml(c.contraparte ?: "—")}</td>
              <td>${escapeHtml(formatMoneyBrl(c.valorR))}</td>
              <td>${escapeHtml(vencimento)}</td>
            </tr>
        """.trimIndent()
    }
    val contratosSecao = """
        <h2>Contratos Ativos -- ${d.contratosAtivos.size} contrato(s), total ${escapeHtml(formatMoneyBrl(d.contratosValorTotal))}</h2>
        ${
            if (d.contratosAtivos.isEmpty()) {
                "<p>Nenhum contrato ativo cadastrado.</p>"
            } else {
                "<table><thead><tr><th>Descrição</th><th>Tipo</th><th>Contraparte</th><th>Valor</th><th>Vencimento</th></tr></thead><tbody>$contratoRows</tbody></table>"
            }
        }
    """.trimIndent()

    val patrimonioSecao = if (patrimonio != null) {
        val patrimonioItemRows = patrimonio.principais.joinToString("") { p ->
            """
                <tr>
                  <td>${escapeHtml(p.descricao)}</td>
                  <td>${escapeHtml(p.categoria ?: "—")}</td>
                  <td>${escapeHtml(formatMoneyBrl(p.valorContabil))}</td>
                </tr>
            """.trimIndent()
        }
        val itensTabela = if (patrimonio.principais.isNotEmpty()) {
            "<table><thead><tr><th>Descrição</th><th>Categoria</th><th>Valor contábil</th></tr></thead><tbody>$patrimonioItemRows</tbody></table>"
        } else ""
        """
            <h2>Patrimônio (Ativos) -- ${patrimonio.totalItens} item(ns)</h2>
            <table><tbody>
              <tr><td>Valor de aquisição (total)</td><td>${escapeHtml(formatMoneyBrl(patrimonio.valorAquisicaoTotal))}</td></tr>
              <tr><td>Depreciação acumulada</td><td>${escapeHtml(formatMoneyBrl(patrimonio.deprecAcumTotal))}</td></tr>
              <tr><td>Valor contábil (atual)</td><td>${escapeHtml(formatMoneyBrl(patrimonio.valorContabilTotal))}</td></tr>
            </tbody></table>
            $itensTabela
        """.trimIndent()
    } else ""

    return """
        <!DOCTYPE html><html lang="pt-BR"><head><meta charset="utf-8"><title>Dossiê Bancário</title>
        <style>
          @page { size: portrait; margin: 14mm; }
          body { font-family: Arial, Helvetica, sans-serif; padding: 16px; color: #111; }
          h1 { font-size: 20px; margin-bottom: 2px; }
          h2 { font-size: 14px; margin-top: 20px; margin-bottom: 6px; border-bottom: 1px solid #ccc; padding-bottom: 4px; }
          p.subtitulo { font-size: 11px; color: #666; margin-top: 0; margin-bottom: 12px; }
          table { border-collapse: collapse; width: 100%; font-size: 11px; margin-bottom: 8px; }
          th, td { border: 1px solid #ccc; padding: 6px 8px; text-align: left; }
          th { background: #f2f2f2; }
        </style></head>
        <body>
          <h1>Dossiê Bancário</h1>
          <p class="subtitulo">Gerado em $geradoEm -- relatório único pra anexar num pedido de crédito rural ou apresentar a um investidor.</p>
          $resumoSecao
          $livroCaixaSecao
          $contratosSecao
          $patrimonioSecao
        </body></html>
    """.trimIndent()
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
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dossiê Bancário", color = MaterialTheme.colorScheme.primary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                // Ícone Imprimir/PDF (Task #615/#711, paridade com o botão
                // Imprimir do dossie-client.tsx no site) -- monta o MESMO
                // relatório já exibido na tela (Resumo Executivo + DRE por
                // fazenda + Livro Caixa + Contratos + Patrimônio) como um
                // único HTML e reaproveita o motor de impressão genérico do
                // HtmlPrinter (mesmo usado por Reconciliação/Frota/DRE/Livro
                // Caixa), em vez de recriar o mecanismo de impressão aqui.
                actions = {
                    val d = dados
                    if (d != null) {
                        IconButton(onClick = { HtmlPrinter.printHtml(context, "Dossiê Bancário", buildDossieHtml(d, ano)) }) {
                            Icon(Icons.Filled.Print, contentDescription = "Imprimir", tint = MaterialTheme.colorScheme.primary)
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
