package com.bragro.mobile.ui.domain

/**
 * Filtro "de passagem" pra abrir a lista de um domínio já filtrada por uma
 * coluna específica, vindo de outra tela (ex.: "Pedido rápido" em Controle
 * de Insumos) -- réplica do que o site faz de verdade em "/m/pedidos?item=X"
 * (ver initialFilters em m/[domain]/page.tsx): ao contrário do que a
 * auditoria descreveu ("Pedido rápido não pré-preenche o item"), o site NÃO
 * pré-preenche um formulário -- ele abre a LISTA de Pedidos já filtrada pelo
 * item, pra você ver rapidinho se já existe pedido em aberto pra aquele item
 * antes de lançar um novo. O app antes pulava direto pro formulário em
 * branco (perdendo esse "veja o que já existe primeiro"), sem nem usar o
 * item recebido. Corrigido replicando o mesmo comportamento do site.
 *
 * Objeto simples (não SharedPreferences, ver FarmSelection.kt pro contraste)
 * porque é estritamente um valor de UMA viagem: setado pela tela de origem
 * antes de navegar, lido e IMEDIATAMENTE limpo pela tela de destino ao
 * montar -- nunca deve sobreviver a um segundo uso (senão o próximo "abrir
 * Pedidos" pelo menu normal apareceria filtrado por engano).
 */
object PendingDomainFilter {
    private var domainId: String? = null
    private var columnKey: String? = null
    private var value: String? = null

    fun set(domainId: String, columnKey: String, value: String) {
        this.domainId = domainId
        this.columnKey = columnKey
        this.value = value
    }

    /** Consome (lê e limpa) o filtro pendente SE for pro domainId pedido --
     * chamar ao montar a tela de destino. Retorna null se não havia nada
     * pendente pra este domínio (uso normal, sem filtro nenhum). */
    fun consume(forDomainId: String): Pair<String, String>? {
        if (domainId != forDomainId) return null
        val key = columnKey ?: return null
        val v = value ?: return null
        domainId = null
        columnKey = null
        value = null
        return key to v
    }
}
