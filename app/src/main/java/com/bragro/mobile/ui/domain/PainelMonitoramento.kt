package com.bragro.mobile.ui.domain

import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Print
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
import androidx.compose.ui.draw.drawWithContent
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
    /** Lançamentos com algo a corrigir (Conferencia.kt). Quando informado,
     * "Pendentes" passa a ser a conferência de cadastro (v11), não status. */
    pendentesConferencia: Int? = null,
): PainelModulo? {
    // Sem lançamentos: o painel aparece mesmo assim, zerado (padrão v7 --
    // todo módulo abre com Painel + Ações).
    if (records.isEmpty()) return PainelModulo(
        titulo = label,
        subtitulo = "nenhum lançamento ainda",
        kpis = listOf(PainelKpi("Lançamentos", 0.0, "numero", null, "no total", "Lançamentos")) +
            (columns.firstOrNull { !it.computed && it.type == "number" && it.money }?.let { listOf(PainelKpi(it.label, 0.0, "moeda", null, "no total", "Lançamentos")) } ?: emptyList()) +
            (if (columns.any { it.key == "status" }) listOf(PainelKpi("Pendentes", 0.0, "numero", null, "nenhum", "Lançamentos")) else emptyList()),
        barras = emptyList(),
    )
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
        kpis += PainelKpi(medidaCol.label, total, if (medidaCol.money) "moeda" else "numero", unidade, if (fazendas > 0) plural(fazendas, "fazenda") else safraAtual?.let { "safra $it" } ?: "no total", "Lançamentos$sufixoSafra", filtro = filtroSafra)
    }
    if (topFaz != null && localKey != null) {
        kpis += PainelKpi(
            topFaz.first, topFaz.second, if (medidaCol?.money == true) "moeda" else "numero",
            if (medidaKey != null) unidade else "lanç.",
            if (total > 0 && medidaKey != null) "${Math.round(pctDe(topFaz.second, total))}% do total" else "fazenda com mais",
            "Ver lançamentos da fazenda",
            filtro = filtroSafra + (localKey to topFaz.first),
        )
    }
    if (topCat != null) {
        kpis += PainelKpi(
            topCat.first, topCat.second, if (medidaCol?.money == true) "moeda" else "numero",
            if (medidaKey != null) unidade else "lanç.", "categoria com mais", "Ver lançamentos da categoria",
            filtro = filtroSafra + ("categoria" to topCat.first),
        )
    }
    if (pendentesConferencia != null) {
        kpis.add(
            minOf(2, kpis.size),
            PainelKpi(
                "Pendentes", pendentesConferencia.toDouble(), "numero", null,
                if (pendentesConferencia > 0) "a corrigir" else "cadastro ok", "Conferir cadastro",
                tom = if (pendentesConferencia > 0) "acima" else null,
            ),
        )
    } else if (comStatus) {
        val topPend = topo(pendRows, "status", null)
        kpis += PainelKpi(
            "Pendentes", pendRows.size.toDouble(), "numero", null,
            if (pendRows.isNotEmpty()) (topPend?.first?.lowercase() ?: "não concluídos") else "tudo concluído",
            topPend?.let { "Ver ${it.first.lowercase()}" } ?: "Lançamentos$sufixoSafra",
            tom = if (pendRows.isNotEmpty()) "acima" else null,
            filtro = filtroSafra + (topPend?.let { mapOf("status" to it.first) } ?: emptyMap()),
        )
    }
    val barras = mutableListOf<PainelBarra>()
    if (comStatus && daSafra.isNotEmpty()) {
        barras += PainelBarra("Concluídos", pctDe(concluidos.toDouble(), daSafra.size.toDouble()), "$concluidos/${daSafra.size}")
    }
    // Participação da fazenda vai no complemento do cartão da fazenda.
    return PainelModulo(
        titulo = titulo,
        subtitulo = listOfNotNull(if (fazendas > 0) plural(fazendas, "fazenda") else null, plural(daSafra.size, "lançamento")).joinToString(" · "),
        kpis = kpis.take(6),
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

// Moeda sempre R$ 00.000,00 (pedido do usuário, app e plataforma).
private val moedaFmt: NumberFormat = NumberFormat.getCurrencyInstance(Locale("pt", "BR")).apply { minimumFractionDigits = 2; maximumFractionDigits = 2 }
fun fmtMoeda(v: Double): String = moedaFmt.format(v)
fun fmtPct(v: Double): String = "${NumberFormat.getNumberInstance(Locale("pt", "BR")).apply { maximumFractionDigits = 1 }.format(v)}%"
/** Número curto pros cartões (R$ 2,42 mi / R$ 846 mil), igual à plataforma. */
fun abrev(v: Double, moeda: Boolean): String {
    val a = Math.abs(v)
    val pre = if (moeda) "R$ " else ""
    val sinal = if (v < 0) "-" else ""
    fun nf(x: Double, d: Int) = NumberFormat.getNumberInstance(Locale("pt", "BR")).apply { maximumFractionDigits = d }.format(x)
    return when {
        a >= 1_000_000 -> "$sinal$pre${nf(a / 1_000_000, 2)} mi"
        a >= 10_000 -> "$sinal$pre${nf(a / 1_000, 0)} mil"
        moeda -> fmtMoeda(v)
        else -> nf(v, 1)
    }
}
fun fmtKpi(k: PainelKpi): String = when (k.formato) {
    "moeda" -> fmtMoeda(k.valor)
    "pct" -> fmtPct(k.valor)
    else -> NumberFormat.getNumberInstance(Locale("pt", "BR")).apply { maximumFractionDigits = 1 }.format(k.valor) +
        (if (!k.unidade.isNullOrBlank()) " ${k.unidade}" else "")
}

/** Valor do cartão sempre numa linha só: a fonte diminui até caber (o
 * cartão tem só 3 linhas -- rótulo, valor e complemento). */
@Composable
fun ValorUmaLinha(texto: String, cor: Color, maxSp: Float = 14.5f, minSp: Float = 9f) {
    var tamanho by androidx.compose.runtime.remember(texto) { mutableStateOf(maxSp) }
    var pronto by androidx.compose.runtime.remember(texto) { mutableStateOf(false) }
    Text(
        texto,
        fontSize = tamanho.sp,
        fontWeight = FontWeight.Bold,
        color = cor,
        maxLines = 1,
        softWrap = false,
        letterSpacing = (-0.2).sp,
        modifier = Modifier.drawWithContent { if (pronto) drawContent() },
        onTextLayout = { r ->
            if (r.hasVisualOverflow && tamanho > minSp) tamanho = (tamanho - 0.5f).coerceAtLeast(minSp) else pronto = true
        },
    )
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
 * Bloco Ações -- v10 "abas encaixadas": uma caixa só, borda cinza fina e
 * cantos de 12dp (iguais aos cartões); as abas (AbasEncaixadas) ficam no
 * topo e os ícones soltos e centralizados embaixo. Sem borda verde e sem
 * setinha (Ações fica sempre à mostra).
 */
@Composable
fun BlocoComFaixa(
    titulo: String,
    modifier: Modifier = Modifier,
    inicialAberto: Boolean = true,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(0.75.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface),
    ) {
        androidx.compose.runtime.CompositionLocalProvider(LocalAcoesEncaixadas provides true) { content() }
    }
}

/**
 * Abas Dados/Operações/Arquivos encaixadas no topo do bloco Ações (v10 A):
 * faixa cinza clara, divisórias finas; a aba ativa fica branca com texto
 * verde e sublinhado verde. Aba com aviso (badge) ganha o amarelo de sempre.
 */
@Composable
fun AbasEncaixadas(titulos: List<String>, selecionada: Int, avisos: List<Int> = emptyList(), onSelect: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val faixa = cs.onSurface.copy(alpha = 0.05f).compositeOver(cs.surface)
    val amarelo = if (cs.background.luminance() < 0.5f) com.bragro.mobile.ui.theme.BrYellow else Color(0xFFB8860B)
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().background(faixa)) {
            titulos.forEachIndexed { i, t ->
                if (i > 0) Box(Modifier.width(1.dp).height(34.dp).background(cs.outlineVariant))
                val ativa = i == selecionada
                val aviso = (avisos.getOrNull(i) ?: 0) > 0
                Box(
                    modifier = Modifier.weight(1f).height(34.dp)
                        // v18 -- aba com aviso: sem fundo amarelo; nome e
                        // traço em amarelo.
                        .background(if (ativa) cs.surface else Color.Transparent)
                        .clickable { onSelect(i) }
                        .drawWithContent {
                            drawContent()
                            if (ativa || aviso) drawRect(if (aviso) amarelo else cs.primary, topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - 2.dp.toPx()), size = androidx.compose.ui.geometry.Size(size.width, 2.dp.toPx()))
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        Idioma.tr(t), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        fontWeight = if (ativa) FontWeight.ExtraBold else FontWeight.SemiBold,
                        color = if (aviso) amarelo else if (ativa) cs.primary else cs.onSurfaceVariant,
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(cs.outlineVariant))
    }
}

/** Setinha de recolher/expandir (fim da linha), mesmo desenho da plataforma. */
@Composable
fun SetaRecolher(aberto: Boolean, modifier: Modifier = Modifier, onToggle: () -> Unit) {
    val fg = MaterialTheme.colorScheme.onSurface
    Box(
        modifier = modifier.size(26.dp).clip(RoundedCornerShape(6.dp)).border(1.dp, fg.copy(alpha = 0.35f), RoundedCornerShape(6.dp)).clickable { onToggle() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.ExpandMore, contentDescription = if (aberto) "Recolher" else "Expandir", tint = fg, modifier = Modifier.size(16.dp).rotate(if (aberto) 0f else -90f))
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
        Box(Modifier.weight(1f).height(1.5.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)))
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
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 160.dp),
        )
        // v14: a linha vai do número até a setinha (encostada na margem),
        // um pouco mais grossa e num cinza um tom mais escuro.
        Box(Modifier.weight(1f).height(1.5.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)))
        Box(
            modifier = Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.ExpandMore, contentDescription = if (aberto) "Recolher" else "Expandir", tint = fg, modifier = Modifier.size(18.dp).rotate(if (aberto) 0f else -90f))
        }
    }
}

/**
 * Painel de monitoramento -- mockup v5: sem faixa de título; linha de
 * identificação com a setinha de recolher no fim; até 6 cartões, 3 por
 * linha, cada um com 3 linhas (rótulo, número, complemento). As barras
 * viram cartões com barrinha.
 */
@Composable
fun PainelMonitoramento(
    painel: PainelModulo,
    modifier: Modifier = Modifier,
    kpiAtivo: String? = null,
    onKpi: ((PainelKpi) -> Unit)? = null,
) {
    var aberto by rememberSaveable(painel.titulo) { mutableStateOf(false) }
    val cartoes: List<Any> = (painel.kpis.take(2) + painel.barras + painel.kpis.drop(2)).take(6)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(0.75.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { aberto = !aberto }.padding(start = 10.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Agriculture, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) }
            Column(modifier = Modifier.weight(1f)) {
                Text(Idioma.tr(painel.titulo).uppercase(), fontWeight = FontWeight.Bold, fontSize = 13.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(painel.subtitulo, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            SetaRecolher(aberto) { aberto = !aberto }
        }
        if (aberto && cartoes.isNotEmpty()) {
            Column(modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                cartoes.chunked(3).forEach { linha ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        linha.forEach { c ->
                            when (c) {
                                is PainelKpi -> KpiTile(c, ativo = kpiAtivo == c.label, onClick = onKpi?.let { f -> { f(c) } }, modifier = Modifier.weight(1f))
                                is PainelBarra -> BarraTile(c, modifier = Modifier.weight(1f))
                            }
                        }
                        repeat(3 - linha.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

/** Barra virou cartão (v15): nome à esquerda, quantidade/detalhe com a
 * barrinha no meio, percentual à direita. Borda cinza. */
@Composable
fun BarraTile(b: PainelBarra, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(kpiVerdeFundo())
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Text(b.label, fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(b.detalhe, fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            val fracao = (b.pct / 100.0).coerceIn(0.02, 1.0).toFloat()
            Box(Modifier.width(34.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f))) {
                Box(Modifier.fillMaxWidth(fracao).height(5.dp).background(if (b.neutra) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f) else corProgressoBarra(b.pct)))
            }
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Text(fmtPct(b.pct), fontSize = 14.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Cartão do painel (v15): nome à esquerda, quantidade/complemento no meio,
 * valor à direita. Borda cinza (o cartão ativo fica com borda verde). */
@Composable
fun KpiTile(k: PainelKpi, ativo: Boolean = false, onClick: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val cor = when (k.tom) {
        "acima" -> corAtencao()
        "abaixo" -> corSucesso()
        else -> MaterialTheme.colorScheme.onSurface
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(kpiVerdeFundo())
            .border(if (ativo) 2.dp else 1.dp, if (ativo) primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
            .let { if (onClick != null) it.clickable { onClick() } else it }
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Text(k.label, fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
        Text(k.sub, fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) { ValorUmaLinha(fmtKpi(k), cor) }
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
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .clickable { onToggle() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // v13: nome à esquerda, quantidade no meio, valor à direita. O valor
        // tem largura fixa e a fonte diminui até caber -- nunca passa do card.
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(titulo, fontWeight = FontWeight.Bold, fontSize = 14.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!onde.isNullOrBlank()) Text(onde, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(46.dp)) {
            Text(qtd.toString(), fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
            Text("lanç.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(104.dp).padding(end = 4.dp)) {
            ValorUmaLinha(total ?: "—", MaterialTheme.colorScheme.onSurface, maxSp = 14f, minSp = 8.5f)
            if (!totalRotulo.isNullOrBlank()) Text(totalRotulo, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Filled.ExpandMore, contentDescription = if (aberto) "Recolher grupo" else "Ver lançamentos do grupo", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.rotate(if (aberto) 180f else 0f))
    }
}

/**
 * Barras horizontais com o nome de cada item à esquerda (um embaixo do
 * outro) e o valor à direita -- "Saldo por item" do Controle de Insumos
 * (pedido do usuário: nome do item na vertical, do lado esquerdo da barra).
 */
@Composable
fun BarrasHorizontais(itens: List<Pair<String, Double>>, formatar: (Double) -> String) {
    val max = itens.maxOfOrNull { kotlin.math.abs(it.second) }?.takeIf { it > 0 } ?: 1.0
    val primary = MaterialTheme.colorScheme.primary
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        itens.forEach { (nome, v) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(nome, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(110.dp))
                Box(Modifier.weight(1f).height(12.dp)) {
                    val fr = (kotlin.math.abs(v) / max).coerceIn(0.02, 1.0).toFloat()
                    Box(Modifier.fillMaxWidth(fr).height(12.dp).clip(RoundedCornerShape(3.dp)).background(if (v < 0) corAtencao() else primary))
                }
                Text(formatar(v), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

/**
 * Ações em grupos com legenda (CONSULTAR / FERRAMENTAS / EXIBIR / LANÇAR),
 * como a barra da plataforma -- cada grupo divide a largura igualmente,
 * separado por um fio; ícones centralizados.
 */
@Composable
fun LinhaGruposAcoes(grupos: List<Pair<String, @Composable () -> Unit>>) {
    val linha = MaterialTheme.colorScheme.outlineVariant
    Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
        grupos.forEachIndexed { i, (titulo, conteudo) ->
            if (i > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(linha))
            Column(Modifier.weight(1f).padding(top = 6.dp, bottom = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(Idioma.tr(titulo).uppercase(), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) { conteudo() }
            }
        }
    }
}

/**
 * Ações em abas Dados / Operações / Arquivos (v14) -- mesmo padrão dos
 * módulos de lançamento, para as telas próprias (Drone, Pivôs, Talhões e
 * Mapas, Prescrição, NF-e, Silos, Reconciliação). Ícones de cada aba em
 * células iguais e centralizadas.
 */
@Composable
fun AcoesEmAbas(
    dados: @Composable () -> Unit,
    operacoes: @Composable () -> Unit,
    arquivos: @Composable () -> Unit,
    /** v20 -- contagem de avisos por aba (Dados, Operações, Arquivos): aba
     * com aviso fica com nome e traço amarelos. */
    avisos: List<Int> = emptyList(),
    titulos: List<String> = listOf("Dados", "Operações", "Arquivos"),
) {
    var aba by rememberSaveable { mutableStateOf(0) }
    BlocoComFaixa("Ações") {
        Column(Modifier.fillMaxWidth()) {
            AbasEncaixadas(titulos, aba, avisos = avisos, onSelect = { aba = it })
            Spacer(Modifier.height(4.dp))
            EqualWidthBlockRow(modifier = Modifier.fillMaxWidth()) {
                when (aba) {
                    0 -> dados()
                    1 -> operacoes()
                    else -> arquivos()
                }
            }
        }
    }
}

/** Ícones da aba Arquivos: Excel e Imprimir/PDF a partir de uma tabela simples. */
@Composable
fun ArquivosTabela(titulo: String, headers: List<String>, nuvem: Boolean = false, linhas: () -> List<List<String>>) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // v19 -- "Imprimir/PDF" desmembrado; ordem Imprimir · PDF · Excel · Nuvem.
    LabeledIconButton(
        icon = androidx.compose.material.icons.Icons.Filled.Print,
        label = "Imprimir",
        onClick = { com.bragro.mobile.ui.print.HtmlPrinter.printSimpleTable(context, titulo, headers, linhas()) },
    )
    LabeledIconButton(
        icon = androidx.compose.material.icons.Icons.Filled.PictureAsPdf,
        label = "PDF",
        onClick = { com.bragro.mobile.ui.print.HtmlPrinter.exportSimpleTablePdf(context, titulo, headers, linhas()) },
    )
    LabeledIconButton(
        icon = androidx.compose.material.icons.Icons.Filled.GridOn,
        label = "Excel",
        onClick = {
            val nome = titulo.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-') + ".xlsx"
            com.bragro.mobile.ui.util.shareBinaryFile(context, com.bragro.mobile.data.export.XlsxWriter.buildXlsx(headers, linhas()), nome)
        },
    )
    if (nuvem) NuvemButton()
}

/** v19 -- botão Nuvem (estado da sincronização) usado nos blocos Ações. */
@Composable
fun NuvemButton() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val online = com.bragro.mobile.data.NetworkStatus.isOnline(context)
    LabeledIconButton(
        icon = if (online) androidx.compose.material.icons.Icons.Filled.Cloud else androidx.compose.material.icons.Icons.Filled.CloudOff,
        label = "Nuvem",
        onClick = {
            val msg = if (!com.bragro.mobile.data.NetworkStatus.isOnline(context)) com.bragro.mobile.data.NetworkStatus.failureMessage(context) else "Conectado -- dados sincronizados com o servidor."
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
        },
    )
}

/** v19 -- faixa "Ações" simples (sem abas) para telas de formulário, QR e
 * Simulador: recebe os botões que antes ficavam no cabeçalho. */
@Composable
fun AcoesFaixa(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        BlocoComFaixa("Ações") {
            EqualWidthBlockRow(modifier = Modifier.fillMaxWidth()) { content() }
        }
    }
}

/** v20 -- ícones sem rótulo alinhados à direita, no topo dos formulários
 * (Copiar último etc.), no lugar da faixa Ações. */
@Composable
fun AcoesIcones(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(LocalSomenteIcone provides true) { content() }
    }
}
