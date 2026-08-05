package com.bragro.mobile.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.bragro.mobile.data.ThemeMode

/** Botão claro/escuro/automático do cabeçalho -- réplica de ThemeToggle
 * (theme-toggle.tsx): cada toque avança pro próximo modo do ciclo
 * Automático -> Claro -> Escuro -> Automático, ícone muda conforme o modo
 * atual (Monitor/Sol/Lua no site -> BrightnessAuto/LightMode/DarkMode aqui). */
@Composable
fun ThemeToggle() {
    val context = LocalContext.current
    val mode by ThemeController.mode.collectAsState()
    val (icon, label) = when (mode) {
        ThemeMode.AUTO -> Icons.Filled.BrightnessAuto to "Automático"
        ThemeMode.LIGHT -> Icons.Filled.LightMode to "Claro"
        ThemeMode.DARK -> Icons.Filled.DarkMode to "Escuro"
    }
    IconButton(onClick = { ThemeController.cycle(context) }) {
        Icon(icon, contentDescription = "Tema: $label")
    }
}
