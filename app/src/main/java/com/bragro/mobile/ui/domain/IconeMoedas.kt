package com.bragro.mobile.ui.domain

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** v1.6.11 (mockup v112b) -- ícone "moedas" igual ao da plataforma (lucide Coins). */
val IconeMoedas: ImageVector by lazy {
    val b = ImageVector.Builder(name = "Moedas", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
    listOf(
        "M14 8A6 6 0 1 1 2 8A6 6 0 1 1 14 8Z",
        "M18.09 10.37A6 6 0 1 1 10.34 18",
        "M7 6h1v4",
        "M16.71 13.88l0.7 0.71l-2.82 2.82",
    ).forEach { d ->
        b.addPath(
            pathData = PathParser().parsePathString(d).toNodes(),
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }
    b.build()
}
