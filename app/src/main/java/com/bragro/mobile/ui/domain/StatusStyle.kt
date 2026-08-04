package com.bragro.mobile.ui.domain

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Espelho de STATUS_LIKE_KEY / STATUS_TONE / statusTone() / ORIGEM_LABELS em
// data-table.tsx -- é um mecanismo 100% genérico (por TEXTO do valor, não por
// domainId), então funciona igual nos ~18 módulos sem exceção: qualquer
// coluna cujo nome bate com esse padrão vira um "badge" colorido em vez de
// texto simples, e qualquer valor reconhecido no dicionário abaixo ganha a
// cor correspondente (verde/amarelo/vermelho), igual ao site.
private val STATUS_LIKE_KEY = Regex("status|^acaoRh$|^confere$|^conferenNf$|^desvio$", RegexOption.IGNORE_CASE)

private enum class Tone { GOOD, WARN, BAD }

private val STATUS_TONE: Map<String, Tone> = mapOf(
    "OK" to Tone.GOOD, "ATIVO" to Tone.GOOD, "EM DIA" to Tone.GOOD, "ATENDIDO" to Tone.GOOD, "PAGO" to Tone.GOOD,
    "RECEBIDO" to Tone.GOOD, "CONTROLADO" to Tone.GOOD, "DENTRO" to Tone.GOOD, "FINALIZADO" to Tone.GOOD, "VITALICIO" to Tone.GOOD,
    "LIBERADO" to Tone.GOOD,
    "ANDAMENTO" to Tone.WARN, "PENDENTE" to Tone.WARN, "PARCIAL" to Tone.WARN, "PROX VENC" to Tone.WARN,
    "ATENCAO" to Tone.WARN, "EM ABERTO" to Tone.WARN, "ABERTO" to Tone.WARN, "MONITORANDO" to Tone.WARN, "EM CONTROLE" to Tone.WARN, "REAVALIAR" to Tone.WARN,
    "SEM ASO" to Tone.WARN, "SEM CNH" to Tone.WARN, "SEM SEGURO" to Tone.WARN, "SEM VENCIMENTO" to Tone.WARN, "SEM FRETE" to Tone.WARN,
    "AGUARDANDO APLICAÇÃO" to Tone.WARN, "EM CARÊNCIA" to Tone.WARN,
    "ATRASADO" to Tone.BAD, "VENCIDO" to Tone.BAD, "REGULARIZAR" to Tone.BAD, "ACIMA" to Tone.BAD,
    "DESLIGADO" to Tone.BAD, "AFASTADO" to Tone.BAD, "CANCELADO" to Tone.BAD,
    // Origem do lançamento em Estoque (ver ORIGEM_LABELS abaixo) -- badge
    // verde só pra indicar "isto chegou sozinho de outro módulo".
    "PEDIDO" to Tone.GOOD, "FROTA" to Tone.GOOD, "SAFRA" to Tone.GOOD, "CONTROLE INTERNO" to Tone.GOOD,
    "NF-E" to Tone.GOOD, "EXTRATO BANCÁRIO" to Tone.GOOD, "SINCRONIZAÇÃO" to Tone.GOOD,
)

/** Traduz o código técnico gravado em EstoqueMovimento.origem (id do módulo
 * de origem, ex.: "frota") pro rótulo que o usuário reconhece -- mesmo mapa
 * de ORIGEM_LABELS em data-table.tsx. Um lançamento digitado manualmente em
 * Estoque não tem origem (fica "—"). */
private val ORIGEM_LABELS: Map<String, String> = mapOf(
    "manual" to "Manual",
    "pedidos" to "Pedido",
    "frota" to "Frota",
    "safra" to "Safra",
    "controleinterno" to "Controle Interno",
    "nfe" to "NF-e",
    "bankimport" to "Extrato Bancário",
    "estoque_sync" to "Sincronização",
)

fun isStatusLikeColumn(key: String): Boolean = STATUS_LIKE_KEY.containsMatchIn(key)

/** Mesma transformação de fmtValue() em data-table.tsx pro caso especial da
 * coluna "origem" -- as demais colunas continuam formatadas como já eram
 * (money/data/etc., ver DomainListScreen/DomainFormScreen). */
fun displayValueFor(key: String, rawValue: String): String {
    if (key == "origem") {
        ORIGEM_LABELS[rawValue.lowercase()]?.let { return it }
    }
    return rawValue
}

private fun statusTone(raw: String): Tone? {
    val v = raw.trim().uppercase()
    if (v.isEmpty() || v == "—") return null
    STATUS_TONE[v]?.let { return it }
    if (v.startsWith("⚠") || v.contains("VERIFICAR")) return Tone.BAD
    return null
}

/** Pill colorido (verde/amarelo/vermelho) pra colunas "status-like" -- mesmo
 * critério visual do StatusCell do site. Cai em texto simples (sem pill)
 * quando o valor não é reconhecido, exatamente como no site. */
@Composable
fun StatusBadge(rawValue: String) {
    val displayValue = displayValueFor("status", rawValue)
    val tone = statusTone(displayValue)
    if (tone == null) {
        Text(displayValue, style = MaterialTheme.typography.bodyMedium)
        return
    }
    val (bg, fg) = when (tone) {
        Tone.GOOD -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) to MaterialTheme.colorScheme.primary
        Tone.WARN -> Color(0xFFF2C037).copy(alpha = 0.20f) to Color(0xFF8A6D00)
        Tone.BAD -> MaterialTheme.colorScheme.error.copy(alpha = 0.15f) to MaterialTheme.colorScheme.error
    }
    Text(
        displayValue,
        style = MaterialTheme.typography.labelMedium,
        color = fg,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}
