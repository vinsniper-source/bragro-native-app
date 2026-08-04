package com.bragro.mobile.ui.domain

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/** Espelho de GraosPesoLiquido()/CanaAtr()/ConversaoProdutividade() em
 * agronomic-calculators.tsx. */
@Composable
fun ColheitaCalculators() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GraosPesoLiquidoCalc()
        CanaAtrCalc()
        ConversaoProdutividadeCalc()
    }
}

@Composable
private fun GraosPesoLiquidoCalc() {
    val pesoBruto = rememberCalcField()
    val umidadeReal = rememberCalcField()
    val umidadePadrao = rememberCalcField()
    val impureza = rememberCalcField()

    val pb = pesoBruto.n
    val uReal = umidadeReal.n
    val uPadrao = umidadePadrao.n
    val imp = impureza.n

    var descImpureza: Double? = null
    var descUmidade: Double? = null
    var pesoLiquido: Double? = null
    var emSacas: Double? = null

    if (pb != null && imp != null) {
        descImpureza = pb * (imp / 100)
        val pesoSemImpureza = pb - descImpureza
        if (uReal != null && uPadrao != null && uPadrao < 100) {
            val delta = uReal - uPadrao
            descUmidade = if (delta > 0) pesoSemImpureza * (delta / (100 - uPadrao)) else 0.0
            pesoLiquido = pesoSemImpureza - descUmidade
            emSacas = pesoLiquido / 60
        }
    }

    CalcCard(icon = "🌾", title = "Grãos — peso líquido") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CalcNumField("Peso bruto", "kg", pesoBruto)
            CalcNumField("Umidade real", "%", umidadeReal)
            CalcNumField("Umidade padrão", "%", umidadePadrao)
            CalcNumField("Impureza", "%", impureza)
            CalcResultField("Desc. umidade", "kg", fmtCalc(descUmidade))
            CalcResultField("Desc. impureza", "kg", fmtCalc(descImpureza))
            CalcResultField("Peso líquido", "kg", fmtCalc(pesoLiquido))
            CalcResultField("Sacas (60kg)", value = fmtCalc(emSacas))
        }
    }
}

@Composable
private fun CanaAtrCalc() {
    val pesoCana = rememberCalcField()
    val atrUsina = rememberCalcField()
    val precoAtr = rememberCalcField()

    val pc = pesoCana.n
    val atr = atrUsina.n
    val preco = precoAtr.n

    val atrTotal = if (pc != null && atr != null) pc * atr else null
    val valorEstimado = if (atrTotal != null && preco != null) atrTotal * preco else null

    CalcCard(icon = "🎋", title = "Cana — ATR") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CalcNumField("Peso bruto cana", "ton", pesoCana)
            CalcNumField("ATR — usina", "kg/ton", atrUsina)
            CalcNumField("Preço ATR", "R$/kg", precoAtr)
            CalcResultField("ATR total", "kg", fmtCalc(atrTotal))
            CalcResultField("Valor estimado", value = fmtCalcBrl(valorEstimado))
        }
    }
}

@Composable
private fun ConversaoProdutividadeCalc() {
    val produtividade = rememberCalcField()
    val p = produtividade.n
    val emSacas = if (p != null) p / 60 else null
    val emToneladas = if (p != null) p / 1000 else null

    CalcCard(icon = "🔄", title = "Conversão de produtividade") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CalcNumField("Produtividade", "kg/ha", produtividade)
            CalcResultField("Sacas (60kg)", value = fmtCalc(emSacas))
            CalcResultField("Toneladas", value = fmtCalc(emToneladas))
        }
    }
}
