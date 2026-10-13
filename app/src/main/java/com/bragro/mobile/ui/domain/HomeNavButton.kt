package com.bragro.mobile.ui.domain

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/** Ação "ir para a página inicial", configurada pelo BRAgroNavHost. */
object HomeNavigator {
    var goHome: () -> Unit = {}
}

/** Substitui a seta de voltar do cabeçalho: leva direto para a Início. */
@Composable
fun HomeNavButton() {
    IconButton(onClick = { HomeNavigator.goHome() }) {
        Icon(Icons.Filled.Home, contentDescription = "Página inicial", tint = MaterialTheme.colorScheme.primary)
    }
}
