package com.bragro.mobile.ui.domain

import java.text.NumberFormat
import java.util.Locale

private val PT_BR_NUM = Locale("pt", "BR")

/**
 * v43 -- número de quantidade no padrão brasileiro, com UMA casa decimal:
 * ponto de milhar, vírgula decimal e sem a sobra do ponto flutuante
 * ("0.7999999999999998" vira "0,8"). Usado nos saldos de estoque, áreas em
 * hectares e demais quantidades do app.
 */
fun numeroBr(v: Double, casas: Int = 1): String =
    NumberFormat.getNumberInstance(PT_BR_NUM).apply {
        minimumFractionDigits = casas
        maximumFractionDigits = casas
    }.format(v)

/** Mesma regra para um valor que chega como texto ("16550.0" -> "16.550,0"). */
fun numeroBr(v: String?, casas: Int = 1): String {
    val d = v?.trim()?.replace(",", ".")?.toDoubleOrNull() ?: return v.orEmpty()
    return numeroBr(d, casas)
}

/** Quantidade com a unidade colada no padrão do app ("16.550,0 LT"). */
fun quantidadeBr(v: Double, unidade: String?, casas: Int = 1): String =
    numeroBr(v, casas) + (unidade?.takeIf { it.isNotBlank() }?.let { " $it" } ?: "")
