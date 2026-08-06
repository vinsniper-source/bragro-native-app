@file:OptIn(ExperimentalTextApi::class)

package com.bragro.mobile.ui.theme

import com.bragro.mobile.R
import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.GoogleFont

// Fonte Geist -- pedido do usuario ("implemente a fonte Geist no app"),
// mesma fonte que o site ja usa (Geist Sans via next/font/google, ver
// layout.tsx). Baixada em tempo de execucao pelo provedor de fontes do
// Google Play Services (Downloadable Fonts API) em vez de arquivos .ttf
// empacotados no APK -- precisa de res/values/font_certs.xml (certificados
// publicos do app "Google Play services", copiados da amostra oficial do
// Google) e da dependencia "androidx.compose.ui:ui-text-google-fonts" (ver
// app/build.gradle.kts). Se o Play Services nao estiver disponivel no
// aparelho (ex.: emulador sem Google APIs), o Compose cai sozinho pra fonte
// padrao do sistema -- nao quebra o app, so nao mostra a Geist.
private val geistFontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

private fun geistFont(weight: FontWeight) = Font(googleFont = GoogleFont("Geist"), fontProvider = geistFontProvider, weight = weight)

val GeistFontFamily = FontFamily(
    geistFont(FontWeight.Normal),
    geistFont(FontWeight.Medium),
    geistFont(FontWeight.SemiBold),
    geistFont(FontWeight.Bold),
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
