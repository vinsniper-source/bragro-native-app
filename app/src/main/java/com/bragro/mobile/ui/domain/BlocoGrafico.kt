package com.bragro.mobile.ui.domain

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * v20 -- bloco individual de gráfico com, no canto superior direito e na
 * vertical: setinha (recolhe só este gráfico), Imprimir e PDF (só este
 * gráfico). Usado em todos os gráficos do app.
 */
@Composable
fun BlocoGrafico(
    titulo: String,
    modifier: Modifier = Modifier,
    onPrint: (() -> Unit)? = null,
    onPdf: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    // v40 -- o bloco nasce recolhido; Imprimir e PDF sobem para a linha do
    // topo, lado a lado; o L sai e fica só a curva na borda inferior direita
    // (desenhada pelo Card do app).
    var aberto by rememberSaveable(titulo) { mutableStateOf(false) }
    com.bragro.mobile.ui.theme.Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(start = 12.dp, top = 6.dp, bottom = 10.dp, end = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (onPrint != null || onPdf != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    // v281 (mockup v343) -- imprimir e PDF sem borda em volta, em negrito.
                    if (onPrint != null) IconButton(onClick = onPrint, modifier = Modifier.size(36.dp)) {
                        com.bragro.mobile.ui.domain.IconeNegrito(com.bragro.mobile.ui.domain.IconesVazados.Print, contentDescription = "Imprimir gráfico", tint = androidx.compose.material3.LocalContentColor.current, size = 22.dp)
                    }
                    if (onPdf != null) IconButton(onClick = onPdf, modifier = Modifier.size(36.dp)) {
                        com.bragro.mobile.ui.domain.IconeNegrito(com.bragro.mobile.ui.domain.IconesVazados.PictureAsPdf, contentDescription = "PDF do gráfico", tint = androidx.compose.material3.LocalContentColor.current, size = 22.dp)
                    }
                }
            }
            if (aberto) content() else Text(titulo, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
        }
        com.bragro.mobile.ui.domain.CantoL(aberto, onClick = { aberto = !aberto })
    }
}

/** Monta os dados de impressão/PDF de um gráfico de barras simples. */
fun dadosGrafico(titulo: String, categorias: List<String>, series: List<Pair<String, List<Double>>>, isMoney: Boolean): com.bragro.mobile.ui.print.HtmlPrinter.ChartPrintData =
    com.bragro.mobile.ui.print.HtmlPrinter.ChartPrintData(
        title = titulo,
        headers = listOf("Item") + series.map { it.first },
        rows = categorias.mapIndexed { i, c -> listOf(c) + series.map { (_, v) -> formatChartValue(v.getOrElse(i) { 0.0 }, isMoney) } },
        barCategories = categorias,
        barSeries = series,
        isMoney = isMoney,
    )

/** "2026-10-14" -> "14/10" (eixo dos gráficos no padrão brasileiro). */
fun diaMesBr(iso: String): String {
    // v273 (mockup v328) -- data sempre completa (dd/mm/aaaa); aceita também
    // data no formato americano (mm/dd/aaaa) vinda da API.
    val d = iso.take(10).split("-")
    if (d.size == 3 && d[0].length == 4) return "${d[2]}/${d[1]}/${d[0]}"
    val us = Regex("^(\\d{1,2})/(\\d{1,2})/(\\d{4})").find(iso.trim())
    if (us != null) {
        val (a, b, ano) = us.destructured
        val (dia, mes) = if (a.toInt() > 12) a to b else if (b.toInt() > 12) b to a else b to a
        return "${dia.padStart(2, '0')}/${mes.padStart(2, '0')}/$ano"
    }
    return iso
}

/** BlocoGrafico com Imprimir/PDF já ligados a um gráfico de barras simples. */
@Composable
fun BlocoGraficoBarras(titulo: String, categorias: List<String>, series: List<Pair<String, List<Double>>>, isMoney: Boolean, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val dados = dadosGrafico(titulo, categorias, series, isMoney)
    BlocoGrafico(
        titulo = titulo,
        modifier = modifier,
        onPrint = { com.bragro.mobile.ui.print.HtmlPrinter.printCharts(context, titulo, listOf(dados)) },
        onPdf = { com.bragro.mobile.ui.print.HtmlPrinter.exportChartsPdfDirect(context, titulo, listOf(dados)) },
        content = content,
    )
}
