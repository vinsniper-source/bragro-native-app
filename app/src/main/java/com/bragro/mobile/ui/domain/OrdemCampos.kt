package com.bragro.mobile.ui.domain

import com.bragro.mobile.data.model.ColumnConfig

/** v416 -- regra da ordem dos campos e do "Ver mais" do formulário, num
 * arquivo puro (sem Compose) para ser testada automaticamente
 * (OrdemCamposTest.kt). É a mesma regra da plataforma (record-form.tsx):
 *  - os campos são agrupados nas seções Quando / Onde / O quê e quem / Itens /
 *    Valores / Outros; um campo automático fica na seção do campo editável
 *    anterior a ele no cadastro;
 *  - a seção Itens só vira BLOCO separado (no fim, depois do "Ver mais") nos
 *    módulos com vários itens; nos outros (ex.: Financeiro) Categoria,
 *    Subcategoria e Doc/NF são campos comuns, na sequência;
 *  - num formulário com mais de 10 campos ficam à vista os 8 primeiros do
 *    cadastro, os obrigatórios e os da seção Itens; o resto vai para o "Ver
 *    mais", e a última linha (grade de 5) é completada com os primeiros
 *    escondidos, na ordem da tela. */
object OrdemCampos {
    /** Módulos em que os itens são um bloco próprio (como na plataforma). */
    val ITENS_COMO_BLOCO = setOf("pedidos", "cotacoes", "safra", "orcamentos", "frota", "controleinterno")

    fun itensSaoBloco(domainId: String, multiItem: Boolean): Boolean = multiItem || domainId in ITENS_COMO_BLOCO

    /** Seção onde o campo aparece; automático herda a do campo editável anterior. */
    fun secaoDe(colunas: List<ColumnConfig>, c: ColumnConfig, domainId: String): String {
        if (!c.computed) return secaoDoCampo(c.key, domainId)
        val i = colunas.indexOf(c)
        val ant = colunas.subList(0, maxOf(i, 0)).lastOrNull { !it.computed && it.type != "textarea" }
        val dep = colunas.drop(i + 1).firstOrNull { !it.computed && it.type != "textarea" }
        return (ant ?: dep)?.let { secaoDoCampo(it.key, domainId) } ?: "outros"
    }

    /** Ids das seções na ordem da tela (Itens por último só quando é bloco). */
    fun ordemSecoes(itensBloco: Boolean): List<String> {
        val ids = SECOES_FORM.map { it.id } + "outros"
        return if (itensBloco) ids.filter { it != "itens" } + "itens" else ids
    }

    /** Chaves na ordem em que aparecem na tela (sem os blocos de itens). */
    fun ordemNaTela(colunas: List<ColumnConfig>, campos: List<ColumnConfig>, domainId: String, itensBloco: Boolean): List<String> =
        ordemSecoes(itensBloco)
            .filter { !(itensBloco && it == "itens") }
            .flatMap { s -> campos.filter { secaoDe(colunas, it, domainId) == s } }
            .map { it.key }

    /** Candidatos ao "Ver mais". Registro novo: nada conta como preenchido. */
    fun ocultosBase(visiveis: List<ColumnConfig>, domainId: String, registroNovo: Boolean, temValor: (ColumnConfig) -> Boolean): Set<String> {
        if (visiveis.size <= 10) return emptySet()
        fun preenchido(c: ColumnConfig) = !registroNovo && temValor(c)
        val cand = visiveis.filterIndexed { i, c -> i >= 8 && !c.required && secaoDoCampo(c.key, domainId) != "itens" }
        if (!registroNovo && cand.any { preenchido(it) }) return emptySet()
        return cand.filter { !preenchido(it) }.map { it.key }.toSet()
    }

    /** Completa a última linha da grade (5 por linha) com os primeiros escondidos. */
    fun completarLinha(ocultos: Set<String>, ordemTela: List<String>, colunas: Int = 5): Set<String> {
        if (ocultos.isEmpty()) return ocultos
        val vistos = ordemTela.count { it !in ocultos }
        val falta = (colunas - vistos % colunas) % colunas
        return ocultos - ordemTela.filter { it in ocultos }.take(falta).toSet()
    }
}
