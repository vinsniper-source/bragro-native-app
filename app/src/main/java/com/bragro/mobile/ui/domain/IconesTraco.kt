package com.bragro.mobile.ui.domain

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

// v44 -- Pecuária, Pastagem e Gráficos com ícone de traço (mockup v44, item 7):
// mesma espessura dos ícones Material "Outlined" usados no resto do app. Só
// traço, sem preenchimento; a cor vem do `tint` de quem desenha.

private fun iconeTraco(nome: String, vararg caminhos: String): ImageVector =
    ImageVector.Builder(name = nome, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
        caminhos.forEach { d ->
            addPath(
                pathData = addPathNodes(d),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.7f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()

val IconePecuaria: ImageVector by lazy {
    iconeTraco(
        "Pecuaria",
        "M5 9c0-2 1.5-3.5 3.5-3.5h7C17.5 5.5 19 7 19 9v3a5 5 0 0 1-5 5h-4a5 5 0 0 1-5-5z",
        "M8 5.5L6.5 3M16 5.5L17.5 3",
        "M10 11h0.01M14 11h0.01",
    )
}

val IconePastagem: ImageVector by lazy {
    iconeTraco(
        "Pastagem",
        "M4 20c0-4 2-7 4-7s2 3 2 7",
        "M10 20c0-5 2.5-9 5-9s3 4 3 9",
        "M3 20h18",
    )
}

val IconeGraficos: ImageVector by lazy {
    iconeTraco("Graficos", "M4 20V10M10 20V5M16 20v-7M22 20H2")
}
