package com.bragro.mobile.ui.domain

import android.content.Context
import androidx.compose.runtime.mutableStateOf

/**
 * Filtro global de fazenda -- equivalente ao FarmSelector do site (ver
 * components/layout/farm-selector.tsx + lib/farm-selection.ts). Escolher uma
 * fazenda aqui persiste no SharedPreferences do aparelho (nao precisa de
 * rede, funciona offline) e cada tela de modulo "farm-linked" (ver
 * FARM_LINKED_DOMAINS/FARM_LINKED_FIELD abaixo -- mesma lista/mapeamento do
 * site em lib/domains/actions.ts) usa o valor salvo aqui como filtro inicial
 * da coluna Local/Fazenda, reaproveitando o mesmo mecanismo de
 * "columnFilters" que ja existe em DomainListScreen pros filtros manuais
 * (Local/Categoria/Safra etc.) -- sem duplicar logica de filtragem nenhuma,
 * so pre-preenchendo o valor. "Todas as fazendas" (selecao null) volta ao
 * comportamento de sempre, sem filtro nenhum.
 *
 * SharedPreferences (nao DataStore) por simplicidade -- e uma unica string,
 * lida/escrita raramente (troca manual do usuario), sem justificar a
 * dependencia extra do DataStore so pra isso.
 */
object FarmSelection {
    private const val PREFS = "farm_selection"
    private const val KEY_SELECTED = "selected_farm_name"

    /** Estado observavel pelo Compose -- qualquer tela que le
     * `FarmSelection.selected.value` recompoe sozinha quando a selecao muda
     * em outra tela, sem precisar de callback/navegacao de volta. */
    val selected = mutableStateOf<String?>(null)

    /** Chamada uma vez (ex.: ao entrar em Início ou na lista de um modulo)
     * pra carregar a selecao persistida -- idempotente, seguro chamar toda
     * vez que uma tela monta. */
    fun load(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        selected.value = prefs.getString(KEY_SELECTED, null)
    }

    fun choose(context: Context, farmName: String?) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().apply {
            if (farmName.isNullOrBlank()) remove(KEY_SELECTED) else putString(KEY_SELECTED, farmName)
        }.apply()
        selected.value = farmName?.takeIf { it.isNotBlank() }
    }

    /** Limpa a selecao ao trocar de conta/organizacao (mesmo espirito do
     * limpeza de fila de sincronizacao pendente em AuthRepository.login()) --
     * uma fazenda escolhida numa organizacao nao deveria continuar filtrando
     * depois de logar em outra. Chamar no logout/login de organizacao
     * diferente. */
    fun clear(context: Context) = choose(context, null)

    // Mesma lista/mapeamento de lib/domains/actions.ts (FARM_LINKED_DOMAINS/
    // FARM_LINKED_FIELD) -- a maioria usa a coluna "local", Pragas e Clima
    // usam "fazenda". Mantido em duplicata proposital (app nativo nao
    // importa codigo do site): se um dominio novo farm-linked for adicionado
    // no site, precisa ser espelhado aqui tambem.
    private val FARM_LINKED_DOMAINS = setOf("safra", "frota", "financeiro", "colheita", "planejamentosafra", "pragas", "clima")
    private val FARM_LINKED_FIELD = mapOf("pragas" to "fazenda", "clima" to "fazenda")

    /** Nome da coluna a filtrar pelo dominio, ou null se o dominio nao for
     * farm-linked (nao mostra nem aplica o filtro global nele). */
    fun farmFieldFor(domainId: String): String? =
        if (FARM_LINKED_DOMAINS.contains(domainId)) (FARM_LINKED_FIELD[domainId] ?: "local") else null
}
