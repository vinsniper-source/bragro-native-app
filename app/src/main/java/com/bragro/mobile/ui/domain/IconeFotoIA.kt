package com.bragro.mobile.ui.domain

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding

/**
 * v216 (mockup aprovado, opção B) -- "foto com IA": câmera com dois brilhos
 * de IA no lugar da lente. Usado onde a foto preenche os campos sozinha
 * (Romaneio, Praga por foto, Preencher com foto...). Sem rótulo.
 */
val IconeFotoIA: ImageVector by lazy {
    // v260 (mockup v257 aprovado) -- minimalista: câmera limpa (corpo + lente)
    // e o brilho de IA por fora, no canto superior direito.
    ImageVector.Builder(name = "FotoIA", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .addPath(
            pathData = addPathNodes("M2 10.5a2 2 0 0 1 2-2h2.4L8 6.2h6.4l1.6 2.3H18a2 2 0 0 1 2 2V19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2z"),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.9f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
        .addPath(
            pathData = addPathNodes("M14.4 14.6a3.4 3.4 0 1 1-6.8 0a3.4 3.4 0 1 1 6.8 0z"),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.9f,
        )
        .addPath(
            pathData = addPathNodes("M20.6 0.8c.32 1.7 1.03 2.41 2.73 2.73-1.7.32-2.41 1.03-2.73 2.73-.32-1.7-1.03-2.41-2.73-2.73 1.7-.32 2.41-1.03 2.73-2.73z"),
            fill = SolidColor(Color.Black),
        )
        .build()
}

/**
 * v260 (mockups v255/v259 aprovados) -- aviso do formulário com os ícones
 * (foto, Copiar...) na MESMA linha, à direita, quando couber; se não couber,
 * o texto usa a largura toda e os ícones descem para a linha de baixo, à direita.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@androidx.compose.runtime.Composable
fun AvisoComIcones(texto: String?, modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier, icones: @androidx.compose.runtime.Composable () -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier.then(androidx.compose.ui.Modifier.fillMaxWidth()),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(2.dp),
    ) {
        if (!texto.isNullOrBlank()) {
            androidx.compose.material3.Text(
                texto,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                modifier = androidx.compose.ui.Modifier.align(androidx.compose.ui.Alignment.CenterVertically).padding(end = 12.dp),
            )
        }
        androidx.compose.foundation.layout.Row(
            modifier = androidx.compose.ui.Modifier.weight(1f).align(androidx.compose.ui.Alignment.CenterVertically),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) { icones() }
    }
}
