package com.bragro.mobile.ui.domain

import java.time.DayOfWeek
import java.time.LocalDate

/** v414 -- contas do bloco "Itens e parcelas" do app, num arquivo puro (sem
 * Compose/Android) para serem testadas automaticamente
 * (app/src/test/.../ParcelasCalcTest.kt). Mesmas regras da plataforma
 * (src/lib/financeiro/parcelas-calc.ts e lib/services/financeiro.ts). */
object ParcelasCalc {
    private val MESES_PERIODO = mapOf("MENSAL" to 1, "BIMESTRAL" to 2, "TRIMESTRAL" to 3, "QUADRIMESTRAL" to 4, "SEMESTRAL" to 6, "ANUAL" to 12)
    private val FAZENDA_RE = Regex("^(FAZ\\.?|S[ÍI]TIO|RANCHO|CH[ÁA]CARA|GLEBA|EST[ÂA]NCIA|HARAS|GRANJA)", RegexOption.IGNORE_CASE)
    private val ENERGIA_RE = Regex("ENERGIA EL[ÉE]TRICA", RegexOption.IGNORE_CASE)

    fun r2(v: Double): Double = Math.round(v * 100.0) / 100.0

    /** "ANUAL 4X" -> (12, 4); vazio / "À VISTA" -> (0, 1). Primeiro = meses entre parcelas, segundo = quantidade. */
    fun periodoParcelas(p: String): Pair<Int, Int> {
        val s = java.text.Normalizer.normalize(p, java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").uppercase().trim()
        if (s.isEmpty() || s.startsWith("A VISTA") || s.startsWith("__")) return 0 to 1 // v419 -- "__vazio__" = à vista
        val meses = MESES_PERIODO.entries.firstOrNull { s.startsWith(it.key) }?.value ?: 1
        val n = Regex("(\\d+)\\s*X\\b").find(s)?.groupValues?.get(1)?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        return meses to n
    }

    /** "1.234,56" / "1234.56" / "R$ 10" -> número. Vazio -> 0. */
    fun numeroBr(t: String?): Double {
        val s = t.orEmpty().trim()
        if (s.isEmpty()) return 0.0
        val limpo = if (s.contains(",")) s.replace(Regex("[^\\d,-]"), "").replace(",", ".") else s.replace(Regex("[^\\d.-]"), "")
        return limpo.toDoubleOrNull() ?: 0.0
    }

    /** Juros (+) / desconto (−) dividido entre as parcelas. "igual" = o mesmo em
     * cada uma; "prazo" = peso 1, 2, 3… A última fecha o arredondamento. */
    fun distribuirAjuste(total: Double, n: Int, modo: String): List<Double> {
        val out = MutableList(n) { 0.0 }
        if (total == 0.0 || n < 1) return out
        val pesos = (1..n).map { if (modo == "prazo") it.toDouble() else 1.0 }
        val soma = pesos.sum()
        var acc = 0.0
        for (i in 0 until n - 1) { out[i] = r2(total * pesos[i] / soma); acc += out[i] }
        out[n - 1] = r2(total - acc)
        return out
    }

    /** Vencimentos sugeridos: data + i × meses; sábado/domingo passam para segunda.
     * À vista (meses = 0) vence na própria data. (O servidor ainda pula feriados.) */
    fun vencimentos(base: LocalDate, meses: Int, n: Int): List<LocalDate> = (0 until n).map { i ->
        var d = if (meses == 0) base else base.plusMonths(((i + 1) * meses).toLong())
        if (meses != 0 && d.dayOfWeek == DayOfWeek.SATURDAY) d = d.plusDays(2)
        if (meses != 0 && d.dayOfWeek == DayOfWeek.SUNDAY) d = d.plusDays(1)
        d
    }

    /** Mesma regra de isDirectFarm da plataforma: Local de fazenda = rateio direto. */
    fun ehFazenda(l: String): Boolean = l.isNotBlank() && !ENERGIA_RE.containsMatchIn(l) && FAZENDA_RE.containsMatchIn(l.trim())

    data class ItemCalc(val fazenda: String, val quantidade: Double, val valor: Double)

    /** Total da nota: soma dos valores dos itens quando todos têm valor; senão, o Bruto. */
    fun totalNota(itens: List<ItemCalc>, bruto: Double): Double =
        if (itens.isNotEmpty() && itens.all { it.valor > 0 }) r2(itens.sumOf { it.valor }) else bruto

    /** Quanto da nota fica em cada fazenda: pelo valor dos itens (todos com valor)
     * ou, sem valor, pela quantidade sobre o total. Itens sem fazenda ficam de fora. */
    fun porFazenda(itens: List<ItemCalc>, bruto: Double): Map<String, Double> {
        val comValor = itens.isNotEmpty() && itens.all { it.valor > 0 }
        val tot = totalNota(itens, bruto)
        val qtd = itens.sumOf { it.quantidade }
        val out = linkedMapOf<String, Double>()
        itens.forEach { l ->
            if (l.fazenda.isBlank()) return@forEach
            val parte = if (comValor) l.valor else if (qtd > 0) tot * l.quantidade / qtd else 0.0
            out[l.fazenda] = (out[l.fazenda] ?: 0.0) + parte
        }
        return out
    }

    /** Prévia de uma parcela por fazenda: o líquido dividido na proporção de cada fazenda. */
    fun previaPorFazenda(liquidoParcela: Double, fazendas: Map<String, Double>): Map<String, Double> {
        val total = fazendas.values.sum()
        if (total <= 0) return emptyMap()
        return fazendas.mapValues { (_, v) -> r2(liquidoParcela * v / total) }
    }
}
