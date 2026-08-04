package com.bragro.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Paleta com as cores da bandeira do Brasil, mesmo espirito visual do site
// (ver Task #62/#70 no historico do projeto) -- verde como cor principal.
// Nao-privadas (usadas tambem em ui/home/DomainVisuals.kt pra colorir cada
// secao de modulos igual ao agrupamento do site -- ver sidebar-nav.tsx).
val BrGreen = Color(0xFF2F6F4F)
val BrYellow = Color(0xFFF2C037)
val BrBlue = Color(0xFF1E4B8A)
// Quarta cor de apoio (fora da bandeira) so pra diferenciar a 4a secao
// ("Pessoas") das outras 3, que ja usam verde/amarelo/azul.
val BrOrange = Color(0xFFD9822B)

private val LightColors = lightColorScheme(
    primary = BrGreen,
    secondary = BrYellow,
    tertiary = BrBlue,
)

private val DarkColors = darkColorScheme(
    primary = BrGreen,
    secondary = BrYellow,
    tertiary = BrBlue,
)

@Composable
fun BRAgroTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}
