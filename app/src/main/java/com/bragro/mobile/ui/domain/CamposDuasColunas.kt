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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp

/** Não deixa "R$ 1.164.566,00" quebrar entre o R$ e o número. */
fun semQuebra(v: String): String = v.replace("R$ ", "R$ ").replace("US$ ", "US$ ")

/**
 * v39 -- campos do cartão em duas colunas, sem nenhum corte.
 *
 * Um campo só fica em meia coluna quando TODAS as suas palavras cabem nela e
 * o valor inteiro também cabe. Se a maior palavra (ou o valor) não couber em
 * meia largura, o campo passa a ocupar a LINHA INTEIRA -- é isso que impede
 * o texto de virar uma coluna de uma letra por linha.
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
    val estiloValor = estilo.copy(fontWeight = FontWeight.Bold)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val gap = 12.dp
        val meia = with(androidx.compose.ui.platform.LocalDensity.current) { ((maxWidth - gap) / 2).roundToPx().coerceAtLeast(1) }
        fun largura(s: String, negrito: Boolean): Int =
            medidor.measure(s, style = if (negrito) estiloValor else estilo, constraints = Constraints()).size.width
        val inteiro = itens.map { item ->
            val t = texto(item)
            val corte = t.indexOf(": ")
            val rot = if (corte >= 0) t.substring(0, corte + 1) else t
            val valor = if (corte >= 0) t.substring(corte + 2) else ""
            // 1) a maior palavra do rótulo ou do valor precisa caber em meia coluna;
            // 2) o valor inteiro também precisa caber, para não ficar espremido.
            val maiorPalavra = maxOf(
                rot.split(' ', '/').maxOfOrNull { largura(it, false) } ?: 0,
                valor.split(' ', '/').maxOfOrNull { largura(it, true) } ?: 0,
            )
            val valorInteiro = if (valor.isBlank()) 0 else largura(valor, true)
            maiorPalavra > meia || valorInteiro > meia
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
