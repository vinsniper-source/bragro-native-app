package com.bragro.mobile.ui.domain

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp

/** Não deixa "R$ 1.164.566,00" quebrar entre o R$ e o número. */
fun semQuebra(v: String): String = v.replace("R$ ", "R$ ").replace("US$ ", "US$ ")

/**
 * v23 -- campos do cartão em duas colunas, sem cortes: cada campo é medido
 * na largura de meia coluna ("Rótulo: valor"). Se couber em até 3 linhas,
 * fica na sua coluna (quebrando linha quando precisar); se passar disso,
 * ocupa as duas colunas.
 */
@Composable
fun <T> CamposDuasColunas(
    itens: List<T>,
    texto: (T) -> String,
    modifier: Modifier = Modifier,
    campo: @Composable (T) -> Unit,
) {
    val medidor = rememberTextMeasurer()
    val estilo = MaterialTheme.typography.bodySmall
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val gap = 12.dp
        val meia = with(androidx.compose.ui.platform.LocalDensity.current) { ((maxWidth - gap) / 2).roundToPx().coerceAtLeast(1) }
        val inteiro = itens.map { item ->
            val t = texto(item)
            val corte = t.indexOf(": ")
            if (corte >= 0) {
                // O valor inteiro precisa caber numa meia coluna (sem cortar palavra
                // nem número); senão o campo ocupa as duas colunas.
                val valor = t.substring(corte + 2)
                val largura = medidor.measure(valor, style = estilo.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), constraints = Constraints()).size.width
                largura > meia
            } else {
                val r = medidor.measure(t, style = estilo, constraints = Constraints(maxWidth = meia))
                r.lineCount > 3
            }
        }
        // Agrupa em linhas: dois campos de meia largura ou um de largura inteira.
        val linhas = mutableListOf<List<Int>>()
        var pendente: Int? = null
        itens.indices.forEach { i ->
            if (inteiro[i]) {
                pendente?.let { linhas += listOf(it) }; pendente = null
                linhas += listOf(i)
            } else if (pendente == null) {
                pendente = i
            } else {
                linhas += listOf(pendente!!, i); pendente = null
            }
        }
        pendente?.let { linhas += listOf(it) }
        Column(Modifier.fillMaxWidth()) {
            linhas.forEach { linha ->
                if (linha.size == 1 && inteiro[linha[0]]) {
                    Column(Modifier.fillMaxWidth()) { campo(itens[linha[0]]) }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                        linha.forEach { i -> Column(Modifier.weight(1f)) { campo(itens[i]) } }
                        if (linha.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
