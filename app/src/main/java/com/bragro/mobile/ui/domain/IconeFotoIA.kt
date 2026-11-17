package com.bragro.mobile.ui.domain

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * v216 (mockup aprovado, opção B) -- "foto com IA": câmera com dois brilhos
 * de IA no lugar da lente. Usado onde a foto preenche os campos sozinha
 * (Romaneio, Praga por foto, Preencher com foto...). Sem rótulo.
 */
val IconeFotoIA: ImageVector by lazy {
    ImageVector.Builder(name = "FotoIA", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .addPath(
            pathData = addPathNodes("M2 8.5a2 2 0 0 1 2-2h2.6L8.2 4h7.6l1.6 2.5H20a2 2 0 0 1 2 2V18a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2z"),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2.2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
        .addPath(
            pathData = addPathNodes("M11 9c.4 2.1 1.3 3 3.4 3.4-2.1.4-3 1.3-3.4 3.4-.4-2.1-1.3-3-3.4-3.4 2.1-.4 3-1.3 3.4-3.4z"),
            fill = SolidColor(Color.Black),
        )
        .addPath(
            pathData = addPathNodes("M15.6 8c.18.95.6 1.37 1.55 1.55-.95.18-1.37.6-1.55 1.55-.18-.95-.6-1.37-1.55-1.55.95-.18 1.37-.6 1.55-1.55z"),
            fill = SolidColor(Color.Black),
        )
        .build()
}
