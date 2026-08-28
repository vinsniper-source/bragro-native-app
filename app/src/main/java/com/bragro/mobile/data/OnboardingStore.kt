package com.bragro.mobile.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.onboardingDataStore by preferencesDataStore(name = "bragro_onboarding")

/**
 * Onboarding leve do app nativo (Task #296/#344, auditoria 2026-08-28 item
 * 1) -- pedido do usuário: o site já tem um wizard de 3 passos pra criar a
 * organização/primeira fazenda (src/app/onboarding/), mas quem entra pelo
 * app sempre já tem conta e organização prontas (LoginScreen.kt não tem
 * cadastro nenhum, só login), então não faz sentido replicar aquele wizard
 * aqui. Este onboarding é mais simples: um tour de 3 telas mostrando pra
 * onde vai cada coisa na Início (Canvas/filtros, barra inferior de módulos,
 * captura rápida), exibido só na PRIMEIRA vez que a Início abre depois do
 * login. Mesmo padrão de persistência do ThemeStore (DataStore, já que não
 * existe localStorage no Android) -- flag booleana simples, sem
 * relação com nenhuma organização/conta específica (é por instalação do
 * app, não por usuário -- se trocar de conta no mesmo aparelho o tour não
 * volta a aparecer, o que é aceitável pra esse caso de uso).
 */
class OnboardingStore(private val context: Context) {
    private val key = booleanPreferencesKey("home_tour_seen")

    suspend fun tourSeen(): Boolean = context.onboardingDataStore.data.first()[key] ?: false

    suspend fun markTourSeen() {
        context.onboardingDataStore.edit { it[key] = true }
    }
}
