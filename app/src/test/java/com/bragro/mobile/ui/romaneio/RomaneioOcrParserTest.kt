package com.bragro.mobile.ui.romaneio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fase 3: primeiro teste automatizado do projeto -- RomaneioOcrParser e
 * logica Kotlin pura (nenhuma dependencia de Android), entao roda como
 * teste de unidade comum na JVM (pasta app/src/test, nao app/src/
 * androidTest), sem precisar de emulador nem do toolchain Android
 * completo -- so precisa do Gradle/JDK, que e exatamente o que o CI
 * (.github/workflows/android-build.yml) ja tem. */
class RomaneioOcrParserTest {

    @Test
    fun `le um ticket completo com todos os campos`() {
        val texto = """
            TICKET DE PESAGEM
            Nº ROMANEIO: 00123
            PESO BRUTO: 32500 KG
            TARA: 12000 KG
            UMIDADE: 14,5 %
            IMPUREZA: 1,2 %
        """.trimIndent()

        val campos = RomaneioOcrParser.parse(texto)

        assertEquals("00123", campos["noRomaneio"])
        assertEquals("32500", campos["pesoBrutoKg"])
        assertEquals("12000", campos["taraKg"])
        assertEquals("14.5", campos["umidade"])
        assertEquals("1.2", campos["impureza"])
    }

    @Test
    fun `funciona com texto em minusculas (case-insensitive)`() {
        val texto = "peso bruto 5000\ntara 300"

        val campos = RomaneioOcrParser.parse(texto)

        assertEquals("5000", campos["pesoBrutoKg"])
        assertEquals("300", campos["taraKg"])
    }

    @Test
    fun `usa o rotulo alternativo BRUTO quando PESO BRUTO nao aparece`() {
        val campos = RomaneioOcrParser.parse("BRUTO 4321")

        assertEquals("4321", campos["pesoBrutoKg"])
    }

    @Test
    fun `troca virgula decimal por ponto`() {
        val campos = RomaneioOcrParser.parse("UMIDADE 14,75")

        assertEquals("14.75", campos["umidade"])
    }

    @Test
    fun `nao cruza linhas -- numero na linha seguinte nao conta`() {
        val campos = RomaneioOcrParser.parse("TARA\n320")

        assertNull(campos["taraKg"])
    }

    @Test
    fun `texto sem nenhum rotulo conhecido devolve mapa vazio`() {
        val campos = RomaneioOcrParser.parse("NOTA FISCAL ELETRONICA XYZ 999")

        assertTrue(campos.isEmpty())
    }

    @Test
    fun `so preenche os campos que consegue reconhecer`() {
        val campos = RomaneioOcrParser.parse("TARA: 8500")

        assertEquals(1, campos.size)
        assertEquals("8500", campos["taraKg"])
    }
}
