package com.bragro.mobile.ui.domain

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp

/** Ação "ir para a página inicial", configurada pelo BRAgroNavHost. */
object HomeNavigator {
    var goHome: () -> Unit = {}
}

/** Substitui a seta de voltar do cabeçalho: leva direto para a Início. */
@Composable
fun HomeNavButton() {
    IconButton(onClick = { HomeNavigator.goHome() }) {
        IconeNegrito(Icons.Outlined.Home, contentDescription = "Página inicial", tint = MaterialTheme.colorScheme.onSurface, size = 24.dp)
    }
}

/** v18 -- seta de voltar no cabeçalho (à esquerda do título): volta para a
 * tela anterior pelo mesmo caminho do botão "voltar" do sistema. */
@Composable
fun BackNavButton() {
    val dispatcher = androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    IconButton(onClick = { dispatcher?.onBackPressed() }) {
        IconeNegrito(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Voltar", tint = MaterialTheme.colorScheme.onSurface, size = 27.dp)
    }
}
