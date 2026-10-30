package com.bragro.mobile.ui.theme

import com.bragro.mobile.R
import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

// Fonte Geist -- mesma do site. v73 -- os arquivos agora vão DENTRO do app
// (res/font, licença SIL Open Font 1.1, Vercel/basement.studio), em vez de
// baixados do Google Play Services na hora: abre igual sem internet e em
// aparelhos sem Google Play, sem depender de serviço de fora.
val GeistFontFamily = FontFamily(
    Font(R.font.geist_regular, FontWeight.Normal),
    Font(R.font.geist_medium, FontWeight.Medium),
    Font(R.font.geist_semibold, FontWeight.SemiBold),
    Font(R.font.geist_bold, FontWeight.Bold),
)

// Aplica a Geist em TODOS os estilos do Typography padrao do Material3
// (Typography() sem argumentos ja tem os tamanhos/pesos corretos pra cada
// papel -- displayLarge, titleMedium, bodySmall etc. -- so a fontFamily
// muda). Mais simples e menos propenso a erro do que redeclarar cada
// TextStyle na mao.
private val defaultTypography = Typography()
val AppTypography = Typography(
    displayLarge = defaultTypography.displayLarge.copy(fontFamily = GeistFontFamily),
    displayMedium = defaultTypography.displayMedium.copy(fontFamily = GeistFontFamily),
    displaySmall = defaultTypography.displaySmall.copy(fontFamily = GeistFontFamily),
    headlineLarge = defaultTypography.headlineLarge.copy(fontFamily = GeistFontFamily),
    headlineMedium = defaultTypography.headlineMedium.copy(fontFamily = GeistFontFamily),
    headlineSmall = defaultTypography.headlineSmall.copy(fontFamily = GeistFontFamily),
    titleLarge = defaultTypography.titleLarge.copy(fontFamily = GeistFontFamily),
    titleMedium = defaultTypography.titleMedium.copy(fontFamily = GeistFontFamily),
    titleSmall = defaultTypography.titleSmall.copy(fontFamily = GeistFontFamily),
    bodyLarge = defaultTypography.bodyLarge.copy(fontFamily = GeistFontFamily),
    bodyMedium = defaultTypography.bodyMedium.copy(fontFamily = GeistFontFamily),
    bodySmall = defaultTypography.bodySmall.copy(fontFamily = GeistFontFamily),
    labelLarge = defaultTypography.labelLarge.copy(fontFamily = GeistFontFamily),
    labelMedium = defaultTypography.labelMedium.copy(fontFamily = GeistFontFamily),
    labelSmall = defaultTypography.labelSmall.copy(fontFamily = GeistFontFamily),
)
