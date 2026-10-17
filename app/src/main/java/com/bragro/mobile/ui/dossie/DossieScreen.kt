package com.bragro.mobile.ui.dossie

import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ChevronLeft
import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.draw.clip
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
import com.bragro.mobile.ui.theme.SearchableDropdownField
import com.bragro.mobile.ui.theme.appFieldColors
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
    val anoAtual = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
    val anos = (anoAtual downTo anoAtual - 9).toList()
    SearchableDropdownField(
        value = ano.toString(),
        label = "Ano",
        options = remember(anos) { anos.map { it.toString() to it.toString() } },
        onSelect = { picked -> picked.toIntOrNull()?.let(onSelect) },
        modifier = Modifier.width(120.dp),
        emptyOptionLabel = null,
    )
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
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
                navigationIcon = { com.bragro.mobile.ui.domain.BackNavButton() },
                title = { Text("Dossiê Bancário", color = MaterialTheme.colorScheme.onSurface) },
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
                            Icon(Icons.Filled.Print, contentDescription = "Imprimir", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    com.bragro.mobile.ui.domain.HomeNavButton()
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

        // Seções em divisórias (padrão da plataforma), recolhíveis; o botão
        // "Seções" em Ações rola direto pra cada uma.
        val abertas = remember { androidx.compose.runtime.mutableStateMapOf<String, Boolean>() }
        fun aberta(id: String) = abertas[id] == true
        val listState = androidx.compose.foundation.lazy.rememberLazyListState()
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        val indiceSecao = remember { mutableMapOf<String, Int>() }
        val fazPositivas = dre?.porFazenda?.count { it.margem >= 0 } ?: 0

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            var n = 0
            item {
                Text(
                    "Relatório único pra anexar num pedido de crédito rural ou apresentar a um investidor — junta DRE, Livro Caixa, Contratos ativos e Patrimônio.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            n++
            if (dre != null) {
                item(key = "painel") {
                    com.bragro.mobile.ui.domain.PainelMonitoramento(
                        com.bragro.mobile.ui.domain.PainelModulo(
                            titulo = "Dossiê · exercício $ano",
                            subtitulo = "${dre.porFazenda.size} fazenda(s) · ${d.contratosAtivos.size} contrato(s) ativo(s)",
                            kpis = listOf(
                                com.bragro.mobile.ui.domain.PainelKpi("Área total", dre.totais.areaHa, "numero", "ha", "${dre.porFazenda.size} fazenda(s)", "Por fazenda"),
                                com.bragro.mobile.ui.domain.PainelKpi("Receita total", dre.totais.receitaTotal, "moeda", null, "mesma base do DRE", "Resumo"),
                                com.bragro.mobile.ui.domain.PainelKpi("Custo total", dre.totais.custoTotal, "moeda", null, "Financeiro + Frota + Safra", "Resumo"),
                                com.bragro.mobile.ui.domain.PainelKpi(
                                    "Margem", dre.totais.margem, "moeda", null,
                                    if (dre.totais.receitaTotal != 0.0) com.bragro.mobile.ui.domain.fmtPct(Math.round(dre.totais.margem / dre.totais.receitaTotal * 1000) / 10.0) + " da receita" else "sem receita",
                                    "Por fazenda", tom = if (dre.totais.margem >= 0) "abaixo" else "acima",
                                ),
                                com.bragro.mobile.ui.domain.PainelKpi("Custo / ha", dre.totais.custoPorHa, "moeda", null, formatArea(dre.totais.areaHa), "Resumo"),
                            ),
                            barras = listOfNotNull(
                                livroCaixa?.takeIf { it.totalEntradas > 0 }?.let {
                                    com.bragro.mobile.ui.domain.PainelBarra("Saldo do Livro Caixa sobre as entradas", maxOf(0.0, Math.round(it.saldoFinal / it.totalEntradas * 100).toDouble()), "${formatMoneyBrl(it.saldoFinal)} / ${formatMoneyBrl(it.totalEntradas)}")
                                },
                                dre.porFazenda.takeIf { it.isNotEmpty() }?.let {
                                    com.bragro.mobile.ui.domain.PainelBarra("Fazendas com margem positiva", Math.round(fazPositivas * 100.0 / it.size).toDouble(), "$fazPositivas/${it.size}")
                                },
                            ),
                        ),
                    )
                }
                n++
            }
            item(key = "acoes") {
                com.bragro.mobile.ui.domain.BlocoComFaixa("Ações") {
                    Column(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // v9: "Ano-Calendário / Exercício" com duas listas lado a lado
                        // (exercício = ano-calendário + 1; mudar uma ajusta a outra).
                        val anoAtual = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
                        val anos = (anoAtual downTo anoAtual - 9).toList()
                        Text("ANO-CALENDÁRIO / EXERCÍCIO", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SearchableDropdownField(
                                value = ano.toString(),
                                label = "Ano-calendário",
                                options = remember(anos) { anos.map { it.toString() to it.toString() } },
                                onSelect = { picked -> picked.toIntOrNull()?.let { viewModel.setAno(it) } },
                                modifier = Modifier.weight(1f),
                                emptyOptionLabel = null,
                            )
                            SearchableDropdownField(
                                value = (ano + 1).toString(),
                                label = "Exercício",
                                options = remember(anos) { anos.map { (it + 1).toString() to (it + 1).toString() } },
                                onSelect = { picked -> picked.toIntOrNull()?.let { viewModel.setAno(it - 1) } },
                                modifier = Modifier.weight(1f),
                                emptyOptionLabel = null,
                            )
                            androidx.compose.material3.OutlinedButton(
                                onClick = { HtmlPrinter.printHtml(context, "Dossiê Bancário", buildDossieHtml(d, ano)) },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(40.dp),
                            ) {
                                Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text(" PDF", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        val atalhos = listOf(
                            "resumo" to "Resumo", "fazendas" to "Por fazenda", "caixa" to "Livro Caixa",
                            "contratos" to "Contratos", "patrimonio" to "Patrimônio",
                        ).filter { indiceSecao.containsKey(it.first) }
                        atalhos.chunked(3).forEach { linha ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                linha.forEach { (id, rotulo) ->
                                    Box(
                                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                            .clickable {
                                                abertas[id] = true
                                                indiceSecao[id]?.let { i -> scope.launch { listState.animateScrollToItem(i) } }
                                            }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center,
                                    ) { Text(rotulo, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
                                }
                                repeat(3 - linha.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
            n++

            if (dre != null) {
                indiceSecao["resumo"] = n
                item(key = "sec-resumo") { Secao("Resumo executivo", aberta("resumo")) { abertas["resumo"] = !aberta("resumo") } }
                n++
                if (aberta("resumo")) {
                    item {
                        SecaoCard(titulo = null) {
                            LinhaResumo("Área total", formatArea(dre.totais.areaHa))
                            LinhaResumo("Receita total", formatMoneyBrl(dre.totais.receitaTotal))
                            LinhaResumo("Custo total", formatMoneyBrl(dre.totais.custoTotal))
                            LinhaResumo("Margem", formatMoneyBrl(dre.totais.margem), destaquePositivo = dre.totais.margem >= 0)
                            LinhaResumo("Custo/ha", formatMoneyBrl(dre.totais.custoPorHa))
                        }
                    }
                    n++
                }

                indiceSecao["fazendas"] = n
                item(key = "sec-fazendas") { Secao("Resultado por fazenda", aberta("fazendas"), "${dre.porFazenda.size}") { abertas["fazendas"] = !aberta("fazendas") } }
                n++
                if (aberta("fazendas")) {
                    if (dre.porFazenda.isEmpty()) {
                        item { Text("Nenhuma fazenda cadastrada.", style = MaterialTheme.typography.bodyMedium) }
                        n++
                    } else {
                        items(dre.porFazenda) { f -> FazendaDreCard(f) }
                        n += dre.porFazenda.size
                    }
                }
            }

            if (livroCaixa != null) {
                indiceSecao["caixa"] = n
                item(key = "sec-caixa") { Secao("Livro Caixa · $ano", aberta("caixa"), "regime de caixa") { abertas["caixa"] = !aberta("caixa") } }
                n++
                if (aberta("caixa")) {
                    item {
                        SecaoCard(titulo = null) {
                            LinhaResumo("Entradas", formatMoneyBrl(livroCaixa.totalEntradas), destaquePositivo = true)
                            LinhaResumo("Saídas", formatMoneyBrl(livroCaixa.totalSaidas), destaquePositivo = false)
                            LinhaResumo("Saldo do ano", formatMoneyBrl(livroCaixa.saldoFinal), destaquePositivo = livroCaixa.saldoFinal >= 0)
                        }
                    }
                    n++
                }
            }

            indiceSecao["contratos"] = n
            item(key = "sec-contratos") {
                Secao("Contratos ativos", aberta("contratos"), "${d.contratosAtivos.size} · ${formatMoneyBrl(d.contratosValorTotal)}") { abertas["contratos"] = !aberta("contratos") }
            }
            n++
            if (aberta("contratos")) {
                if (d.contratosAtivos.isEmpty()) {
                    item { Text("Nenhum contrato ativo cadastrado.", style = MaterialTheme.typography.bodyMedium) }
                    n++
                } else {
                    items(d.contratosAtivos) { c -> ContratoCard(c) }
                    n += d.contratosAtivos.size
                }
            }

            if (patrimonio != null) {
                indiceSecao["patrimonio"] = n
                item(key = "sec-patrimonio") {
                    Secao("Patrimônio (ativos)", aberta("patrimonio"), "${patrimonio.totalItens} item(ns)") { abertas["patrimonio"] = !aberta("patrimonio") }
                }
                n++
                if (aberta("patrimonio")) {
                    // Resumo + itens num cartão só (item único da lista): ao
                    // recolher a seção some tudo junto, sem itens soltos.
                    item(key = "patrimonio-corpo") {
                        SecaoCard(titulo = null) {
                            LinhaResumo("Valor de aquisição (total)", formatMoneyBrl(patrimonio.valorAquisicaoTotal))
                            LinhaResumo("Depreciação acumulada", formatMoneyBrl(patrimonio.deprecAcumTotal))
                            LinhaResumo("Valor contábil (atual)", formatMoneyBrl(patrimonio.valorContabilTotal), destaquePositivo = true)
                            if (patrimonio.principais.isNotEmpty()) {
                                HorizontalDivider()
                                patrimonio.principais.forEach { p -> PatrimonioItemLinha(p) }
                                if (patrimonio.totalItens > patrimonio.principais.size) {
                                    Text(
                                        "Mostrando os ${patrimonio.principais.size} itens de maior valor contábil. Lista completa em Inventário (Ativos).",
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                        }
                    }
                    n++
                }
            }
        }
    }
}

/** Divisória de seção "TÍTULO  extra ——— v" (mesma do LANÇAMENTOS). */
@Composable
private fun Secao(titulo: String, aberta: Boolean, extra: String? = null, onToggle: () -> Unit) {
    com.bragro.mobile.ui.domain.CabecalhoLancamentos(total = 0, aberto = aberta, titulo = titulo, rotulo = extra ?: "", onToggle = onToggle)
}

@Composable
private fun SecaoCard(titulo: String?, conteudo: @Composable () -> Unit) {
    var aberto by remember { mutableStateOf(true) }
    com.bragro.mobile.ui.theme.FormBlockCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (titulo != null) com.bragro.mobile.ui.domain.TituloRecolhivel(titulo, aberto) { aberto = !aberto }
            if (aberto || titulo == null) {
                if (titulo != null) HorizontalDivider()
                conteudo()
            }
        }
    }
}

@Composable
private fun LinhaResumo(label: String, valor: String, destaquePositivo: Boolean? = null) {
    val cor = when (destaquePositivo) {
        true -> com.bragro.mobile.ui.domain.corSucesso()
        false -> com.bragro.mobile.ui.domain.corAtencao()
        null -> MaterialTheme.colorScheme.onSurface
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(valor, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = cor)
    }
}

@Composable
private fun FazendaDreCard(f: DossieDreFazendaData) {
    com.bragro.mobile.ui.theme.FormBlockCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val lucro = f.margem >= 0
            val corSelo = if (lucro) com.bragro.mobile.ui.domain.corSucesso() else com.bragro.mobile.ui.domain.corAtencao()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(f.farmName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                // Selo LUCRO/PREJUÍZO (padrão da plataforma).
                Text(
                    if (lucro) "LUCRO" else "PREJUÍZO",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = corSelo,
                    modifier = Modifier
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
                        .background(corSelo.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column { Text("Área", style = MaterialTheme.typography.labelSmall); Text(formatArea(f.areaHa), style = MaterialTheme.typography.bodyMedium) }
                Column { Text("Custo", style = MaterialTheme.typography.labelSmall); Text(formatMoneyBrl(f.custoTotal), style = MaterialTheme.typography.bodyMedium) }
                Column { Text("Receita", style = MaterialTheme.typography.labelSmall); Text(formatMoneyBrl(f.receitaTotal), style = MaterialTheme.typography.bodyMedium) }
                Column {
                    Text("Margem", style = MaterialTheme.typography.labelSmall)
                    Text(
                        formatMoneyBrl(f.margem),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = corSelo,
                    )
                }
            }
        }
    }
}

@Composable
private fun ContratoCard(c: DossieContratoData) {
    com.bragro.mobile.ui.theme.FormBlockCard(modifier = Modifier.fillMaxWidth()) {
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
private fun PatrimonioItemLinha(p: DossiePatrimonioItemData) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(p.descricao, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            if (!p.categoria.isNullOrBlank()) Text(p.categoria, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(formatMoneyBrl(p.valorContabil), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun PatrimonioItemCard(p: DossiePatrimonioItemData) {
    com.bragro.mobile.ui.theme.FormBlockCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(p.descricao, style = MaterialTheme.typography.bodyMedium)
                if (!p.categoria.isNullOrBlank()) Text(p.categoria, style = MaterialTheme.typography.labelSmall)
            }
            Text(formatMoneyBrl(p.valorContabil), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}
