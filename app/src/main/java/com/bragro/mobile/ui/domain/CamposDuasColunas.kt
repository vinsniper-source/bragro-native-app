package com.bragro.mobile.ui.domain

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
        // v1.6.5 (mockup v106, item 6) -- sem buracos: entre dois campos de
        // largura inteira, cada coluna é preenchida de cima para baixo e o
        // próximo campo entra na coluna que terminou mais acima (estimativa
        // em linhas). Assim uma etiqueta sobe para o vão ao lado de um campo
        // que quebrou em duas linhas, e o texto de baixo sobe junto.
        val alturas = itens.indices.map { i ->
            if (inteiro[i]) 0f else {
                val t = texto(itens[i])
                val corte = t.indexOf(": ")
                val valor = if (corte >= 0) t.substring(corte + 2) else t
                if (largura(t, false) <= meia) 1f
                else 1f + kotlin.math.ceil(largura(valor, true).toFloat() / meia).coerceAtLeast(1f)
            }
        }
        // v1.6.7 (mockup v108, item 4) -- os campos curtos vão primeiro, em
        // duas colunas equilibradas, e os de largura inteira ficam no fim:
        // assim não sobra vazio entre um campo longo e outro.
        val blocos = mutableListOf<Pair<Boolean, List<Int>>>() // (inteiro?, índices)
        val curtos = itens.indices.filter { !inteiro[it] }
        if (curtos.isNotEmpty()) blocos += false to curtos
        itens.indices.filter { inteiro[it] }.forEach { blocos += true to listOf(it) }
        Column(Modifier.fillMaxWidth()) {
            blocos.forEach { (cheio, idx) ->
                if (cheio) {
                    Column(Modifier.fillMaxWidth()) { campo(itens[idx[0]]) }
                } else {
                    val esq = mutableListOf<Int>(); val dir = mutableListOf<Int>()
                    var he = 0f; var hd = 0f
                    idx.forEach { i -> if (he <= hd) { esq += i; he += alturas[i] } else { dir += i; hd += alturas[i] } }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                        Column(Modifier.weight(1f)) { esq.forEach { campo(itens[it]) } }
                        Column(Modifier.weight(1f)) { dir.forEach { campo(itens[it]) } }
                    }
                }
            }
        }
    }
}
