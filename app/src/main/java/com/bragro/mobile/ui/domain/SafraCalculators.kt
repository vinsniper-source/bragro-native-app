package com.bragro.mobile.ui.domain

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/** Espelho de Semeadura()/Pulverizacao()/Adubacao() em agronomic-calculators.tsx. */
@Composable
fun SafraCalculators() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SemeaduraCalc()
        PulverizacaoCalc()
        AdubacaoCalc()
    }
}

@Composable
private fun SemeaduraCalc() {
    val popDesejada = rememberCalcField()
    val espacamento = rememberCalcField()
    val pms = rememberCalcField()
    val germinacao = rememberCalcField()
    val area = rememberCalcField()

    val pop = popDesejada.n
    val esp = espacamento.n
    val pmsVal = pms.n
    val germ = germinacao.n
    val areaVal = area.n

    var kgPorHa: Double? = null
    var totalSementes: Double? = null
    if (pop != null && esp != null && esp > 0 && pmsVal != null && germ != null && germ > 0) {
        val rowMetersPerHa = 10000 / esp
        val seedsPerMeterAdj = pop / (germ / 100)
        val totalSeedsPerHa = seedsPerMeterAdj * rowMetersPerHa
        kgPorHa = (totalSeedsPerHa * (pmsVal / 1000)) / 1000
        if (areaVal != null) totalSementes = kgPorHa * areaVal
    }

    CalcCard(icon = "🌱", title = "Semeadura") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CalcNumField("Pop. desejada", "plantas/m", popDesejada)
            CalcNumField("Espaçamento", "m", espacamento)
            CalcNumField("PMS", "g", pms)
            CalcNumField("Germinação", "%", germinacao)
            CalcNumField("Área a plantar", "ha", area)
            CalcResultField("Quantidade", "kg/ha", fmtCalc(kgPorHa))
            CalcResultField("Total sementes", "kg", fmtCalc(totalSementes))
        }
    }
}

@Composable
private fun PulverizacaoCalc() {
    val vazaoBico = rememberCalcField()
    val velocidade = rememberCalcField()
    val espacamentoBicos = rememberCalcField()
    val areaPulv = rememberCalcField()
    val capacidadeTanque = rememberCalcField()
    val doseProduto = rememberCalcField()

    val q = vazaoBico.n
    val v = velocidade.n
    val e = espacamentoBicos.n
    val area = areaPulv.n
    val capacidade = capacidadeTanque.n
    val dose = doseProduto.n

    val vazaoLha = if (q != null && v != null && v > 0 && e != null && e > 0) (q * 600) / (v * e) else null
    val caldaTotal = if (vazaoLha != null && area != null) vazaoLha * area else null
    val produtoTotal = if (dose != null && area != null) dose * area else null
    val nTanques = if (caldaTotal != null && capacidade != null && capacidade > 0) caldaTotal / capacidade else null

    CalcCard(icon = "💧", title = "Pulverização") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CalcNumField("Vazão/bico", "L/min", vazaoBico)
            CalcNumField("Velocidade", "km/h", velocidade)
            CalcNumField("Espaç. bicos", "m", espacamentoBicos)
            CalcNumField("Área", "ha", areaPulv)
            CalcNumField("Capac. tanque", "L", capacidadeTanque)
            CalcNumField("Dose produto", "L ou kg/ha", doseProduto)
            CalcResultField("Vazão", "L/ha", fmtCalc(vazaoLha))
            CalcResultField("Calda total", "L", fmtCalc(caldaTotal))
            CalcResultField("Produto total", "L/kg", fmtCalc(produtoTotal))
            CalcResultField("Tanques", value = fmtCalc(nTanques, 1))
        }
    }
}

@Composable
private fun AdubacaoCalc() {
    val dose = rememberCalcField()
    val area = rememberCalcField()
    val pctN = rememberCalcField()
    val pctP = rememberCalcField()
    val pctK = rememberCalcField()

    val d = dose.n
    val a = area.n

    val aduboTotal = if (d != null && a != null) d * a else null
    val nTotal = if (aduboTotal != null && pctN.n != null) aduboTotal * (pctN.n!! / 100) else null
    val pTotal = if (aduboTotal != null && pctP.n != null) aduboTotal * (pctP.n!! / 100) else null
    val kTotal = if (aduboTotal != null && pctK.n != null) aduboTotal * (pctK.n!! / 100) else null

    CalcCard(icon = "🧪", title = "Adubação") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CalcNumField("Dose", "kg/ha", dose)
            CalcNumField("Área", "ha", area)
            CalcNumField("% N", field = pctN)
            CalcNumField("% P", field = pctP)
            CalcNumField("% K", field = pctK)
            CalcResultField("Adubo total", "kg", fmtCalc(aduboTotal))
            CalcResultField("N", "kg", fmtCalc(nTotal))
            CalcResultField("P", "kg", fmtCalc(pTotal))
            CalcResultField("K", "kg", fmtCalc(kTotal))
        }
    }
}
