package com.bragro.mobile.ui.domain

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bragro.mobile.ui.i18n.Text
import java.text.NumberFormat
import java.util.Locale

/**
 * v53 -- gráfico de ÁREA (igual ao AreaChart da plataforma no estoque dos
 * silos): linha da série com preenchimento em degradê até o eixo, grade
 * horizontal tracejada, eixo de valores à esquerda (0 / meio / máximo) e
 * datas embaixo (primeira, algumas do meio e a última, sem colidir).
 */
@Composable
fun GraficoArea(
    categorias: List<String>,
    valores: List<Double>,
    cor: Color,
    altura: Dp = 170.dp,
    unidade: String = "",
) {
    if (categorias.isEmpty() || valores.isEmpty()) {
        Text("Sem dados para este gráfico.", style = MaterialTheme.typography.bodySmall)
        return
    }
    val medidor = rememberTextMeasurer()
    val estilo = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val corGrade = MaterialTheme.colorScheme.outlineVariant
    val nf = NumberFormat.getNumberInstance(Locale("pt", "BR")).apply { maximumFractionDigits = 1 }
    val maxBruto = valores.maxOrNull()?.takeIf { it > 0.0 } ?: 1.0
    val maximo = escalaRedonda(maxBruto)
    val n = minOf(categorias.size, valores.size)

    Canvas(Modifier.fillMaxWidth().height(altura)) {
        val rotulosY = listOf(0.0, maximo / 2, maximo).map { nf.format(it) + if (unidade.isNotEmpty() && it == maximo) " $unidade" else "" }
        val larguraEixo = rotulosY.maxOf { medidor.measure(it, estilo).size.width }.toFloat() + 6.dp.toPx()
        val alturaDatas = medidor.measure("00/00", estilo).size.height.toFloat() + 4.dp.toPx()
        val topo = 6.dp.toPx()
        val esq = larguraEixo
        val dir = size.width - 4.dp.toPx()
        val base = size.height - alturaDatas
        val alturaUtil = base - topo
        fun y(v: Double) = (base - (v / maximo) * alturaUtil).toFloat()
        fun x(i: Int) = if (n <= 1) (esq + dir) / 2 else esq + (dir - esq) * i / (n - 1)

        // Grade + eixo de valores.
        listOf(0.0, maximo / 2, maximo).forEachIndexed { k, v ->
            val yy = y(v)
            drawLine(corGrade, Offset(esq, yy), Offset(dir, yy), strokeWidth = 1f, pathEffect = if (k == 0) null else PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
            val t = medidor.measure(rotulosY[k], estilo)
            drawText(t, topLeft = Offset(esq - 6.dp.toPx() - t.size.width, yy - t.size.height / 2f))
        }

        // Área + linha.
        val linha = Path()
        for (i in 0 until n) {
            val p = Offset(x(i), y(valores[i]))
            if (i == 0) linha.moveTo(p.x, p.y) else linha.lineTo(p.x, p.y)
        }
        val area = Path().apply {
            addPath(linha)
            lineTo(x(n - 1), base)
            lineTo(x(0), base)
            close()
        }
        drawPath(area, Brush.verticalGradient(listOf(cor.copy(alpha = 0.35f), cor.copy(alpha = 0.04f)), startY = topo, endY = base))
        drawPath(linha, cor, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        if (n == 1) drawCircle(cor, 3.dp.toPx(), Offset(x(0), y(valores[0])))

        // Datas: só as que cabem sem encostar, sempre a primeira e a última.
        val larguraData = medidor.measure(categorias.maxByOrNull { it.length } ?: "", estilo).size.width.toFloat() + 8.dp.toPx()
        val passo = maxOf(1, kotlin.math.ceil(larguraData / ((dir - esq) / maxOf(1, n - 1))).toInt())
        val indices = (0 until n step passo).toMutableList()
        if (n > 1 && indices.last() != n - 1) {
            if (x(n - 1) - x(indices.last()) < larguraData) indices.removeAt(indices.size - 1)
            indices.add(n - 1)
        }
        indices.forEach { i ->
            val t = medidor.measure(categorias[i], estilo)
            val cx = (x(i) - t.size.width / 2f).coerceIn(esq - 2f, size.width - t.size.width.toFloat())
            drawText(t, topLeft = Offset(cx, base + 3.dp.toPx()))
        }
    }
}

/** Arredonda o máximo do eixo para 1/2/2,5/5 × 10^k (eixo "redondo"). */
private fun escalaRedonda(v: Double): Double {
    val exp = kotlin.math.floor(kotlin.math.log10(v))
    val base = Math.pow(10.0, exp)
    val f = v / base
    val passo = when {
        f <= 1.0 -> 1.0
        f <= 2.0 -> 2.0
        f <= 2.5 -> 2.5
        f <= 5.0 -> 5.0
        else -> 10.0
    }
    return passo * base
}
