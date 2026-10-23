package com.bragro.mobile.ui.domain

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.bragro.mobile.ui.i18n.Text

/**
 * v45/v46 -- Ponto 1 do mockup v46: Imprimir e PDF num bloco só, com borda
 * e uma divisória vertical no meio. Usado no app todo (blocos Arquivos dos
 * módulos, Dossiê Bancário e cabeçalho de cada gráfico).
 *
 * - [rotulos]: mostra "Imprimir"/"PDF" abaixo do ícone (blocos Arquivos).
 * - [compacto]: altura reduzida (32dp), para o cabeçalho dos gráficos.
 * - [preencher]: ocupa toda a largura recebida, metade para cada lado
 *   (célula da grade do Dossiê).
 */
@Composable
fun PrintPdfSegmentedButton(
    onPrint: () -> Unit,
    onPdf: () -> Unit,
    modifier: Modifier = Modifier,
    rotulos: Boolean = true,
    compacto: Boolean = false,
    preencher: Boolean = false,
) {
    val borda = MaterialTheme.colorScheme.outlineVariant
    val forma = RoundedCornerShape(8.dp)
    Row(
        modifier = modifier
            .then(if (preencher) Modifier.fillMaxWidth() else Modifier)
            .height(IntrinsicSize.Min)
            .clip(forma)
            .border(1.dp, borda, forma),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Segmento(IconesVazados.Print, "Imprimir", rotulos, compacto, preencher, onPrint)
        Box(
            Modifier
                .padding(vertical = if (compacto) 6.dp else 8.dp)
                .width(1.dp)
                .fillMaxHeight()
                .background(borda),
        )
        Segmento(IconesVazados.PictureAsPdf, "PDF", rotulos, compacto, preencher, onPdf)
    }
}

@Composable
private fun RowScope.Segmento(
    icone: ImageVector,
    rotulo: String,
    rotulos: Boolean,
    compacto: Boolean,
    preencher: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .then(if (preencher) Modifier.weight(1f) else Modifier.widthIn(min = if (compacto) 36.dp else 44.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = if (compacto) 8.dp else 10.dp, vertical = if (compacto) 6.dp else 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(
            icone,
            contentDescription = rotulo,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(if (compacto) 18.dp else 20.dp),
        )
        if (rotulos) Text(rotulo, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
    }
}
