package com.bragro.mobile.ui.domain

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

// Espelho de FINANCEIRO_PERIODOS + genericPeriodoRange + buildFinanceiroPeriodoWhere
// + financeiroPeriodoVctoWindow (data-table.tsx / actions.ts) -- só que calculado
// em memória sobre os registros já sincronizados (Room), sem endpoint novo. As
// mesmas 8 categorias servem tanto pro Financeiro (recorrência ou vencimento,
// conforme a visão) quanto pros demais domínios (janela de data pra trás sobre
// a 1ª coluna de data), exatamente como no site.
enum class PeriodoCategoria(val label: String) {
    SEMANAL("Semanal"),
    QUINZENAL("Quinzenal"),
    MENSAL("Mensal"),
    BIMESTRAL("Bimestral"),
    TRIMESTRAL("Trimestral"),
    QUADRIMESTRAL("Quadrimestral"),
    SEMESTRAL("Semestral"),
    ANUAL("Anual"),
}

private val ISO = SimpleDateFormat("yyyy-MM-dd", Locale.US)
private fun iso(cal: Calendar): String = ISO.format(cal.time)

/** Espelho de genericPeriodoRange (data-table.tsx): janela de data PARA TRÁS
 * a partir de hoje -- usada nos domínios sem campo de recorrência, filtrando
 * pela 1ª coluna de data. */
fun genericPeriodoRange(categoria: PeriodoCategoria): Pair<String, String> {
    val to = Calendar.getInstance()
    val from = Calendar.getInstance()
    when (categoria) {
        PeriodoCategoria.SEMANAL -> from.add(Calendar.DAY_OF_MONTH, -7)
        PeriodoCategoria.QUINZENAL -> from.add(Calendar.DAY_OF_MONTH, -15)
        PeriodoCategoria.MENSAL -> from.set(Calendar.DAY_OF_MONTH, 1)
        PeriodoCategoria.BIMESTRAL -> from.add(Calendar.MONTH, -2)
        PeriodoCategoria.TRIMESTRAL -> from.add(Calendar.MONTH, -3)
        PeriodoCategoria.QUADRIMESTRAL -> from.add(Calendar.MONTH, -4)
        PeriodoCategoria.SEMESTRAL -> from.add(Calendar.MONTH, -6)
        PeriodoCategoria.ANUAL -> from.add(Calendar.MONTH, -12)
    }
    return iso(from) to iso(to)
}

/** Espelho de financeiroPeriodoVctoWindow (actions.ts): janela de VENCIMENTO
 * pra FRENTE a partir de hoje -- usada só em Contas a Pagar/Receber. */
fun financeiroPeriodoVctoWindow(categoria: PeriodoCategoria): Pair<String, String> {
    val from = Calendar.getInstance()
    val to = Calendar.getInstance()
    when (categoria) {
        PeriodoCategoria.SEMANAL -> to.add(Calendar.DAY_OF_MONTH, 7)
        PeriodoCategoria.QUINZENAL -> to.add(Calendar.DAY_OF_MONTH, 15)
        PeriodoCategoria.MENSAL -> {
            from.set(Calendar.DAY_OF_MONTH, 1)
            to.set(Calendar.DAY_OF_MONTH, 1)
            to.add(Calendar.MONTH, 1)
            to.add(Calendar.DAY_OF_MONTH, -1)
        }
        PeriodoCategoria.BIMESTRAL -> to.add(Calendar.MONTH, 2)
        PeriodoCategoria.TRIMESTRAL -> to.add(Calendar.MONTH, 3)
        PeriodoCategoria.QUADRIMESTRAL -> to.add(Calendar.MONTH, 4)
        PeriodoCategoria.SEMESTRAL -> to.add(Calendar.MONTH, 6)
        PeriodoCategoria.ANUAL -> to.add(Calendar.MONTH, 12)
    }
    return iso(from) to iso(to)
}

/** Compara datas no formato ISO (yyyy-MM-dd, ou com hora anexada) como texto
 * -- funciona pra comparação de intervalo porque o formato é sempre fixo. */
fun isDateInRange(value: String?, from: String, to: String): Boolean {
    val v = value?.trim()?.take(10)
    if (v.isNullOrBlank()) return false
    return v >= from && v <= to
}

/** Espelho de buildFinanceiroPeriodoWhere (actions.ts): em Pagar/Receber
 * filtra pela janela de VENCIMENTO; nas demais visões, casa pelo início do
 * campo "Periodo" do lançamento (ex.: "MENSAL 2X" cai em "MENSAL"). */
fun filterByFinanceiroPeriodo(
    records: List<Map<String, String?>>,
    categoria: PeriodoCategoria?,
    view: FinanceiroView,
): List<Map<String, String?>> {
    if (categoria == null) return records
    if (view == FinanceiroView.PAGAR || view == FinanceiroView.RECEBER) {
        val (from, to) = financeiroPeriodoVctoWindow(categoria)
        return records.filter { isDateInRange(it["vcto"], from, to) }
    }
    return records.filter { it["periodo"]?.trim()?.uppercase(Locale.ROOT)?.startsWith(categoria.name) == true }
}

/** Intervalo de datas manual ("Intervalo" no site) -- aplicado sobre a coluna
 * indicada (vcto em Pagar/Receber, data nos demais casos). Vazio em from/to
 * quer dizer "sem limite" desse lado. */
fun filterByDateInterval(
    records: List<Map<String, String?>>,
    dateKey: String,
    from: String,
    to: String,
): List<Map<String, String?>> {
    if (from.isBlank() && to.isBlank()) return records
    return records.filter { r ->
        val v = r[dateKey]?.trim()?.take(10)
        if (v.isNullOrBlank()) return@filter false
        (from.isBlank() || v >= from) && (to.isBlank() || v <= to)
    }
}
