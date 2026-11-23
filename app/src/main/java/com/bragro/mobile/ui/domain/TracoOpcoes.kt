package com.bragro.mobile.ui.domain

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bragro.mobile.ui.i18n.Text

/**
 * v271 (site v314/v315/v316) -- alternar sem botão: fonte verde e um traço
 * embaixo de cada opção; a escolhida fica com o traço verde e grosso.
 * Opções alinhadas à esquerda.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun TracoOpcoes(opcoes: List<Pair<String, String>>, selecionado: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier, estilo: androidx.compose.ui.text.TextStyle? = null) {
    val cs = MaterialTheme.colorScheme
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(22.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = modifier) {
        opcoes.forEach { (valor, rotulo) ->
            val ativo = valor == selecionado
            Column(modifier = Modifier.width(IntrinsicSize.Max).clickable { onSelect(valor) }.padding(top = 4.dp)) {
                Text(
                    rotulo,
                    color = cs.onSurface, // v276 (mockup v333) -- fonte branca/preta; só o traço é verde
                    fontWeight = if (ativo) FontWeight.SemiBold else FontWeight.Normal,
                    style = estilo ?: MaterialTheme.typography.bodySmall, // v281 -- no topo usa o tamanho do título
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                Box(modifier = Modifier.fillMaxWidth().height(if (ativo) 2.dp else 1.dp).background(if (ativo) cs.primary else cs.outlineVariant))
            }
        }
    }
}
