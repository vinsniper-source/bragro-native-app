package com.bragro.mobile.ui.domain

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.Agriculture
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.bragro.mobile.ui.i18n.Text
import com.bragro.mobile.ui.i18n.Idioma
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bragro.mobile.data.model.ColumnConfig
import com.bragro.mobile.ui.theme.BrOrange
import java.text.NumberFormat
import java.util.Locale

/*
 * Painel de monitoramento + faixas "PAINEL / AÇÕES / LANÇAMENTOS" -- espelho
 * da plataforma (module-panel.tsx / module-panel.ts / acoes-bloco.tsx),
 * adaptado ao celular: indicadores em 2 colunas, barras uma embaixo da
 * outra, faixa com título preto/branco, linha e setinha no mesmo tom (sem
 * fundo verde), borda verde contornando o bloco inteiro. Nada vermelho:
 * fora da meta = laranja, dentro = verde.
 */

data class PainelKpi(
    val label: String,
    val valor: Double,
    val formato: String, // "moeda" | "numero" | "pct"
    val unidade: String? = null,
    val sub: String,
    val destino: String,
    val tom: String? = null, // "acima" (laranja) | "abaixo" (verde)
    /** Filtro aplicado na lista ao tocar no indicador (coluna -> valor). Vazio = só mostra os lançamentos. */
    val filtro: Map<String, String> = emptyMap(),
)

data class PainelBarra(
    val label: String,
    val pct: Double,
    val detalhe: String,
    /** Participação (não é progresso) -- barra cinza, sem escala laranja→verde. */
    val neutra: Boolean = false,
)

data class PainelModulo(
    val titulo: String,
    val subtitulo: String,
    val kpis: List<PainelKpi>,
    val barras: List<PainelBarra>,
)

private val MEDIDA_PREFERIDA = mapOf(
    "safra" to "realizado",
    "romaneios" to "valorFreteR",
    "frota" to "total",
    "estoque" to "entrada",
    "colheita" to "colhida",
)
val STATUS_CONCLUIDO = setOf(
    "PAGO", "RECEBIDO", "FINALIZADO", "CONCLUIDO", "CONCLUÍDO", "ENTREGUE", "ATENDIDO", "OK", "APROVADO",
    "CONFERIDO", "CONTROLADO", "RETIRADO", "FATURADO", "VALIDO", "VÁLIDO", "EM DIA",
)
private val RECEITA_OPS = setOf("VENDA", "RECEBIMENTO", "RESGATE", "ESTORNO", "DEVOLUÇÃO", "DEVOLUCAO")

fun painelNum(v: String?): Double {
    if (v.isNullOrBlank()) return 0.0
    return v.trim().toDoubleOrNull() ?: v.trim().replace(".", "").replace(",", ".").toDoubleOrNull() ?: 0.0
}

private fun pctDe(a: Double, b: Double): Double = if (b > 0) Math.round(a / b * 1000) / 10.0 else 0.0

private fun topo(rows: List<Map<String, String?>>, key: String, medida: String?): Pair<String, Double>? {
    val m = LinkedHashMap<String, Double>()
    for (r in rows) {
        val k = r[key]?.trim().orEmpty()
        if (k.isEmpty()) continue
        m[k] = (m[k] ?: 0.0) + (if (medida != null) painelNum(r[medida]) else 1.0)
    }
    return m.maxByOrNull { it.value }?.toPair()
}

private fun plural(n: Int, s: String, p: String = s + "s") = if (n == 1) "$n $s" else "$n $p"

/** Mesma lógica de getPainelModulo (plataforma), calculada aqui com os registros já carregados. */
fun calcularPainelModulo(
    domainId: String,
    label: String,
    columns: List<ColumnConfig>,
    records: List<Map<String, String?>>,
): PainelModulo? {
    if (records.isEmpty()) return null
    val tem = { k: String -> columns.any { it.key == k } }
    val medidaKey = MEDIDA_PREFERIDA[domainId]?.takeIf { tem(it) }
        ?: columns.firstOrNull { !it.computed && it.type == "number" && it.money }?.key
    val medidaCol = medidaKey?.let { k -> columns.firstOrNull { it.key == k } }
    val localKey = when {
        tem("local") -> "local"
        tem("fazenda") -> "fazenda"
        else -> null
    }

    // Clima: chuva e temperatura (mesmo painel da tela Clima da plataforma).
    if (domainId == "clima" && tem("chuvaMm")) {
        val chuva = records.sumOf { painelNum(it["chuvaMm"]) }
        val dias = records.count { painelNum(it["chuvaMm"]) > 0 }
        val maxs = records.mapNotNull { it["tempMaxC"]?.toDoubleOrNull() }
        val mins = records.mapNotNull { it["tempMinC"]?.toDoubleOrNull() }
        val fazKey = if (tem("fazenda")) "fazenda" else localKey
        val topFaz = fazKey?.let { topo(records, it, "chuvaMm") }
        return PainelModulo(
            titulo = label,
            subtitulo = plural(records.size, "registro"),
            kpis = listOf(
                PainelKpi("Chuva acumulada", Math.round(chuva).toDouble(), "numero", "mm", "todos os registros", "Lançamentos"),
                PainelKpi("Dias com chuva", dias.toDouble(), "numero", null, "de ${records.size} registros", "Lançamentos"),
                PainelKpi("Temp. máxima", maxs.maxOrNull() ?: 0.0, "numero", "°C", "maior registrada", "Lançamentos"),
                PainelKpi("Temp. mínima", mins.minOrNull() ?: 0.0, "numero", "°C", "menor registrada", "Lançamentos"),
            ),
            barras = listOfNotNull(
                PainelBarra("Dias com chuva", pctDe(dias.toDouble(), records.size.toDouble()), "$dias/${records.size}", neutra = true),
                topFaz?.let { PainelBarra("Chuva · ${it.first}", pctDe(it.second, chuva), "${Math.round(it.second)} mm do total", neutra = true) },
            ),
        )
    }

    // Safra atual = a que concentra os lançamentos (empate: a mais recente).
    val safraAtual = if (tem("safra")) {
        records.mapNotNull { it["safra"]?.takeIf { s -> s.isNotBlank() } }
            .groupingBy { it }.eachCount().entries
            .sortedWith(compareBy<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .lastOrNull()?.key
    } else null
    val daSafra = if (safraAtual != null) records.filter { it["safra"] == safraAtual } else records
    val filtroSafra = if (safraAtual != null) mapOf("safra" to safraAtual) else emptyMap()
    val sufixoSafra = if (safraAtual != null) " · Safra $safraAtual" else ""
    val titulo = if (safraAtual != null) {
        if (label.uppercase() == "SAFRA") "Safra $safraAtual" else "$label · Safra $safraAtual"
    } else label
    val fazendas = localKey?.let { k -> daSafra.mapNotNull { it[k]?.takeIf { s -> s.isNotBlank() } }.toSet().size } ?: 0

    if (domainId == "financeiro") {
        // Datas ISO (aaaa-mm-dd) comparadas como texto -- sem java.time (minSdk 24).
        val iso = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = java.util.Calendar.getInstance()
        val hoje = iso.format(cal.time)
        cal.add(java.util.Calendar.DAY_OF_YEAR, 30)
        val em30 = iso.format(cal.time)
        fun isReceita(r: Map<String, String?>) = r["operacao"].orEmpty().uppercase() in RECEITA_OPS
        fun valor(r: Map<String, String?>) = painelNum(r["bruto"])
        fun pago(r: Map<String, String?>) = !r["dataPgto"].isNullOrBlank() || r["status"].orEmpty().uppercase() == "PAGO"
        fun vcto(r: Map<String, String?>) = r["vcto"]?.take(10)?.takeIf { it.length == 10 }
        val despesas = daSafra.filterNot(::isReceita)
        val receitas = daSafra.filter(::isReceita)
        val totalDesp = despesas.sumOf(::valor)
        val pagas = despesas.filter(::pago)
        val valorPago = pagas.sumOf(::valor)
        val abertas = records.filter { !isReceita(it) && !pago(it) }
        val aVencer = abertas.filter { val d = vcto(it); d != null && d >= hoje && d <= em30 }
        val vencidas = abertas.filter { val d = vcto(it); d != null && d < hoje }
        val totalRec = receitas.sumOf(::valor)
        return PainelModulo(
            titulo = titulo,
            subtitulo = "${plural(fazendas, "fazenda")} · ${plural(despesas.size, "despesa")} · ${plural(receitas.size, "receita")}",
            kpis = listOf(
                PainelKpi("Despesa da safra", totalDesp, "moeda", null, plural(despesas.size, "lançamento"), "Lançamentos$sufixoSafra", filtro = filtroSafra),
                PainelKpi("Pago", valorPago, "moeda", null, "${fmtPct(pctDe(valorPago, totalDesp))} da despesa", "Já pago/recebido", filtro = filtroSafra),
                PainelKpi("A vencer (30 dias)", aVencer.sumOf(::valor), "moeda", null, plural(aVencer.size, "parcela"), "Contas a pagar"),
                PainelKpi(
                    "Vencidas", vencidas.size.toDouble(), "numero", null,
                    if (vencidas.isEmpty()) "nenhuma" else fmtMoeda(vencidas.sumOf(::valor)),
                    "Contas a pagar", tom = if (vencidas.isNotEmpty()) "acima" else null,
                ),
                PainelKpi("Receita da safra", totalRec, "moeda", null, plural(receitas.size, "lançamento"), "Contas a receber", filtro = filtroSafra),
            ),
            barras = listOf(
                PainelBarra("Despesas pagas", pctDe(pagas.size.toDouble(), despesas.size.toDouble()), "${pagas.size}/${despesas.size}"),
                PainelBarra(
                    "Valor pago / despesa", pctDe(valorPago, totalDesp),
                    "${Math.round(valorPago / 1000)} mil / ${Math.round(totalDesp / 1000)} mil",
                ),
            ),
        )
    }

    val unidade = if (medidaCol != null && !medidaCol.money) (if (medidaKey == "colhida") "kg" else "") else null
    val total = medidaKey?.let { k -> daSafra.sumOf { painelNum(it[k]) } } ?: 0.0
    val topFaz = localKey?.let { topo(daSafra, it, medidaKey) }
    val topCat = if (tem("categoria")) topo(daSafra, "categoria", medidaKey) else null
    val comStatus = tem("status")
    val concluidos = if (comStatus) daSafra.count { it["status"].orEmpty().uppercase() in STATUS_CONCLUIDO } else 0
    val pendRows = if (comStatus) daSafra.filter { !it["status"].isNullOrBlank() && it["status"]!!.uppercase() !in STATUS_CONCLUIDO } else emptyList()

    val kpis = mutableListOf(
        PainelKpi("Lançamentos", daSafra.size.toDouble(), "numero", null, safraAtual?.let { "safra $it" } ?: "no total", "Lançamentos$sufixoSafra", filtro = filtroSafra),
    )
    if (medidaKey != null && medidaCol != null) {
        kpis += PainelKpi("Total · ${medidaCol.label}", total, if (medidaCol.money) "moeda" else "numero", unidade, safraAtual?.let { "safra $it" } ?: "no total", "Lançamentos$sufixoSafra", filtro = filtroSafra)
    }
    if (topFaz != null && localKey != null) {
        kpis += PainelKpi(
            "Fazenda com mais", topFaz.second, if (medidaCol?.money == true) "moeda" else "numero",
            if (medidaKey != null) unidade else "lanç.", topFaz.first, "Ver lançamentos da fazenda",
            filtro = filtroSafra + (localKey to topFaz.first),
        )
    }
    if (topCat != null) {
        kpis += PainelKpi(
            "Categoria com mais", topCat.second, if (medidaCol?.money == true) "moeda" else "numero",
            if (medidaKey != null) unidade else "lanç.", topCat.first, "Ver lançamentos da categoria",
            filtro = filtroSafra + ("categoria" to topCat.first),
        )
    }
    if (comStatus) {
        val topPend = topo(pendRows, "status", null)
        kpis += PainelKpi(
            "Pendentes", pendRows.size.toDouble(), "numero", null,
            if (pendRows.isNotEmpty()) "não concluídos${topPend?.let { " · ${it.first.lowercase()}" } ?: ""}" else "tudo concluído",
            topPend?.let { "Ver ${it.first.lowercase()}" } ?: "Lançamentos$sufixoSafra",
            tom = if (pendRows.isNotEmpty()) "acima" else null,
            filtro = filtroSafra + (topPend?.let { mapOf("status" to it.first) } ?: emptyMap()),
        )
    }
    val barras = mutableListOf<PainelBarra>()
    if (comStatus && daSafra.isNotEmpty()) {
        barras += PainelBarra("Concluídos", pctDe(concluidos.toDouble(), daSafra.size.toDouble()), "$concluidos/${daSafra.size}")
    }
    if (topFaz != null && total > 0 && medidaKey != null) {
        barras += PainelBarra("Participação de ${topFaz.first}", pctDe(topFaz.second, total), "do total", neutra = true)
    }
    return PainelModulo(
        titulo = titulo,
        subtitulo = listOfNotNull(if (fazendas > 0) plural(fazendas, "fazenda") else null, plural(daSafra.size, "lançamento")).joinToString(" · "),
        kpis = kpis.take(5),
        barras = barras.take(2),
    )
}

/** Fatia filtrada mostrada como barra extra quando um indicador abriu um filtro. */
fun barraFiltroAtivo(records: List<Map<String, String?>>, filtro: Map<String, String>): PainelBarra? {
    val extras = filtro.filterKeys { it != "safra" }.filterValues { it.isNotBlank() }
    if (extras.isEmpty() || records.isEmpty()) return null
    val base = filtro["safra"]?.let { s -> records.filter { it["safra"] == s } } ?: records
    if (base.isEmpty()) return null
    val fat = base.filter { r -> extras.all { (k, v) -> r[k].orEmpty().equals(v, ignoreCase = true) } }
    return PainelBarra("Filtro ativo: ${extras.values.joinToString(" · ")}", pctDe(fat.size.toDouble(), base.size.toDouble()), "${fat.size}/${base.size}", neutra = true)
}

private val moedaFmt: NumberFormat = NumberFormat.getCurrencyInstance(Locale("pt", "BR")).apply { maximumFractionDigits = 0 }
fun fmtMoeda(v: Double): String = moedaFmt.format(v)
fun fmtPct(v: Double): String = "${NumberFormat.getNumberInstance(Locale("pt", "BR")).apply { maximumFractionDigits = 1 }.format(v)}%"
fun fmtKpi(k: PainelKpi): String = when (k.formato) {
    "moeda" -> fmtMoeda(k.valor)
    "pct" -> fmtPct(k.valor)
    else -> NumberFormat.getNumberInstance(Locale("pt", "BR")).apply { maximumFractionDigits = 0 }.format(k.valor) +
        (if (!k.unidade.isNullOrBlank()) " ${k.unidade}" else "")
}

/** Verde (bom) / laranja (atenção) -- nada vermelho. */
@Composable
fun corSucesso(): Color = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFF6FBF7F) else Color(0xFF2E7D32)

@Composable
fun corAtencao(): Color = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFFE5A35A) else BrOrange

/** Cor da barra conforme o progresso (corProgresso da plataforma): até 33% laranja, até 66% âmbar, até 99% verde-claro, 100% verde. */
@Composable
fun corProgressoBarra(pct: Double): Color = when {
    pct > 100 -> corAtencao()
    pct >= 100 -> progressGreen()
    pct >= 67 -> Color(0xFF7CB342)
    pct >= 34 -> Color(0xFFF2B33D)
    else -> corAtencao()
}

/** Fundo verde-claro dos indicadores (kpi-verde). */
@Composable
fun kpiVerdeFundo(): Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f).compositeOver(MaterialTheme.colorScheme.surface)

/**
 * Bloco com faixa de título (Painel / Ações / seções): título em preto/branco,
 * linha e setinha no mesmo tom, borda verde contornando o bloco inteiro.
 */
@Composable
fun BlocoComFaixa(
    titulo: String,
    modifier: Modifier = Modifier,
    inicialAberto: Boolean = true,
    content: @Composable () -> Unit,
) {
    var aberto by rememberSaveable(titulo) { mutableStateOf(inicialAberto) }
    val borda = MaterialTheme.colorScheme.primary
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(2.dp, borda, RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface),
    ) {
        FaixaTitulo(titulo, aberto) { aberto = !aberto }
        if (aberto) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            content()
        }
    }
}

/** Linha "TÍTULO ——— v" usada dentro do BlocoComFaixa. */
@Composable
fun FaixaTitulo(titulo: String, aberto: Boolean, extra: String? = null, onToggle: () -> Unit) {
    val fg = MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onToggle() }.padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(Idioma.tr(titulo).uppercase(), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.8.sp, color = fg)
        if (extra != null) Text(extra, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Box(Modifier.weight(1f).height(1.dp).background(fg.copy(alpha = 0.22f)))
        Box(
            modifier = Modifier.size(24.dp).border(1.dp, fg.copy(alpha = 0.35f), RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.ExpandMore, contentDescription = if (aberto) "Recolher" else "Expandir", tint = fg, modifier = Modifier.size(16.dp).rotate(if (aberto) 0f else -90f))
        }
    }
}

/** Divisória "LANÇAMENTOS  n ——— v" (sem borda de bloco), igual à plataforma. */
@Composable
fun CabecalhoLancamentos(total: Int, aberto: Boolean, extra: String? = null, titulo: String = "Lançamentos", rotulo: String? = null, onToggle: () -> Unit) {
    val fg = MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onToggle() }.padding(horizontal = 2.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(Idioma.tr(titulo).uppercase(), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.8.sp, color = fg)
        Text(
            rotulo ?: (NumberFormat.getIntegerInstance(Locale("pt", "BR")).format(total) + (extra?.let { " · $it" } ?: "")),
            fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
        )
        Box(Modifier.weight(1f).height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
        Box(
            modifier = Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.ExpandMore, contentDescription = if (aberto) "Recolher" else "Expandir", tint = fg, modifier = Modifier.size(18.dp).rotate(if (aberto) 0f else -90f))
        }
    }
}

/** Painel de monitoramento (dentro de um BlocoComFaixa "Painel de monitoramento"). */
@Composable
fun PainelMonitoramento(
    painel: PainelModulo,
    modifier: Modifier = Modifier,
    kpiAtivo: String? = null,
    onKpi: ((PainelKpi) -> Unit)? = null,
) {
    BlocoComFaixa("Painel de monitoramento", modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Agriculture, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) }
                Column {
                    Text(Idioma.tr(painel.titulo).uppercase(), fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(painel.subtitulo, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            painel.kpis.chunked(2).forEach { par ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    par.forEach { k -> KpiTile(k, ativo = kpiAtivo == k.label, onClick = onKpi?.let { f -> { f(k) } }, modifier = Modifier.weight(1f)) }
                    if (par.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        if (painel.barras.isNotEmpty()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                painel.barras.forEach { BarraPainel(it) }
            }
        }
    }
}

@Composable
fun KpiTile(k: PainelKpi, ativo: Boolean = false, onClick: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val cor = when (k.tom) {
        "acima" -> corAtencao()
        "abaixo" -> corSucesso()
        else -> MaterialTheme.colorScheme.onSurface
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(kpiVerdeFundo())
            .border(if (ativo) 2.dp else 1.dp, if (ativo) primary else primary.copy(alpha = 0.30f), RoundedCornerShape(12.dp))
            .let { if (onClick != null) it.clickable { onClick() } else it }
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(k.label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(end = 14.dp))
            Text(fmtKpi(k), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = cor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(k.sub, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (onClick != null) {
                HorizontalDivider(modifier = Modifier.padding(top = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Text(k.destino, fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (onClick != null) {
            Icon(
                Icons.Filled.NorthEast, contentDescription = "Abrir: ${k.destino}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(14.dp).align(Alignment.TopEnd),
            )
        }
    }
}

@Composable
fun BarraPainel(b: PainelBarra) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(b.label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Text("${b.detalhe} · ${fmtPct(b.pct)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        val fracao = (b.pct / 100.0).coerceIn(0.01, 1.0).toFloat()
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f))) {
            Box(
                Modifier.fillMaxWidth(fracao).height(8.dp).clip(RoundedCornerShape(4.dp))
                    .background(if (b.neutra) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f) else corProgressoBarra(b.pct)),
            )
        }
    }
}

/*
 * Cards unificados por atividade (chaveAtividade da plataforma): lançamentos
 * com a mesma chave viram um cartão de grupo com os totais; tocando nele os
 * lançamentos aparecem logo abaixo, cada um com editar/excluir.
 * Financeiro e Pedidos têm agrupamento próprio e ficam de fora aqui.
 */
fun chaveAtividade(domainId: String, r: Map<String, String?>): String? {
    fun k(vararg p: String?): String? {
        val partes = p.map { it?.trim()?.uppercase().orEmpty() }
        return if (partes.all { it.isEmpty() }) null else partes.joinToString("|")
    }
    fun ou(a: String?, b: String?) = if (!a.isNullOrBlank()) a else b
    return when (domainId) {
        "safra" -> k(r["operacao"])
        "colheita" -> k(r["cultura"], r["local"], r["safra"])
        "frota" -> k(r["frota"])
        "estoque" -> k(r["categoria"])
        "romaneios" -> k(ou(r["noContrato"], r["compradorDestino"]))
        "pecuaria" -> k(r["operacao"])
        "caixainterno", "controleinterno", "inventario" -> k(r["categoria"])
        "clima", "pragas" -> k(r["fazenda"], r["talhao"])
        "pastagem" -> k(r["local"], r["piquete"])
        "cotacoesfornecedores" -> k(r["categoria"], r["item"])
        "maodeobra" -> k(ou(r["categoriaAtividade"], r["tarefa"]))
        "contratos" -> k(ou(r["tipo"], r["categoria"]))
        "receituarios" -> k(r["produto"])
        else -> null
    }
}

/** Título legível da chave ("PLANTIO|SOJA" -> "Plantio · Soja"). */
fun tituloChave(chave: String): String = chave.split("|").filter { it.isNotBlank() }
    .joinToString(" · ") { s -> s.lowercase().replaceFirstChar { it.uppercase() } }

/** Medida somada no cartão de grupo (mesma do painel). */
fun medidaDoModulo(domainId: String, columns: List<ColumnConfig>): ColumnConfig? {
    val pref = MEDIDA_PREFERIDA[domainId]
    return columns.firstOrNull { it.key == pref }
        ?: columns.firstOrNull { !it.computed && it.type == "number" && it.money }
}

@Composable
fun CartaoGrupo(
    titulo: String,
    qtd: Int,
    onde: String?,
    total: String?,
    totalRotulo: String?,
    aberto: Boolean,
    onToggle: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, primary.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
            .clickable { onToggle() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(Idioma.tr(plural(qtd, "lançamento")).uppercase(), fontSize = 10.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(titulo, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!onde.isNullOrBlank()) Text(onde, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (!total.isNullOrBlank()) {
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(horizontal = 8.dp)) {
                Text(total, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
                if (!totalRotulo.isNullOrBlank()) Text(totalRotulo, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        Icon(Icons.Filled.ExpandMore, contentDescription = if (aberto) "Recolher grupo" else "Ver lançamentos do grupo", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.rotate(if (aberto) 180f else 0f))
    }
}
