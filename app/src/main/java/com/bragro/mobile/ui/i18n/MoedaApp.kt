package com.bragro.mobile.ui.i18n

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.text.NumberFormat
import java.util.Locale

/**
 * v1.6.13 (mockup v114) -- "Exibir valores em" igual à plataforma
 * (lib/currency/currency-context.tsx): BRL, USD (U$D) ou ARS. Os valores
 * guardados continuam em R$; só a exibição converte, pela cotação do dia
 * (mesma do bloco Câmbio). Sem cotação, mostra em R$ para não sumir o número.
 */
object MoedaApp {
    private const val PREFS = "bragro_moeda"

    /** "BRL", "USD" ou "ARS". Estado observável: trocar recompõe a UI. */
    var codigo by mutableStateOf("BRL")
        private set
    var usdBrl by mutableStateOf<Double?>(null)
        private set
    var arsBrl by mutableStateOf<Double?>(null)
        private set

    fun init(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        codigo = p.getString("codigo", "BRL") ?: "BRL"
        usdBrl = p.getFloat("usd", 0f).toDouble().takeIf { it > 0 }
        arsBrl = p.getFloat("ars", 0f).toDouble().takeIf { it > 0 }
    }

    fun definir(context: Context, novo: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("codigo", novo).apply()
        codigo = novo
    }

    /** Chamado quando o Início recebe o câmbio do dia. */
    fun atualizarTaxas(context: Context, usd: Double?, ars: Double?) {
        val e = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        if (usd != null && usd > 0) { usdBrl = usd; e.putFloat("usd", usd.toFloat()) }
        if (ars != null && ars > 0) { arsBrl = ars; e.putFloat("ars", ars.toFloat()) }
        e.apply()
    }

    val sigla: String get() = when (codigo) { "USD" -> "U\$D"; "ARS" -> "ARS"; else -> "BRL" }

    private fun taxa(c: String): Double? = when (c) { "BRL" -> 1.0; "USD" -> usdBrl; "ARS" -> arsBrl; else -> null }

    /** Converte entre moedas (taxas em R$ por unidade). null sem cotação. */
    fun converter(valor: Double, de: String, para: String): Double? {
        val a = taxa(de) ?: return null
        val b = taxa(para) ?: return null
        return valor * a / b
    }

    fun comCodigo(v: Double, c: String): String {
        val loc = when (c) { "USD" -> Locale.US; "ARS" -> Locale("es", "AR"); else -> Locale("pt", "BR") }
        val nf = NumberFormat.getNumberInstance(loc).apply { minimumFractionDigits = 2; maximumFractionDigits = 2 }
        val pref = when (c) { "USD" -> "U\$D"; "ARS" -> "ARS"; else -> "R$" }
        return "$pref ${nf.format(v)}"
    }

    private val brl: NumberFormat get() = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))

    /** Formata um valor em R$ na moeda de exibição escolhida. */
    fun formatar(valorBrl: Double): String {
        if (codigo == "BRL") return brl.format(valorBrl).replace(' ', ' ')
        val t = taxa(codigo)
        if (t == null || t <= 0) return brl.format(valorBrl).replace(' ', ' ')
        return comCodigo(valorBrl / t, codigo)
    }
}
