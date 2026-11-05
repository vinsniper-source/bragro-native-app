package com.bragro.mobile.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.Locale

/**
 * v87 (mockup v87 aprovado) -- histórico de cotações, igual à plataforma
 * (components/dashboard/historico-cotacoes.tsx): linha pequena de 12 meses
 * embaixo de cada grão e, ao tocar, o gráfico com Soja/Milho/Sorgo/Dólar,
 * períodos de 3/6/12 meses e a linha laranja da Sua média de venda.
 */

private val BR = Locale("pt", "BR")
private fun brl(v: Double, casas: Int = 2) = String.format(BR, "%,.${casas}f", v)
// minSdk 24: sem java.time -- datas como "dia desde 1970" (UTC).
private fun diaDe(iso: String): Long? {
    val m = Regex("""^(\d{4})-(\d{2})-(\d{2})""").find(iso) ?: return null
    val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
    cal.clear()
    cal.set(m.groupValues[1].toInt(), m.groupValues[2].toInt() - 1, m.groupValues[3].toInt())
    return cal.timeInMillis / 86_400_000L
}
private fun hoje(): Long = System.currentTimeMillis() / 86_400_000L
private fun dmDia(dia: Long): String {
    val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
    cal.timeInMillis = dia * 86_400_000L
    return String.format(BR, "%02d/%02d", cal.get(java.util.Calendar.DAY_OF_MONTH), cal.get(java.util.Calendar.MONTH) + 1)
}

private fun pontosDe(h: JsonObject?): List<Pair<Long, Double>> =
    (h?.get("pontos") as? JsonArray)?.mapNotNull { p ->
        val a = p as? JsonArray ?: return@mapNotNull null
        val d = (a.getOrNull(0) as? JsonPrimitive)?.content ?: return@mapNotNull null
        val v = (a.getOrNull(1) as? JsonPrimitive)?.content?.toDoubleOrNull() ?: return@mapNotNull null
        diaDe(d)?.let { it to v }
    }.orEmpty()

private fun inicioDe(h: JsonObject?): Long? =
    (h?.get("inicio") as? JsonPrimitive)?.content?.let { diaDe(it) }

private fun noPeriodo(h: JsonObject?, meses: Int): List<Pair<Long, Double>> {
    val limite = hoje() - (meses * 30.44).toLong()
    return pontosDe(h).filter { it.first >= limite }
}

@Composable
fun MiniHistoricoGrao(h: JsonObject?, onClick: () -> Unit) {
    val pts = noPeriodo(h, 12)
    if (pts.isEmpty()) return
    val inicio = inicioDe(h)
    val curto = pts.size < 4 || (inicio != null && inicio > hoje() - 300)
    val verde = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.padding(top = 3.dp).clickable(onClick = onClick)) {
        // v1.6.14 (mockup v115, item 2) -- minigráfico na largura toda (antes,
        // com pouco histórico, virava um risco solto no canto) e só com 3+ dias.
        val corLinha = if (pts.last().second >= pts.first().second) verde else com.bragro.mobile.ui.theme.BrOrange
        if (pts.size >= 3) {
            Canvas(Modifier.fillMaxWidth().height(24.dp)) {
                val vals = pts.map { it.second }
                val mn = vals.min(); val mx = vals.max()
                val t0 = pts.first().first.toFloat(); val t1 = pts.last().first.toFloat()
                val x0 = 0f
                val largura = size.width
                val path = Path()
                pts.forEachIndexed { i, (d, v) ->
                    val x = x0 + ((d - t0) / maxOf(1f, t1 - t0)) * largura
                    val y = if (mx == mn) size.height / 2 else size.height - 3f - ((v - mn) / (mx - mn)).toFloat() * (size.height - 6f)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, corLinha, style = Stroke(width = 1.6.dp.toPx()))
            }
        }
        val vals = pts.map { it.second }
        Text(
            if (curto && inicio != null) "histórico começando em ${dmDia(inicio)}" else "12 meses · mín ${brl(vals.min())} · máx ${brl(vals.max())}",
            style = MaterialTheme.typography.labelSmall,
            color = muted,
            maxLines = 2,
        )
    }
}

private val ROTULOS = linkedMapOf("soja" to "Soja", "milho" to "Milho", "sorgo" to "Sorgo", "dolar" to "Dólar")

@Composable
fun HistoricoCotacoesDialog(historico: JsonObject?, medias: JsonObject?, inicial: String, onDismiss: () -> Unit) {
    var serie by remember { mutableStateOf(inicial) }
    var meses by remember { mutableStateOf(12) }
    val verde = MaterialTheme.colorScheme.primary
    val laranja = com.bragro.mobile.ui.domain.corAtencao()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val grade = MaterialTheme.colorScheme.outlineVariant
    val h = historico?.get(serie) as? JsonObject
    val pts = noPeriodo(h, meses)
    val dolar = serie == "dolar"
    val media = if (dolar) null else (medias?.get(serie) as? JsonObject)?.let { m ->
        val v = (m["mediaRsSc"] as? JsonPrimitive)?.content?.toDoubleOrNull()
        val sf = (m["safra"] as? JsonPrimitive)?.content.orEmpty()
        v?.let { sf to it }
    }
    val inicio = inicioDe(h)
    val parcial = inicio != null && inicio > hoje() - (meses * 30.44).toLong()

    @Composable
    fun pill(texto: String, on: Boolean, onClick: () -> Unit) {
        Text(
            texto,
            style = MaterialTheme.typography.labelMedium,
            color = if (on) MaterialTheme.colorScheme.onPrimary else verde,
            modifier = Modifier
                .background(if (on) verde else MaterialTheme.colorScheme.surface, RoundedCornerShape(50))
                .border(1.dp, verde.copy(alpha = 0.35f), RoundedCornerShape(50))
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } },
        title = { Text("Histórico de cotações", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ROTULOS.forEach { (id, r) -> pill(r, serie == id) { serie = id } }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(3, 6, 12).forEach { m -> pill("$m m", meses == m) { meses = m } }
                }
                if (pts.isEmpty()) {
                    Text("Ainda sem cotações registradas.", style = MaterialTheme.typography.bodySmall, color = muted)
                } else {
                    val vals = pts.map { it.second }
                    Canvas(Modifier.fillMaxWidth().height(160.dp)) {
                        val todos = vals + listOfNotNull(media?.second)
                        var mn = todos.min(); var mx = todos.max()
                        val folga = ((mx - mn) * 0.08).takeIf { it > 0 } ?: (mx * 0.02).coerceAtLeast(1.0)
                        mn -= folga; mx += folga
                        val fim = hoje().toFloat()
                        val ini = fim - (meses * 30.44f)
                        fun x(d: Long) = ((d - ini) / (fim - ini)) * size.width
                        fun y(v: Double) = ((1 - (v - mn) / (mx - mn)) * size.height).toFloat()
                        for (k in 0..4) {
                            val yy = size.height * k / 4f
                            drawLine(grade, Offset(0f, yy), Offset(size.width, yy), strokeWidth = 1f)
                        }
                        media?.let { (_, v) ->
                            drawLine(laranja, Offset(0f, y(v)), Offset(size.width, y(v)), strokeWidth = 1.4.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)))
                        }
                        val path = Path()
                        pts.forEachIndexed { i, (d, v) -> if (i == 0) path.moveTo(x(d), y(v)) else path.lineTo(x(d), y(v)) }
                        drawPath(path, verde, style = Stroke(width = 2.dp.toPx()))
                        val (ud, uv) = pts.last()
                        drawCircle(verde, radius = 3.5.dp.toPx(), center = Offset(x(ud), y(uv)))
                    }
                    if (media != null) Text("- - Sua média ${media.first} · R$ ${brl(media.second)}", style = MaterialTheme.typography.labelSmall, color = laranja)
                    val fmt: (Double) -> String = { if (dolar) "R$ ${brl(it, 4)}" else "R$ ${brl(it)}" }
                    val variacao = if (pts.size > 1 && !parcial) (vals.last() - vals.first()) / vals.first() * 100 else null
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        listOf("Mín" to fmt(vals.min()), "Máx" to fmt(vals.max())).forEach { (r, v) ->
                            Column {
                                Text(r, style = MaterialTheme.typography.labelSmall, color = muted)
                                Text(v, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        Column {
                            Text("$meses m", style = MaterialTheme.typography.labelSmall, color = muted)
                            Text(
                                variacao?.let { (if (it >= 0) "+" else "−") + brl(kotlin.math.abs(it), 1) + "%" } ?: "—",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = when {
                                    variacao == null -> MaterialTheme.colorScheme.onSurface
                                    variacao >= 0 -> verde
                                    else -> laranja
                                },
                            )
                        }
                    }
                    Text(
                        (if (parcial && inicio != null) "Histórico começando em ${dmDia(inicio)}. " else "") +
                            if (dolar) "Fonte: Banco Central (PTAX de fechamento)" else "Fonte: Grão Direto (registrado pelo BRAgro a cada atualização)",
                        style = MaterialTheme.typography.labelSmall,
                        color = muted,
                    )
                }
            }
        },
    )
}
