package com.bragro.mobile.ui.domain

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bragro.mobile.ui.theme.BrBlue
import com.bragro.mobile.ui.theme.BrGreen
import com.bragro.mobile.ui.theme.BrYellow
import java.text.NumberFormat
import java.util.Locale

/** Gráfico de barras simples, feito só com Box/Row/Column (sem dependência
 * nova de biblioteca de gráficos) -- réplica visual leve do BarChart
 * (recharts) usado no site em module-charts-panel.tsx. Cada categoria vira
 * uma coluna com 1 barra por série, altura proporcional ao maior valor de
 * TODAS as séries/categorias. */
data class BarSeries(val label: String, val values: List<Double>, val color: Color)

private val SERIES_COLORS = listOf(BrGreen, BrYellow, BrBlue)
private val PT_BR = Locale("pt", "BR")

private fun fmtChartValue(v: Double, isMoney: Boolean): String {
    return if (isMoney) {
        NumberFormat.getCurrencyInstance(PT_BR).format(v)
    } else {
        NumberFormat.getNumberInstance(PT_BR).apply { maximumFractionDigits = 1 }.format(v)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SimpleBarChart(
    categories: List<String>,
    series: List<BarSeries>,
    isMoney: Boolean,
    barsHeight: Dp = 140.dp,
) {
    if (categories.isEmpty() || series.isEmpty()) {
        Text("Sem dados para este gráfico.", style = MaterialTheme.typography.bodySmall)
        return
    }
    val maxValue = (series.flatMap { it.values }.maxOrNull() ?: 0.0).let { if (it <= 0.0) 1.0 else it }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(barsHeight),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            categories.forEachIndexed { i, cat ->
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        // Série única (o caso mais comum: gráfico genérico do
                        // módulo, Eficiência de Frota etc.) ganha o valor
                        // escrito em cima da barra -- pedido do usuário
                        // ("preciso que coloque valores no topo"). Com mais
                        // de uma série o rótulo não cabe (barras finas lado a
                        // lado), então mantém só a legenda de cores abaixo.
                        series.forEachIndexed { si, s ->
                            val v = s.values.getOrElse(i) { 0.0 }
                            val fraction = (v / maxValue).coerceIn(0.0, 1.0).toFloat()
                            Column(
                                modifier = Modifier.fillMaxHeight(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Bottom,
                            ) {
                                if (series.size == 1 && v != 0.0) {
                                    Text(
                                        fmtChartValue(v, isMoney),
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Clip,
                                        modifier = Modifier.padding(bottom = 2.dp).basicMarquee(),
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 1.dp)
                                        .width(if (series.size > 1) 8.dp else 18.dp)
                                        .fillMaxHeight(if (fraction > 0f) fraction else 0.01f)
                                        .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                                        .background(s.color),
                                )
                            }
                        }
                    }
                    Text(
                        cat,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.padding(top = 2.dp).basicMarquee(),
                    )
                }
            }
        }
        if (series.size > 1) {
            Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                series.forEach { s ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.width(8.dp).height(8.dp).background(s.color))
                        Spacer(Modifier.width(4.dp))
                        Text(s.label, style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(Modifier.width(4.dp))
                }
            }
        }
    }
}

fun defaultSeriesColor(index: Int): Color = SERIES_COLORS[index % SERIES_COLORS.size]

fun formatChartValue(v: Double, isMoney: Boolean): String = fmtChartValue(v, isMoney)
