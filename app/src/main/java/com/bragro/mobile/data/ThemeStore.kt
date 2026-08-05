package com.bragro.mobile.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.themeDataStore by preferencesDataStore(name = "bragro_theme")

/** Espelho de ThemeMode (theme-toggle.tsx): "auto" muda sozinho (noite ou
 * preferência do sistema), "light"/"dark" ficam fixos até o usuário trocar
 * de novo. */
enum class ThemeMode { AUTO, LIGHT, DARK }

/** Persiste a preferência de tema escolhida no botão de tema -- espelho de
 * localStorage("sa-theme") no site, só que em DataStore (a mesma API já
 * usada por TokenStore) em vez de localStorage, que não existe no Android. */
class ThemeStore(private val context: Context) {
    private val key = stringPreferencesKey("mode")

    suspend fun current(): ThemeMode {
        val raw = context.themeDataStore.data.first()[key]
        return ThemeMode.entries.find { it.name == raw } ?: ThemeMode.AUTO
    }

    suspend fun save(mode: ThemeMode) {
        context.themeDataStore.edit { it[key] = mode.name }
    }
}
