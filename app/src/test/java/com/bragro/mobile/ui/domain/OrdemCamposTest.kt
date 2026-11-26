package com.bragro.mobile.ui.domain

import com.bragro.mobile.data.model.ColumnConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** v416 -- confere sozinho a ordem dos campos e o "Ver mais" do app, usando o
 * cadastro real do Financeiro (mesma ordem de registry.ts da plataforma).
 * O resultado esperado é o que a plataforma mostra no Novo registro. */
class OrdemCamposTest {
    private fun col(key: String, type: String = "text", req: Boolean = false, calc: Boolean = false) =
        ColumnConfig(key = key, label = key, type = type, required = req, computed = calc)

    // Ordem do cadastro (registry.ts > financeiro).
    private val financeiro = listOf(
        col("data", "date", req = true), col("safra", "select"), col("cultura", "select"), col("local", "select"),
        col("areaHa", "number", calc = true), col("setor", "select"), col("operacao", "select", req = true),
        col("categoria", "select", req = true), col("subcategoria", "select"), col("entidade", "select"), col("docNf"),
        col("grupoRateio", calc = true), col("banco", "select"), col("formaPgto", "select"), col("periodo", "select"),
        col("parcelaS", calc = true), col("vcto", "date", calc = true), col("mesAno", calc = true),
        col("bruto", "number", req = true), col("ajuste", "number"), col("liquido", "number", calc = true),
        col("dataPgto", "date"), col("dataReceb", "date"), col("status", "select", calc = true),
        col("conciliar", "checkbox", calc = true), col("rateioPct", "number", calc = true), col("rateadoR", "number", calc = true),
        col("rateioIndiretoR", "number", calc = true), col("custoHaIndireto", "number", calc = true),
        col("conferenNf", calc = true), col("itensNf", calc = true), col("despesaFixa", "checkbox"), col("incluirDeclaracao", "checkbox"),
    )

    private fun telaNovoFinanceiro(): List<String> {
        val campos = financeiro.filter { it.key != "ajuste" } // Juros / desconto fica só no bloco
        val itensBloco = OrdemCampos.itensSaoBloco("financeiro", multiItem = false)
        val ordem = OrdemCampos.ordemNaTela(financeiro, campos, "financeiro", itensBloco)
        val base = OrdemCampos.ocultosBase(financeiro, "financeiro", registroNovo = true) { true }
        val ocultos = OrdemCampos.completarLinha(base, ordem)
        return ordem.filter { it !in ocultos }
    }

    @Test fun financeiroNovoMesmaSequenciaDaPlataforma() {
        assertEquals(
            listOf("data", "periodo", "parcelaS", "vcto", "mesAno", "safra", "cultura", "local", "areaHa", "setor", "operacao", "categoria", "subcategoria", "docNf", "bruto"),
            telaNovoFinanceiro(),
        )
    }

    @Test fun financeiroItensNaoSaoBloco() {
        assertFalse(OrdemCampos.itensSaoBloco("financeiro", multiItem = false))
        assertTrue(OrdemCampos.itensSaoBloco("pedidos", multiItem = false))
        assertTrue(OrdemCampos.itensSaoBloco("qualquer", multiItem = true))
    }

    @Test fun automaticosNaSecaoDoCampoAnterior() {
        assertEquals("quando", OrdemCampos.secaoDe(financeiro, financeiro.first { it.key == "vcto" }, "financeiro"))
        assertEquals("onde", OrdemCampos.secaoDe(financeiro, financeiro.first { it.key == "areaHa" }, "financeiro"))
    }

    @Test fun registroNovoNaoSeguraAutomaticoPreenchido() {
        // Mesmo com valor (Parcela 1/1, Status…), num registro novo vão para o Ver mais.
        val base = OrdemCampos.ocultosBase(financeiro, "financeiro", registroNovo = true) { true }
        assertTrue("status" in base)
        assertTrue("dataPgto" in base)
    }

    @Test fun editandoComCampoEscondidoPreenchidoAbreTudo() {
        val base = OrdemCampos.ocultosBase(financeiro, "financeiro", registroNovo = false) { it.key == "dataPgto" }
        assertTrue(base.isEmpty())
    }

    @Test fun formularioPequenoNaoTemVerMais() {
        assertTrue(OrdemCampos.ocultosBase(financeiro.take(10), "financeiro", registroNovo = true) { false }.isEmpty())
    }

    @Test fun completaALinhaDeCinco() {
        val ordem = listOf("a", "b", "c", "d", "e", "f", "g")
        // 2 à vista (a, b) -> completa com c, d, e.
        assertEquals(setOf("f", "g"), OrdemCampos.completarLinha(setOf("c", "d", "e", "f", "g"), ordem))
        // linha já cheia: não puxa nada.
        assertEquals(setOf("f", "g"), OrdemCampos.completarLinha(setOf("f", "g"), ordem.take(5) + listOf("f", "g")))
    }
}
