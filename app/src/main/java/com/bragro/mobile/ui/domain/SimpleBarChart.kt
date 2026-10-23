package com.bragro.mobile.ui.domain

import androidx.compose.foundation.background
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import com.bragro.mobile.ui.i18n.Text
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

/** Texto girado -90° (lê de baixo para cima) que ocupa exatamente a
 * largura = altura da linha e a altura = comprimento do texto. Encostado
 * no fundo de uma Row alinhada em Bottom, começa na base da barra. */
@Composable
private fun TextoEmPe(texto: String, cor: Color, negrito: Boolean = false) {
    Text(
        texto,
        style = MaterialTheme.typography.labelSmall,
        color = cor,
        fontWeight = if (negrito) androidx.compose.ui.text.font.FontWeight.SemiBold else null,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier.layout { measurable, _ ->
            val p = measurable.measure(Constraints())
            layout(p.height, p.width) {
                p.placeWithLayer(x = (p.height - p.width) / 2, y = (p.width - p.height) / 2) { rotationZ = -90f }
            }
        },
    )
}

/**
 * v45 -- regra dos rótulos (Pontos C e E do mockup v45):
 *  - Layout consistente para TODAS as barras (sem exceção -- Ponto E):
 *    [nome-vertical-esq] [barra] [valor-dir]
 *  - nome: sempre em pé (vertical) à ESQUERDA da barra, começando da base
 *    (quando não cabe em pé, deitado abaixo do eixo a cada N colunas)
 *  - valor: DEITADO acima da barra quando a barra é alta o suficiente
 *    (>= linhaTexto + 4dp de margem -- Ponto C); caso contrário em pé à
 *    DIREITA da barra começando da base. Regra idêntica para todas as barras.
 * `linha` desenha uma série extra como linha por cima das barras (ex.:
 * Total no balanço hídrico).
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SimpleBarChart(
    categories: List<String>,
    series: List<BarSeries>,
    isMoney: Boolean,
    barsHeight: Dp = 140.dp,
    linha: BarSeries? = null,
) {
    if (categories.isEmpty() || series.isEmpty()) {
        Text("Sem dados para este gráfico.", style = MaterialTheme.typography.bodySmall)
        return
    }
    val maxValue = (series.flatMap { it.values } + (linha?.values ?: emptyList())).maxOrNull().let { if (it == null || it <= 0.0) 1.0 else it }
    val estilo = MaterialTheme.typography.labelSmall
    val medidor = androidx.compose.ui.text.rememberTextMeasurer()
    val densidade = androidx.compose.ui.platform.LocalDensity.current
    val corNome = MaterialTheme.colorScheme.onSurfaceVariant
    val corValor = MaterialTheme.colorScheme.onSurface
    val gap = 4.dp

    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
        val n = categories.size
        val colW = (maxWidth - gap * (n - 1)) / n
        fun largura(s: String): Dp = if (s.isEmpty()) 0.dp else with(densidade) {
            medidor.measure(s, estilo, maxLines = 1, softWrap = false).size.width.toDp()
        }
        val linhaTexto = with(densidade) { medidor.measure("Ag", estilo).size.height.toDp() }
        // Valores formatados só para série única.
        val valores = if (series.size == 1) series[0].values.map { if (it != 0.0) fmtChartValue(it, isMoney) else "" } else emptyList()
        val nomeMax = categories.maxOf { largura(it) }
        val barraMin = 4.dp
        // v45 -- Ponto E: nome sempre em pé (vertical) à esquerda; valor em pé à direita.
        // Largura disponível para a barra = colW - linhaTexto(nome) - linhaTexto(valor se houver)
        val temValor = valores.isNotEmpty()
        // Decide se o nome cabe em pé dentro da coluna (limitado a linhaTexto de largura).
        // nomeEmPe = true: mostra à esquerda. nomeEmPe = false: mostra deitado abaixo (a cada N cols).
        val nomeEmPe = colW >= barraMin + linhaTexto
        val valorEmPe = temValor && colW >= barraMin + (if (nomeEmPe) linhaTexto else 0.dp) + linhaTexto
        val mostraValor = temValor && valorEmPe
        // Sem espaço nem em pé: nome deitado a cada `passo` colunas.
        val passo = if (nomeEmPe) 1 else kotlin.math.ceil(((nomeMax + gap) / (colW + gap)).toDouble()).toInt().coerceAtLeast(1)
        val areaBarras = (colW - (if (nomeEmPe) linhaTexto else 0.dp) - (if (mostraValor) linhaTexto else 0.dp)).coerceAtLeast(barraMin)
        val larguraBarra = ((areaBarras - 2.dp * series.size) / series.size).coerceIn(2.dp, if (series.size > 1) 10.dp else 18.dp)
        // Altura mínima da barra para o valor caber DEITADO acima (Ponto C).
        // O valor fica deitado só quando a barra tem altura >= (1 linha de texto + 4dp margem).
        val altMinValorDeitado = linhaTexto + 4.dp

        Column(modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().height(barsHeight)) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    categories.forEachIndexed { i, cat ->
                        // v45 -- Ponto E: layout idêntico para TODAS as barras:
                        //   [nome-em-pé-esq] [coluna-barra+valor-deitado] [valor-em-pé-dir]
                        Row(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            // Nome em pé à esquerda (começa da base -- Row alinhada em Bottom)
                            if (nomeEmPe) TextoEmPe(cat, corNome)

                            series.forEach { s ->
                                val v = s.values.getOrElse(i) { 0.0 }
                                val fraction = (v / maxValue).coerceIn(0.0, 1.0).toFloat()
                                val altBarra = barsHeight * fraction
                                // v45 -- Ponto C: valor deitado acima quando barra alta o suficiente,
                                // senão em pé à direita (tratado fora desta Column).
                                val valorDeitadoAqui = mostraValor && valores[i].isNotEmpty() && altBarra >= altMinValorDeitado
                                Column(
                                    modifier = Modifier.fillMaxHeight(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Bottom,
                                ) {
                                    if (valorDeitadoAqui) {
                                        // Valor deitado acima da barra (Ponto C)
                                        Text(
                                            valores[i], style = estilo, color = corValor, maxLines = 1, softWrap = false,
                                            modifier = Modifier.padding(bottom = 2.dp).wrapContentWidth(unbounded = true),
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .padding(horizontal = 1.dp)
                                            .width(larguraBarra)
                                            .fillMaxHeight(if (fraction > 0f) fraction else 0.01f)
                                            .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                                            .background(s.color),
                                    )
                                }
                            }

                            // v45 -- Ponto C/E: valor em pé à DIREITA da barra quando a barra
                            // não é alta o suficiente para o valor deitado. Layout idêntico para
                            // TODAS as barras (Ponto E) -- o bloco abaixo é sempre renderizado
                            // da mesma forma independentemente da altura da barra.
                            if (mostraValor && valores[i].isNotEmpty()) {
                                val v = series[0].values.getOrElse(i) { 0.0 }
                                val fraction = (v / maxValue).coerceIn(0.0, 1.0).toFloat()
                                val altBarra = barsHeight * fraction
                                val valorDeitadoAcima = altBarra >= altMinValorDeitado
                                // Só mostra em pé quando não coube deitado
                                if (!valorDeitadoAcima) {
                                    TextoEmPe(valores[i], corValor, negrito = true)
                                }
                            }
                        }
                    }
                }
                if (linha != null) {
                    val corLinha = linha.color
                    androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
                        val gapPx = gap.toPx()
                        val cw = (size.width - gapPx * (n - 1)) / n
                        val path = androidx.compose.ui.graphics.Path()
                        linha.values.take(n).forEachIndexed { i, v ->
                            val x = i * (cw + gapPx) + cw / 2
                            val yy = size.height * (1f - (v / maxValue).coerceIn(0.0, 1.0).toFloat())
                            if (i == 0) path.moveTo(x, yy) else path.lineTo(x, yy)
                            drawCircle(corLinha, radius = 2.dp.toPx(), center = androidx.compose.ui.geometry.Offset(x, yy))
                        }
                        drawPath(path, corLinha, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx()))
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
            if (!nomeEmPe) {
                Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    categories.forEachIndexed { i, cat ->
                        val restante = colW * (n - i) + gap * (n - i - 1)
                        val mostra = i % passo == 0 && (largura(cat) <= restante || passo == 1)
                        Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                            if (mostra) Text(
                                cat, style = estilo, color = corNome, maxLines = 1, softWrap = false,
                                modifier = Modifier.wrapContentWidth(Alignment.CenterHorizontally, unbounded = true),
                            )
                        }
                    }
                }
            }
            val legenda = series + listOfNotNull(linha)
            if (legenda.size > 1) {
                androidx.compose.foundation.layout.FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    legenda.forEach { s ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.width(if (s === linha) 12.dp else 8.dp).height(if (s === linha) 2.dp else 8.dp).background(s.color))
                            Spacer(Modifier.width(4.dp))
                            Text(s.label, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

fun defaultSeriesColor(index: Int): Color = SERIES_COLORS[index % SERIES_COLORS.size]

fun formatChartValue(v: Double, isMoney: Boolean): String = fmtChartValue(v, isMoney)
