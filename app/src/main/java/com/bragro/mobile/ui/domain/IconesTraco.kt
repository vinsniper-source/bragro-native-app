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

// v46 -- Pecuária (opção A do mockup): cabeça de boi de frente, ocupando a
// altura toda do quadro para ficar na mesma altura do título.
val IconePecuaria: ImageVector by lazy {
    iconeTraco(
        "Pecuaria",
        // chifres
        "M2.5 5.5c1.8 0 3.6.8 4.8 2.2M21.5 5.5c-1.8 0-3.6.8-4.8 2.2",
        // orelhas
        "M7.3 8.5 4 10.5M16.7 8.5l3.3 2",
        // cabeça
        "M7.3 7.7h9.4v6.6a4.7 4.7 0 0 1-9.4 0z",
        // focinho e olhos
        "M9.6 16.6h4.8M9.7 11.6h0.01M14.3 11.6h0.01",
    )
}

// v46 -- Pastagem (opção A do mockup): touceira de capim sobre o chão,
// do rodapé ao topo do quadro.
val IconePastagem: ImageVector by lazy {
    iconeTraco(
        "Pastagem",
        "M3 20h18",
        "M6 20c0-3-1-6-3-8",
        "M6 20c0-4 1-8 3-11",
        "M12 20V5",
        "M12 20c0-3 2-6 4-8",
        "M18 20c0-3 1-6 3-8",
    )
}

val IconeGraficos: ImageVector by lazy {
    iconeTraco("Graficos", "M4 20V10M10 20V5M16 20v-7M22 20H2")
}

// v45 -- Ícone hollow (só traço) para "Ajustar Físico x Fiscal":
// balança de dois pratos desequilibrada (lado fiscal mais alto, lado físico
// mais baixo) sinalizando a reconciliação entre estoque real e fiscal.
val IconeAjustarFisicoFiscal: ImageVector by lazy {
    iconeTraco(
        "AjustarFisicoFiscal",
        "M12 4v16",
        "M5 8h14",
        "M5 8l-2 4h4l-2-4z",
        "M19 8l-2 6h4l-2-6z",
        "M9 20h6",
    )
}

/**
 * v46 -- ícones minimalistas vazados (só traço) que substituem, no app todo,
 * os ícones Material "Outlined" que na prática saem preenchidos (blocos
 * cheios, letras "PDF", quadrados do QR, etc.). Mesma espessura e cantos
 * arredondados dos ícones de traço acima.
 */
object IconesVazados {
    private const val CIRCULO = "M12 2a10 10 0 1 0 0 20a10 10 0 1 0 0-20z"
    private const val QUADRO = "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z"

    val Print: ImageVector by lazy {
        iconeTraco(
            "Print",
            "M6 9V3h12v6",
            "M6 18H4a2 2 0 0 1-2-2v-5a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2h-2",
            "M6 14h12v7H6z",
        )
    }
    val PictureAsPdf: ImageVector by lazy {
        iconeTraco(
            "PictureAsPdf",
            "M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z",
            "M14 2v6h6",
            "M8 13h8M8 17h5",
        )
    }
    val ViewList: ImageVector by lazy { iconeTraco("ViewList", "M8 6h13M8 12h13M8 18h13", "M3.5 6h0.01M3.5 12h0.01M3.5 18h0.01") }
    val ViewColumn: ImageVector by lazy { iconeTraco("ViewColumn", QUADRO, "M9 3v18M15 3v18") }
    val TableChart: ImageVector by lazy { iconeTraco("TableChart", QUADRO, "M3 9h18M9 9v12") }
    val FilterAlt: ImageVector by lazy { iconeTraco("FilterAlt", "M22 3H2l8 9.46V19l4 2v-8.54z") }
    val FilterAltOff: ImageVector by lazy { iconeTraco("FilterAltOff", "M22 3H2l8 9.46V19l4 2v-8.54z", "M2 2l20 20") }
    val OfflineBolt: ImageVector by lazy { iconeTraco("OfflineBolt", CIRCULO, "M13 6l-5 7h4l-1 5 5-7h-4z") }
    val Agriculture: ImageVector by lazy {
        iconeTraco(
            "Agriculture",
            "M5 9V5h4l1.5 4",
            "M3 15V9h8l2 4h5a2 2 0 0 1 2 2v1",
            "M4 17a3 3 0 1 0 6 0a3 3 0 1 0-6 0z",
            "M15 18a2 2 0 1 0 4 0a2 2 0 1 0-4 0z",
            "M10 17h5",
        )
    }
    val Storage: ImageVector by lazy {
        iconeTraco(
            "Storage",
            "M3 5c0-1.7 4-3 9-3s9 1.3 9 3-4 3-9 3-9-1.3-9-3z",
            "M3 5v14c0 1.7 4 3 9 3s9-1.3 9-3V5",
            "M3 12c0 1.7 4 3 9 3s9-1.3 9-3",
        )
    }
    val QrCode: ImageVector by lazy {
        iconeTraco(
            "QrCode",
            "M3 3h7v7H3z", "M14 3h7v7h-7z", "M3 14h7v7H3z",
            "M14 14h3v3h-3z", "M20 14v0.01", "M14 20h0.01", "M17 20h4v-3",
        )
    }
    val FlightTakeoff: ImageVector by lazy {
        iconeTraco(
            "FlightTakeoff",
            "M2 21h20",
            "M3 13l2 4 13.5-6.5a2 2 0 0 0-1.7-3.6L12.5 9 6 5.5 4.5 6.3 8.5 11 5 12.5 3.5 11.5z",
        )
    }
    val BugReport: ImageVector by lazy {
        iconeTraco(
            "BugReport",
            "M8 2l1.9 1.9M16 2l-1.9 1.9",
            "M9 7V6a3 3 0 0 1 6 0v1",
            "M12 20c-3.3 0-6-2.7-6-6v-3a4 4 0 0 1 4-4h4a4 4 0 0 1 4 4v3c0 3.3-2.7 6-6 6z",
            "M12 20v-9",
            "M6 13H2M22 13h-4M3 7c0 2 1.5 3.5 3.3 3.8M21 7c0 2-1.5 3.5-3.3 3.8M3 21c0-2 1.6-3.7 3.6-4M21 21c0-2-1.6-3.7-3.6-4",
        )
    }
    val Balance: ImageVector by lazy {
        iconeTraco(
            "Balance",
            "M12 3v18", "M7 21h10", "M3 7h18",
            "M2 16l3-8 3 8c-.9.6-1.9 1-3 1s-2.1-.4-3-1z",
            "M16 16l3-8 3 8c-.9.6-1.9 1-3 1s-2.1-.4-3-1z",
        )
    }
    val Opacity: ImageVector by lazy {
        iconeTraco(
            "Opacity",
            "M12 22a7 7 0 0 0 7-7c0-2-1-3.9-3-5.5S12.5 5.5 12 3c-.5 2.5-2 4.9-4 6.5S5 13 5 15a7 7 0 0 0 7 7z",
            "M5.5 15h13",
        )
    }
    val Apps: ImageVector by lazy {
        iconeTraco(
            "Apps",
            "M4 4h4v4H4z", "M10 4h4v4h-4z", "M16 4h4v4h-4z",
            "M4 10h4v4H4z", "M10 10h4v4h-4z", "M16 10h4v4h-4z",
            "M4 16h4v4H4z", "M10 16h4v4h-4z", "M16 16h4v4h-4z",
        )
    }
    val Analytics: ImageVector by lazy { iconeTraco("Analytics", QUADRO, "M8 17v-4M12 17V8M16 17v-6") }
    val Assessment: ImageVector by lazy { iconeTraco("Assessment", QUADRO, "M8 17v-4M12 17V8M16 17v-6") }
    val Insights: ImageVector by lazy { iconeTraco("Insights", "M3 17l6-6 4 4 8-8", "M15 7h6v6") }
    val Grass: ImageVector get() = IconePastagem
    val StopCircle: ImageVector by lazy { iconeTraco("StopCircle", CIRCULO, "M9 9h6v6H9z") }
    val PlayCircle: ImageVector by lazy { iconeTraco("PlayCircle", CIRCULO, "M10 8l6 4-6 4z") }
    val RadioButtonChecked: ImageVector by lazy { iconeTraco("RadioButtonChecked", CIRCULO, "M8 12a4 4 0 1 0 8 0a4 4 0 1 0-8 0z") }
    val Home: ImageVector by lazy { iconeTraco("Home", "M3 10l9-7 9 7v10a1 1 0 0 1-1 1h-5v-7H9v7H4a1 1 0 0 1-1-1z") }
    val AccountBalance: ImageVector by lazy {
        iconeTraco("AccountBalance", "M3 21h18", "M6 18v-7M10 18v-7M14 18v-7M18 18v-7", "M12 2l8 5H4z")
    }
}
