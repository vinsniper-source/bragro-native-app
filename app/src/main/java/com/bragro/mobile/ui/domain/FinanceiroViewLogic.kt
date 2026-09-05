package com.bragro.mobile.ui.domain

// Espelho de FinanceiroView (lib/domains/actions.ts) + buildFinanceiroViewWhere
// + computeFluxoRows (data-table.tsx) -- só que aplicado em memória sobre os
// registros já sincronizados no aparelho (Room), em vez de uma query no
// servidor. Os campos calculados (rateadoR/rateioIndiretoR/custoHaIndireto/
// conciliar/status etc.) já vêm prontos do servidor dentro de cada registro
// -- nada é recalculado aqui, só filtrado/ordenado/exibido. Isso funciona
// 100% offline (mesmo critério do resto do app), sem precisar de nenhum
// endpoint novo.
enum class FinanceiroView(val label: String) {
    TODOS("Todos"),
    PAGAR("Contas a Pagar"),
    RECEBER("Contas a Receber"),
    CONCILIADO("Conciliado"),
    FLUXO("Fluxo de Caixa"),
    RATEIO_DIRETO("Rateio Direto"),
    RATEIO_INDIRETO("Rateio Indireto"),
}

// Mesma lista de OPERACOES_SEM_RATEIO/receita usada em buildFinanceiroViewWhere
// pra decidir Pagar (despesa, ainda sem dataPgto) vs Receber (receita, ainda
// sem dataReceb) -- comparação sem acento/maiúscula pra tolerar variações de
// digitação na lista suspensa "Operação".
private val RECEITA_OPS = setOf("venda", "recebimento", "resgate", "estorno", "devolucao", "devolução")

// Não é mais "private": também usada por FinanceiroFieldLine/FluxoCard
// (FinanceiroScreen.kt) pra decidir a cor da fonte de Bruto/Liquido -- verde
// (receita) / laranja-âmbar (despesa), mesmo pedido do usuário aplicado no
// site (ver financeiroMoneyColorClass em data-table.tsx).
fun isReceitaOp(operacao: String?): Boolean {
    val v = operacao?.trim()?.lowercase() ?: return false
    return RECEITA_OPS.contains(v)
}

fun filterByFinanceiroView(records: List<Map<String, String?>>, view: FinanceiroView): List<Map<String, String?>> =
    when (view) {
        FinanceiroView.TODOS -> records
        FinanceiroView.PAGAR -> records.filter { it["dataPgto"].isNullOrBlank() && !isReceitaOp(it["operacao"]) }
        FinanceiroView.RECEBER -> records.filter { it["dataReceb"].isNullOrBlank() && isReceitaOp(it["operacao"]) }
        FinanceiroView.CONCILIADO -> records.filter { it["conciliar"] == "true" }
        FinanceiroView.FLUXO -> records.filter { it["conciliar"] == "true" }
        FinanceiroView.RATEIO_DIRETO -> records.filter { !it["rateadoR"].isNullOrBlank() && it["rateioIndiretoR"].isNullOrBlank() }
        FinanceiroView.RATEIO_INDIRETO -> records.filter { !it["rateioIndiretoR"].isNullOrBlank() }
    }

/** Espelho de FINANCEIRO_VIEW_COLUMN_KEYS (data-table.tsx) -- substitui o
 * seletor de Colunas do usuário enquanto uma visão rápida está ativa (null
 * = TODOS/FLUXO, que usam layout próprio). */
val FINANCEIRO_VIEW_COLUMN_KEYS: Map<FinanceiroView, List<String>> = mapOf(
    FinanceiroView.PAGAR to listOf("data", "vcto", "periodo", "parcelaS", "entidade", "categoria", "subcategoria", "docNf", "banco", "formaPgto", "bruto", "liquido", "status"),
    FinanceiroView.RECEBER to listOf("data", "vcto", "periodo", "parcelaS", "entidade", "categoria", "subcategoria", "docNf", "bruto", "liquido", "status"),
    FinanceiroView.CONCILIADO to listOf("data", "entidade", "categoria", "dataPgto", "dataReceb", "banco", "formaPgto", "liquido", "status"),
    FinanceiroView.RATEIO_DIRETO to listOf("data", "periodo", "parcelaS", "local", "categoria", "bruto", "areaHa", "rateioPct", "rateadoR"),
    FinanceiroView.RATEIO_INDIRETO to listOf("data", "periodo", "parcelaS", "local", "categoria", "bruto", "rateioIndiretoR", "custoHaIndireto"),
)

data class FluxoRow(
    val original: Map<String, String?>,
    val dataMovimento: String?,
    val entrada: Double,
    val saida: Double,
    val saldoAcumulado: Double,
)

/** Mesma lógica de computeFluxoRows (data-table.tsx). Os registros de entrada
 * devem já estar filtrados (conciliar=true) e ordenados por vcto ASC -- ver
 * chamada em FinanceiroScreen.kt -- senão o saldo acumulado sai errado
 * (é uma soma corrida na ordem em que os registros são processados). */
fun computeFluxoRows(records: List<Map<String, String?>>): List<FluxoRow> {
    var saldo = 0.0
    return records.map { r ->
        val dataMovimento = r["dataReceb"]?.takeIf { it.isNotBlank() }
            ?: r["dataPgto"]?.takeIf { it.isNotBlank() }
            ?: r["vcto"]
        val liquido = (r["liquido"]?.takeIf { it.isNotBlank() } ?: r["bruto"])?.toDoubleOrNull() ?: 0.0
        val entrada = if (!r["dataReceb"].isNullOrBlank()) liquido else 0.0
        val saida = if (r["dataReceb"].isNullOrBlank() && !r["dataPgto"].isNullOrBlank()) liquido else 0.0
        saldo += entrada - saida
        FluxoRow(original = r, dataMovimento = dataMovimento, entrada = entrada, saida = saida, saldoAcumulado = saldo)
    }
}

/** Espelho de dateWeightClass (data-table.tsx): dentro de Financeiro, a
 * coluna "vcto" fica em negrito e "data" em peso normal -- nenhuma outra
 * coluna/domínio é afetada. */
fun isFinanceiroBoldColumn(key: String): Boolean = key == "vcto"
