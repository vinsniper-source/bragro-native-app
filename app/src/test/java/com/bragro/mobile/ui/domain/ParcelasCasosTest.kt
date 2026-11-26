package com.bragro.mobile.ui.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** v418 -- os MESMOS casos testados na plataforma (casos-compartilhados.test.ts),
 * lidos da mesma cópia do casos-calculos.json (app/src/test/resources). Se o app
 * e a plataforma derem resultados diferentes, um dos dois testes quebra. */
class ParcelasCasosTest {
    private val casos: JsonObject by lazy {
        val texto = javaClass.classLoader!!.getResource("casos-calculos.json")!!.readText()
        Json.parseToJsonElement(texto).jsonObject
    }

    @Test fun periodo() {
        casos["periodo"]!!.jsonArray.forEach { c ->
            val o = c.jsonObject
            val entrada = o["entrada"]!!.jsonPrimitive.content
            assertEquals("período '$entrada'", o["meses"]!!.jsonPrimitive.int to o["parcelas"]!!.jsonPrimitive.int, ParcelasCalc.periodoParcelas(entrada))
        }
    }

    @Test fun jurosDistribuido() {
        casos["ajuste"]!!.jsonArray.forEach { c ->
            val o = c.jsonObject
            val r = ParcelasCalc.distribuirAjuste(o["total"]!!.jsonPrimitive.double, o["parcelas"]!!.jsonPrimitive.int, o["modo"]!!.jsonPrimitive.content)
            val esperado = o["esperado"]!!.jsonArray.map { it.jsonPrimitive.double }
            assertEquals("quantidade", esperado.size, r.size)
            esperado.zip(r).forEach { (e, v) -> assertEquals("juros ${o["total"]} ${o["modo"]}", e, v, 0.001) }
        }
    }

    @Test fun vencimentos() {
        casos["vencimentos"]!!.jsonArray.forEach { c ->
            val o = c.jsonObject
            val r = ParcelasCalc.vencimentos(LocalDate.parse(o["base"]!!.jsonPrimitive.content), o["meses"]!!.jsonPrimitive.int, o["parcelas"]!!.jsonPrimitive.int)
            assertEquals("vencimentos de ${o["base"]}", o["esperado"]!!.jsonArray.map { LocalDate.parse(it.jsonPrimitive.content) }, r)
        }
    }

    @Test fun previaPorFazenda() {
        casos["previa"]!!.jsonArray.forEach { c ->
            val o = c.jsonObject
            val fazendas = o["fazendas"]!!.jsonObject.mapValues { it.value.jsonPrimitive.double }
            val r = ParcelasCalc.previaPorFazenda(o["liquido"]!!.jsonPrimitive.double, fazendas)
            o["esperado"]!!.jsonObject.forEach { (faz, v) -> assertEquals("prévia $faz", v.jsonPrimitive.double, r[faz]!!, 0.001) }
        }
    }
}
