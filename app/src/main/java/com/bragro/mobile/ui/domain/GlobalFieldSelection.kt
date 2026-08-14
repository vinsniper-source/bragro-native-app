package com.bragro.mobile.ui.domain

import android.content.Context
import androidx.compose.runtime.mutableStateOf

/**
 * Filtro global genérico (Safra/Cultura) -- mesmo mecanismo de
 * [FarmSelection] (ver FarmSelection.kt), generalizado numa classe
 * reutilizável pra não duplicar a lógica de SharedPreferences duas vezes.
 * Diferente de FarmSelection (que mapeia cada domínio pra sua PRÓPRIA coluna
 * "farm-linked", ver FARM_LINKED_DOMAINS/FARM_LINKED_FIELD), aqui a coluna
 * filtrada tem sempre a MESMA key ("safra"/"cultura") em qualquer domínio
 * que a tiver -- por isso quem consome isto (DomainListScreen.kt) checa
 * direto se `config.columns` tem uma coluna com essa key, em vez de manter
 * outra lista de domínios hardcoded.
 *
 * Pedido do usuário ("crie mais dois ícones globais safra e cultura"),
 * mesmo espírito do filtro global de fazenda já existente no cabeçalho do
 * Início e de cada módulo.
 */
class GlobalFieldSelection(private val prefsName: String, private val prefsKey: String, val fieldKey: String) {
    /** Estado observável pelo Compose -- qualquer tela que lê `.selected.value`
     * recompõe sozinha quando a seleção muda em outra tela. */
    val selected = mutableStateOf<String?>(null)

    /** Idempotente, seguro chamar toda vez que uma tela monta. */
    fun load(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        selected.value = prefs.getString(prefsKey, null)
    }

    fun choose(context: Context, value: String?) {
        val prefs = context.applicationContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        prefs.edit().apply {
            if (value.isNullOrBlank()) remove(prefsKey) else putString(prefsKey, value)
        }.apply()
        selected.value = value?.takeIf { it.isNotBlank() }
    }

    /** Limpa a seleção ao trocar de conta/organização -- mesmo espírito de
     * FarmSelection.clear(). Chamar no logout/login de organização diferente. */
    fun clear(context: Context) = choose(context, null)
}

val SafraSelection = GlobalFieldSelection("safra_selection", "selected_safra", "safra")
val CulturaSelection = GlobalFieldSelection("cultura_selection", "selected_cultura", "cultura")
