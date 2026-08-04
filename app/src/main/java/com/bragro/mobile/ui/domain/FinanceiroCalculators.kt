package com.bragro.mobile.ui.domain

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.pow

/** Espelho de JurosCompostos()/Amortizacao() em agronomic-calculators.tsx. */
@Composable
fun FinanceiroCalculators() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        JurosCompostosCalc()
        AmortizacaoCalc()
    }
}

@Composable
private fun JurosCompostosCalc() {
    val capital = rememberCalcField()
    val taxa = rememberCalcField()
    val periodo = rememberCalcField()

    val c = capital.n
    val i = taxa.n
    val n = periodo.n

    var montante: Double? = null
    var jurosTotais: Double? = null
    if (c != null && i != null && n != null && n >= 0) {
        montante = c * (1 + i / 100).pow(n)
        jurosTotais = montante - c
    }

    CalcCard(icon = "📈", title = "Juros compostos") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CalcNumField("Capital inicial", "R$", capital)
            CalcNumField("Taxa", "% ao mês", taxa)
            CalcNumField("Período", "meses", periodo)
            CalcResultField("Montante final", value = fmtCalcBrl(montante))
            CalcResultField("Juros totais", value = fmtCalcBrl(jurosTotais))
        }
    }
}

@Composable
private fun AmortizacaoCalc() {
    val valorFinanciado = rememberCalcField()
    val taxa = rememberCalcField()
    val parcelas = rememberCalcField()
    var sistema by remember { mutableStateOf("SAC") }

    val pv = valorFinanciado.n
    val taxaPct = taxa.n
    val n = parcelas.n

    var parcela1: Double? = null
    var parcelaFinal: Double? = null
    var totalJuros: Double? = null
    var totalPago: Double? = null

    if (pv != null && pv > 0 && taxaPct != null && n != null && n > 0) {
        val i = taxaPct / 100
        if (sistema == "PRICE") {
            val pmt = if (i > 0) (pv * (i * (1 + i).pow(n))) / ((1 + i).pow(n) - 1) else pv / n
            parcela1 = pmt
            parcelaFinal = pmt
            totalPago = pmt * n
            totalJuros = totalPago - pv
        } else {
            val amortizacaoConstante = pv / n
            parcela1 = amortizacaoConstante + pv * i
            parcelaFinal = amortizacaoConstante + amortizacaoConstante * i
            totalJuros = i * (n * pv - amortizacaoConstante * ((n * (n - 1)) / 2))
            totalPago = pv + totalJuros
        }
    }

    CalcCard(icon = "🏦", title = "Amortização (custeio/financiamento)") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CalcNumField("Valor financiado", "R$", valorFinanciado)
            CalcNumField("Taxa", "% ao mês", taxa)
            CalcNumField("Nº de parcelas", field = parcelas)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("SAC", "PRICE").forEach { s ->
                    if (sistema == s) {
                        Button(onClick = { sistema = s }, modifier = Modifier) { Text(s) }
                    } else {
                        OutlinedButton(onClick = { sistema = s }, modifier = Modifier) { Text(s) }
                    }
                }
            }
            CalcResultField("1ª parcela", value = fmtCalcBrl(parcela1))
            CalcResultField("Última parcela", value = fmtCalcBrl(parcelaFinal))
            CalcResultField("Total de juros", value = fmtCalcBrl(totalJuros))
            CalcResultField("Total pago", value = fmtCalcBrl(totalPago))
        }
    }
}
