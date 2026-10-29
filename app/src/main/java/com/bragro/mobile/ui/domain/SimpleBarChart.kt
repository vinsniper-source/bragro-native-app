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

/** Quebra o nome em 2 linhas no espaço mais perto do meio (texto muito
 * extenso, ver regra v46 abaixo). Sem espaço, devolve o nome inteiro. */
internal fun quebrarEmDuas(nome: String): List<String> {
    val palavras = nome.trim().split(Regex("\\s+"))
    if (palavras.size < 2) return listOf(nome)
    var melhor = 1
    var menorDif = Int.MAX_VALUE
    for (j in 1 until palavras.size) {
        val dif = kotlin.math.abs(palavras.take(j).joinToString(" ").length - palavras.drop(j).joinToString(" ").length)
        if (dif < menorDif) { menorDif = dif; melhor = j }
    }
    return listOf(palavras.take(melhor).joinToString(" "), palavras.drop(melhor).joinToString(" "))
}

private enum class ModoRotulo { DEITADO, EM_PE, COMPACTO }

/**
 * v46/v47 -- regra geral dos rótulos (mockups v46 e v47). O PDF e a
 * impressão (HtmlPrinter) usam a mesma regra, recalculada para a página em
 * paisagem. A decisão vale para
 * o gráfico inteiro, então todas as barras ficam iguais:
 *  1. Textos e valores curtos (cabem na HORIZONTAL na largura da coluna
 *     sem encostar no vizinho): texto na horizontal na BASE da barra e
 *     número curto na horizontal no TOPO.
 *  2. Texto ou valor extenso (colidiria com a barra vizinha): nome em pé à
 *     ESQUERDA da barra e valor em pé à DIREITA, ambos começando na base.
 *  3. Texto muito extenso (em pé não cabe na altura do gráfico): o nome
 *     quebra em DUAS colunas em pé à esquerda; valor segue em pé à direita.
 *  Sem largura nem para os rótulos em pé (muitas colunas estreitas): nomes
 *  deitados abaixo do eixo a cada N colunas, sem valor.
 * Com mais de uma série os valores não aparecem (ficam na legenda/toque).
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
        val lh = with(densidade) { medidor.measure("Ag", estilo).size.height.toDp() }
        // Valores só com série única.
        val valores = if (series.size == 1) series[0].values.map { if (it != 0.0) fmtChartValue(it, isMoney) else "" } else emptyList()
        val temValor = valores.any { it.isNotEmpty() }
        val nomeMax = categories.maxOf { largura(it) }
        val valorMax = if (temValor) valores.maxOf { largura(it) } else 0.dp
        val barraMin = 4.dp
        val folga = 2.dp

        // 1. cabe deitado?
        val cabeDeitado = nomeMax + folga <= colW && valorMax + folga <= colW
        // 2/3. em pé: 1 coluna se o nome cabe na altura, senão 2 colunas.
        val linhasNome = if (nomeMax <= barsHeight) 1 else 2
        // v63 -- números curtos sem R$ que cabem deitados na coluna vão para
        // a base: aí não precisa reservar a faixa do valor em pé.
        val curtosPossivel = temValor && !isMoney && valores.all { it.isEmpty() || largura(it) + folga <= colW }
        val larguraRotulosEmPe = lh * linhasNome + (if (temValor && !curtosPossivel) lh else 0.dp)
        val cabeEmPe = colW >= larguraRotulosEmPe + barraMin * series.size + 2.dp
        val modo = when {
            cabeDeitado -> ModoRotulo.DEITADO
            cabeEmPe -> ModoRotulo.EM_PE
            else -> ModoRotulo.COMPACTO
        }
        val nomesEmPe = categories.map { if (modo == ModoRotulo.EM_PE && linhasNome == 2) quebrarEmDuas(it) else listOf(it) }
        val mostraValor = temValor && modo != ModoRotulo.COMPACTO
        // Reserva no topo para o valor deitado da barra mais alta.
        val reservaTopo = if (modo == ModoRotulo.DEITADO && mostraValor) lh + 2.dp else 0.dp
        val altUtil = barsHeight - reservaTopo
        val areaBarras = (colW - (if (modo == ModoRotulo.EM_PE) larguraRotulosEmPe else 0.dp)).coerceAtLeast(barraMin)
        val larguraBarra = ((areaBarras - 2.dp * series.size) / series.size).coerceIn(2.dp, if (series.size > 1) 10.dp else 18.dp)
        // v62 -- regra geral (todos os módulos): com nome em pé, o valor que
        // cabe deitado na largura da coluna (sem encostar no vizinho) vai
        // deitado, centralizado embaixo da base da barra; o que colidiria
        // continua em pé à direita da barra. Não muda o modo DEITADO.
        val larguraBarras = (larguraBarra + 2.dp) * series.size
        // v63 -- decisão do gráfico INTEIRO (nunca mistura): só números
        // curtos sem R$ e só se TODOS couberem na coluna vão para a base.
        val valoresCurtos = modo == ModoRotulo.EM_PE && mostraValor && curtosPossivel
        val valorEmbaixo = categories.indices.map { i -> valoresCurtos && !valores.getOrNull(i).isNullOrEmpty() }
        val temValorEmbaixo = valorEmbaixo.any { it }
        // Compacto: nome deitado a cada `passo` colunas.
        val passo = if (modo == ModoRotulo.COMPACTO) kotlin.math.ceil(((nomeMax + gap) / (colW + gap)).toDouble()).toInt().coerceAtLeast(1) else 1

        // v63 -- regra 3: barras que encostariam umas nas outras -> gráfico
        // horizontal (nome à esquerda, barra deitada, valor no fim). Séries
        // com linha de total (ex.: balanço hídrico) seguem na vertical.
        if (modo == ModoRotulo.COMPACTO && linha == null) {
            BarrasHorizontais(categories, series, valores, maxValue, maxWidth, ::largura, corNome, corValor, estilo)
            return@BoxWithConstraints
        }
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().height(barsHeight)) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    categories.forEachIndexed { i, _ ->
                        Row(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            // Nome em pé à esquerda (1 ou 2 colunas), começando da base.
                            if (modo == ModoRotulo.EM_PE) nomesEmPe[i].forEach { TextoEmPe(it, corNome) }
                            Column(
                                modifier = Modifier.fillMaxHeight(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Bottom,
                            ) {
                                // Valor deitado no topo da barra (modo curto).
                                if (modo == ModoRotulo.DEITADO && mostraValor && valores[i].isNotEmpty()) {
                                    Text(
                                        valores[i], style = estilo, color = corValor, maxLines = 1, softWrap = false,
                                        modifier = Modifier.padding(bottom = 2.dp).wrapContentWidth(unbounded = true),
                                    )
                                }
                                Row(verticalAlignment = Alignment.Bottom) {
                                    series.forEach { s ->
                                        val v = s.values.getOrElse(i) { 0.0 }
                                        val fraction = (v / maxValue).coerceIn(0.0, 1.0).toFloat()
                                        Box(
                                            modifier = Modifier
                                                .padding(horizontal = 1.dp)
                                                .width(larguraBarra)
                                                .height((altUtil * fraction).coerceAtLeast(1.dp))
                                                .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                                                .background(s.color),
                                        )
                                    }
                                }
                            }
                            // Valor em pé à direita (modo extenso).
                            if (modo == ModoRotulo.EM_PE && mostraValor) {
                                if (valoresCurtos) Unit
                                else if (valores[i].isNotEmpty()) TextoEmPe(valores[i], corValor, negrito = true)
                                else Spacer(Modifier.width(lh))
                            }
                        }
                    }
                }
                if (linha != null) {
                    val corLinha = linha.color
                    androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
                        val gapPx = gap.toPx()
                        val cw = (size.width - gapPx * (n - 1)) / n
                        val utilPx = altUtil.toPx()
                        val path = androidx.compose.ui.graphics.Path()
                        linha.values.take(n).forEachIndexed { i, v ->
                            val x = i * (cw + gapPx) + cw / 2
                            val yy = size.height - utilPx * (v / maxValue).coerceIn(0.0, 1.0).toFloat()
                            if (i == 0) path.moveTo(x, yy) else path.lineTo(x, yy)
                            drawCircle(corLinha, radius = 2.dp.toPx(), center = androidx.compose.ui.geometry.Offset(x, yy))
                        }
                        drawPath(path, corLinha, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx()))
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
            // v62 -- valores curtos deitados, centralizados embaixo da barra
            // (mesma montagem da linha das barras: nome, barras, espaço do valor).
            if (temValorEmbaixo) {
                Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    categories.indices.forEach { i ->
                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center) {
                            Spacer(Modifier.width(lh * nomesEmPe[i].size))
                            Box(Modifier.width(larguraBarras), contentAlignment = Alignment.TopCenter) {
                                if (valorEmbaixo[i]) Text(
                                    valores[i], style = estilo, color = corValor, maxLines = 1, softWrap = false,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                    modifier = Modifier.wrapContentWidth(Alignment.CenterHorizontally, unbounded = true),
                                )
                            }
                        }
                    }
                }
            }
            // Nome deitado na base (modo curto e compacto).
            if (modo != ModoRotulo.EM_PE) {
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

/** v63 -- gráfico de barras HORIZONTAIS (regra 3 da regra geral): uma linha
 * por item, nome à esquerda (todos aparecem), barra(s) deitada(s) e o valor
 * no fim da barra (só com série única). */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun BarrasHorizontais(
    categories: List<String>,
    series: List<BarSeries>,
    valores: List<String>,
    maxValue: Double,
    larguraTotal: Dp,
    largura: (String) -> Dp,
    corNome: Color,
    corValor: Color,
    estilo: androidx.compose.ui.text.TextStyle,
) {
    val nomeW = (categories.maxOf { largura(it) } + 6.dp).coerceAtMost(larguraTotal * 0.38f)
    val valorW = if (valores.isEmpty()) 0.dp else (valores.maxOf { largura(it) } + 6.dp).coerceAtMost(larguraTotal * 0.32f)
    val barraAlt = if (series.size > 1) 6.dp else 11.dp
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        categories.forEachIndexed { i, cat ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    cat, style = estilo, color = corNome, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    modifier = Modifier.width(nomeW).padding(end = 6.dp),
                )
                androidx.compose.foundation.layout.BoxWithConstraints(Modifier.weight(1f)) {
                    val util = (maxWidth - valorW).coerceAtLeast(8.dp)
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        series.forEach { s ->
                            val v = s.values.getOrElse(i) { 0.0 }
                            val frac = (v / maxValue).coerceIn(0.0, 1.0).toFloat()
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier
                                        .width((util * frac).coerceAtLeast(1.dp))
                                        .height(barraAlt)
                                        .clip(RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp))
                                        .background(s.color),
                                )
                                if (series.size == 1 && valores.getOrNull(i).orEmpty().isNotEmpty()) {
                                    Text(
                                        valores[i], style = estilo, color = corValor, maxLines = 1, softWrap = false,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                        modifier = Modifier.padding(start = 4.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        if (series.size > 1) {
            androidx.compose.foundation.layout.FlowRow(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                series.forEach { s ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.width(8.dp).height(8.dp).background(s.color))
                        Spacer(Modifier.width(4.dp))
                        Text(s.label, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

fun defaultSeriesColor(index: Int): Color = SERIES_COLORS[index % SERIES_COLORS.size]

fun formatChartValue(v: Double, isMoney: Boolean): String = fmtChartValue(v, isMoney)
