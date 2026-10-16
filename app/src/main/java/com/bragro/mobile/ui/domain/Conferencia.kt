package com.bragro.mobile.ui.domain

import com.bragro.mobile.data.local.FarmEntity
import com.bragro.mobile.data.local.LookupEntity
import com.bragro.mobile.data.model.ColumnConfig
import com.bragro.mobile.data.model.DomainConfig
import androidx.compose.foundation.background
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bragro.mobile.ui.i18n.Text
import java.text.Normalizer
import java.text.NumberFormat
import java.util.Locale

/*
 * Conferência de cadastro -- o "Pendentes" do painel (v11): não é status
 * (a pagar / em andamento), é só o que está errado no preenchimento:
 * campo obrigatório vazio, valor fora da lista suspensa, grafia diferente
 * da lista (minúscula / sem acento / acento errado) e números que não batem.
 * Espelho de lib/domains/conferencia.ts da plataforma.
 */
data class ProblemaCampo(
    val campo: String,
    val rotulo: String,
    val tipo: String, // vazio | lista | grafia | divergencia
    val atual: String? = null,
    val sugestao: String? = null,
    val mensagem: String,
)

val ROTULO_PROBLEMA = mapOf("vazio" to "Campo vazio", "lista" to "Fora da lista", "grafia" to "Acento / maiúscula", "divergencia" to "Divergência")

private val MARCAS = Regex("\\p{M}+")
private val ESPACOS = Regex("\\s+")
fun normalizarTexto(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFD).replace(MARCAS, "").replace(ESPACOS, " ").trim().uppercase(Locale.ROOT)

private fun distancia(a: String, b: String): Int {
    if (a == b) return 0
    if (kotlin.math.abs(a.length - b.length) > 2) return 99
    var prev = IntArray(b.length + 1) { it }
    for (i in 1..a.length) {
        val cur = IntArray(b.length + 1)
        cur[0] = i
        for (j in 1..b.length) cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
        prev = cur
    }
    return prev[b.length]
}

private val TRIOS = listOf(Triple("qtd", "unitario", "total"), Triple("quantidade", "precoUnitario", "precoTotal"), Triple("qtd", "valorUnitarioR", "valorTotalR"))
private val nf = NumberFormat.getNumberInstance(Locale("pt", "BR")).apply { maximumFractionDigits = 2 }

private fun opcoesDo(col: ColumnConfig, r: Map<String, String?>, lookups: Map<String, List<LookupEntity>>, farms: List<FarmEntity>): List<String> {
    if (!col.staticOptions.isNullOrEmpty()) return col.staticOptions
    val nomes = farms.map { it.name }
    if (col.key == "fazenda") return nomes
    val cat = col.dependsOn?.let { d -> col.lookupCategoryByValue?.get(r[d].orEmpty()) } ?: col.lookupCategory
    val base = cat?.let { lookups[it] }?.map { it.value }.orEmpty()
    return if (col.key == "local" && base.isNotEmpty()) base + nomes else base
}

fun conferirRegistro(cfg: DomainConfig, r: Map<String, String?>, lookups: Map<String, List<LookupEntity>>, farms: List<FarmEntity>): List<ProblemaCampo> {
    val out = mutableListOf<ProblemaCampo>()
    for (col in cfg.columns) {
        if (col.computed || col.type == "checkbox") continue
        val v = r[col.key]
        if (v.isNullOrBlank()) {
            if (col.required && col.visibleWhenField == null && col.visibleWhenAll == null) out += ProblemaCampo(col.key, col.label, "vazio", mensagem = "${col.label} não preenchido")
            continue
        }
        if (col.type != "select") continue
        val opcoes = opcoesDo(col, r, lookups, farms)
        if (opcoes.isEmpty() || v in opcoes) continue
        val n = normalizarTexto(v)
        val igual = opcoes.firstOrNull { normalizarTexto(it) == n }
        if (igual != null) {
            out += ProblemaCampo(col.key, col.label, "grafia", v, igual, "${col.label} escrito diferente da lista")
            continue
        }
        var melhor: String? = null
        var melhorD = 3
        if (n.length > 3) for (o in opcoes) {
            val no = normalizarTexto(o)
            val d = if (no.startsWith(n) || n.startsWith(no)) 1 else distancia(n, no)
            if (d < melhorD) { melhorD = d; melhor = o }
        }
        out += ProblemaCampo(col.key, col.label, "lista", v, melhor, "${col.label} fora da lista suspensa")
    }
    val tem = { k: String -> cfg.columns.any { it.key == k } }
    val computado = { k: String -> cfg.columns.any { it.key == k && it.computed } }
    for ((q, u, t) in TRIOS) {
        if (!tem(q) || !tem(u) || !tem(t) || computado(t)) continue
        val nq = painelNum(r[q]); val nu = painelNum(r[u]); val nt = painelNum(r[t])
        if (nq != 0.0 && nu != 0.0 && nt != 0.0) {
            val esperado = nq * nu
            if (kotlin.math.abs(esperado - nt) > maxOf(0.05, kotlin.math.abs(nt) * 0.01)) {
                val rot = cfg.columns.firstOrNull { it.key == t }?.label ?: t
                out += ProblemaCampo(t, rot, "divergencia", mensagem = "$rot ${nf.format(nt)} ≠ quantidade × unitário (${nf.format(esperado)})")
            }
        }
    }
    if (tem("dataInicio") && tem("dataFim")) {
        val ini = r["dataInicio"]?.take(10).orEmpty(); val fim = r["dataFim"]?.take(10).orEmpty()
        if (ini.length == 10 && fim.length == 10 && fim < ini) out += ProblemaCampo("dataFim", "Data fim", "divergencia", mensagem = "Data fim antes da data início")
    }
    if (tem("hectare")) {
        val ha = painelNum(r["hectare"])
        val nome = r["local"] ?: r["fazenda"]
        val faz = nome?.let { nm -> farms.firstOrNull { normalizarTexto(it.name) == normalizarTexto(nm) } }
        if (ha > 0 && faz != null && faz.areaHa > 0 && ha > faz.areaHa * 1.001) {
            out += ProblemaCampo("hectare", "Hectare", "divergencia", mensagem = "Hectare ${nf.format(ha)} maior que a área da fazenda (${nf.format(faz.areaHa)} ha)")
        }
    }
    return out
}

/** Lista da conferência (abre ao tocar em "Pendentes"). */
@androidx.compose.runtime.Composable
fun ConferenciaCard(
    records: List<Map<String, String?>>,
    problemas: Map<String, List<ProblemaCampo>>,
    onAplicar: (List<Triple<String, String, String>>) -> Unit,
    onAbrir: (String) -> Unit,
    onFechar: () -> Unit,
) {
    val cs = androidx.compose.material3.MaterialTheme.colorScheme
    val laranja = corAtencao()
    val verde = corSucesso()
    var confirmar by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    fun titulo(id: String): String {
        val r = records.firstOrNull { it["id"] == id } ?: return "Lançamento"
        val data = (r["data"] ?: r["dataInicio"] ?: r["dataEmissao"])?.take(10)?.let { isoDateToBr(it) }
        return listOfNotNull(r["os"]?.takeIf { it.isNotBlank() }?.let { "O.S. $it" }, (r["local"] ?: r["fazenda"] ?: r["item"] ?: r["descricao"])?.takeIf { it.isNotBlank() }, data).joinToString(" · ").ifBlank { "Lançamento" }
    }
    val corrigiveis = problemas.flatMap { (id, ps) -> ps.filter { (it.tipo == "grafia" || it.tipo == "lista") && it.sugestao != null }.map { Triple(id, it, titulo(id)) } }
    val M = androidx.compose.ui.Modifier
    com.bragro.mobile.ui.theme.Card(modifier = M.fillMaxWidth(), colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = cs.surface)) {
        androidx.compose.foundation.layout.Row(M.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(if (problemas.isEmpty()) "Cadastro conferido — nada a corrigir" else "${problemas.size} lançamento(s) para corrigir", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = 13.sp, modifier = M.weight(1f))
            if (corrigiveis.isNotEmpty()) androidx.compose.material3.TextButton(onClick = { confirmar = true }) { Text("Corrigir todos (${corrigiveis.size})", fontSize = 12.sp) }
            androidx.compose.material3.IconButton(onClick = onFechar) { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.Close, contentDescription = "Fechar") }
        }
        problemas.entries.take(60).forEach { (id, ps) ->
            androidx.compose.material3.HorizontalDivider(color = cs.outlineVariant)
            androidx.compose.foundation.layout.Column(M.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)) {
                androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(titulo(id), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = 12.5.sp, modifier = M.weight(1f), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    androidx.compose.material3.OutlinedButton(onClick = { onAbrir(id) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp), modifier = M.height(30.dp)) { Text("Abrir", fontSize = 11.5.sp) }
                }
                ps.forEach { p ->
                    Text(
                        ROTULO_PROBLEMA[p.tipo].orEmpty().uppercase(), fontSize = 9.5.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold, color = laranja,
                        modifier = M.background(laranja.copy(alpha = 0.15f), androidx.compose.foundation.shape.RoundedCornerShape(50)).padding(horizontal = 7.dp, vertical = 1.dp),
                    )
                    if (p.atual != null && p.sugestao != null) {
                        Text(
                            androidx.compose.ui.text.buildAnnotatedString {
                                append("${p.rotulo}: ")
                                pushStyle(androidx.compose.ui.text.SpanStyle(color = cs.onSurfaceVariant, textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough)); append(p.atual); pop()
                                append(" → ")
                                pushStyle(androidx.compose.ui.text.SpanStyle(color = verde, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)); append(p.sugestao); pop()
                            },
                            fontSize = 12.sp,
                        )
                        androidx.compose.material3.TextButton(onClick = { onAplicar(listOf(Triple(id, p.campo, p.sugestao))) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 0.dp), modifier = M.height(28.dp)) { Text("Aplicar", fontSize = 11.5.sp) }
                    } else {
                        Text(p.mensagem + (p.atual?.let { ": \"$it\"" } ?: ""), fontSize = 12.sp)
                    }
                }
            }
        }
        if (problemas.size > 60) Text("Mostrando 60 de ${problemas.size}. Corrija estes e a lista atualiza.", fontSize = 11.sp, color = cs.onSurfaceVariant, modifier = M.padding(12.dp))
    }
    if (confirmar) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmar = false },
            title = { Text("Corrigir ${corrigiveis.size} campo(s)?") },
            text = {
                androidx.compose.foundation.layout.Column(M.heightIn(max = 380.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
                    Text("Confira o que vai mudar. Campos vazios e divergências não entram — abra o lançamento.", fontSize = 12.sp, color = cs.onSurfaceVariant)
                    corrigiveis.forEach { (_, p, t) -> Text("$t — ${p.rotulo}: ${p.atual} → ${p.sugestao}", fontSize = 12.sp) }
                }
            },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { confirmar = false; onAplicar(corrigiveis.map { (id, p, _) -> Triple(id, p.campo, p.sugestao!!) }) }) { Text("Confirmar") } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { confirmar = false }) { Text("Cancelar") } },
        )
    }
}
