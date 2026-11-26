package com.bragro.mobile.ui.domain

import com.bragro.mobile.ui.domain.ParcelasCalc.ItemCalc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** v414 -- conferência automática das contas do app (mesmos casos dos testes da
 * plataforma, financeiro-calculos.test.ts). Roda antes de cada assembleRelease:
 * se uma conta quebrar, o APK não é gerado. */
class ParcelasCalcTest {
    private fun igual(esperado: List<Double>, real: List<Double>) {
        assertEquals("quantidade", esperado.size, real.size)
        esperado.zip(real).forEach { (e, r) -> assertEquals(e, r, 0.001) }
    }
    private fun soma(xs: Collection<Double>) = ParcelasCalc.r2(xs.sum())

    @Test fun periodo() {
        assertEquals(12 to 4, ParcelasCalc.periodoParcelas("ANUAL 4X"))
        assertEquals(1 to 10, ParcelasCalc.periodoParcelas("MENSAL 10X"))
        assertEquals(6 to 2, ParcelasCalc.periodoParcelas("semestral 2x"))
        assertEquals(0 to 1, ParcelasCalc.periodoParcelas("À VISTA"))
        assertEquals(0 to 1, ParcelasCalc.periodoParcelas(""))
    }

    @Test fun numeroBr() {
        assertEquals(1234.56, ParcelasCalc.numeroBr("1.234,56"), 0.001)
        assertEquals(1234.56, ParcelasCalc.numeroBr("1234.56"), 0.001)
        assertEquals(-40.0, ParcelasCalc.numeroBr("-40.00"), 0.001)
        assertEquals(0.0, ParcelasCalc.numeroBr(null), 0.001)
    }

    @Test fun jurosIgual() = igual(listOf(10.0, 10.0, 10.0, 10.0), ParcelasCalc.distribuirAjuste(40.0, 4, "igual"))

    @Test fun jurosProporcionalAoPrazo() = igual(listOf(4.0, 8.0, 12.0, 16.0), ParcelasCalc.distribuirAjuste(40.0, 4, "prazo"))

    @Test fun descontoNegativo() = igual(listOf(-4.0, -8.0, -12.0, -16.0), ParcelasCalc.distribuirAjuste(-40.0, 4, "prazo"))

    @Test fun arredondamentoFechaASoma() {
        igual(listOf(33.33, 33.33, 33.34), ParcelasCalc.distribuirAjuste(100.0, 3, "igual"))
        for (n in listOf(7, 11, 13)) {
            assertEquals(1234.57, soma(ParcelasCalc.distribuirAjuste(1234.57, n, "prazo")), 0.001)
            assertEquals(-99.99, soma(ParcelasCalc.distribuirAjuste(-99.99, n, "igual")), 0.001)
        }
        igual(listOf(0.0, 0.0, 0.0), ParcelasCalc.distribuirAjuste(0.0, 3, "igual"))
    }

    @Test fun vencimentosAnuaisComFimDeSemana() {
        val v = ParcelasCalc.vencimentos(LocalDate.of(2026, 11, 26), 12, 4)
        assertEquals(listOf(LocalDate.of(2027, 11, 26), LocalDate.of(2028, 11, 27), LocalDate.of(2029, 11, 26), LocalDate.of(2030, 11, 26)), v)
    }

    @Test fun vencimentoAVista() {
        assertEquals(listOf(LocalDate.of(2026, 11, 28)), ParcelasCalc.vencimentos(LocalDate.of(2026, 11, 28), 0, 1))
    }

    @Test fun exemploDoBlocoComJuros() {
        // 1.700 em 4x anual + 40 de juros proporcional ao prazo -> 429 / 433 / 437 / 441.
        val (meses, n) = ParcelasCalc.periodoParcelas("ANUAL 4X")
        assertEquals(12, meses)
        val valor = ParcelasCalc.r2(1700.0 / n)
        val liquidos = ParcelasCalc.distribuirAjuste(40.0, n, "prazo").map { ParcelasCalc.r2(valor + it) }
        igual(listOf(429.0, 433.0, 437.0, 441.0), liquidos)
        assertEquals(1740.0, soma(liquidos), 0.001)
    }

    @Test fun fazendaDiretaOuIndireta() {
        assertTrue(ParcelasCalc.ehFazenda("FAZENDA TESTE"))
        assertTrue(ParcelasCalc.ehFazenda("FAZ. SANTA FÉ"))
        assertTrue(ParcelasCalc.ehFazenda("Sítio Boa Vista"))
        assertFalse(ParcelasCalc.ehFazenda("OFICINA"))
        assertFalse(ParcelasCalc.ehFazenda("ADMINISTRATIVO"))
        assertFalse(ParcelasCalc.ehFazenda(""))
    }

    private val itens = listOf(
        ItemCalc("FAZENDA TESTE", 10.0, 1000.0),
        ItemCalc("FAZENDA TESTE 1", 20.0, 500.0),
        ItemCalc("FAZENDA TESTE", 5.0, 200.0),
    )

    @Test fun totalPelaSomaDosItens() {
        assertEquals(1700.0, ParcelasCalc.totalNota(itens, 999.0), 0.001)
        // Algum item sem valor: vale o Bruto.
        assertEquals(999.0, ParcelasCalc.totalNota(itens + ItemCalc("FAZENDA TESTE", 1.0, 0.0), 999.0), 0.001)
    }

    @Test fun divisaoPorFazendaPeloValor() {
        val m = ParcelasCalc.porFazenda(itens, 0.0)
        assertEquals(1200.0, m["FAZENDA TESTE"]!!, 0.001)
        assertEquals(500.0, m["FAZENDA TESTE 1"]!!, 0.001)
    }

    @Test fun divisaoPorFazendaPelaQuantidadeSemValor() {
        val semValor = listOf(ItemCalc("FAZENDA TESTE", 30.0, 0.0), ItemCalc("OFICINA", 10.0, 0.0))
        val m = ParcelasCalc.porFazenda(semValor, 1000.0)
        assertEquals(750.0, m["FAZENDA TESTE"]!!, 0.001)
        assertEquals(250.0, m["OFICINA"]!!, 0.001)
    }

    @Test fun previaDaParcelaPorFazenda() {
        val p = ParcelasCalc.previaPorFazenda(429.0, mapOf("FAZENDA TESTE" to 1200.0, "FAZENDA TESTE 1" to 500.0))
        assertEquals(302.82, p["FAZENDA TESTE"]!!, 0.001)
        assertEquals(126.18, p["FAZENDA TESTE 1"]!!, 0.001)
        assertTrue(ParcelasCalc.previaPorFazenda(429.0, emptyMap()).isEmpty())
    }
}
