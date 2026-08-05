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
import java.text.NumberFormat
import java.util.Locale

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

private val ISO_DATE_PREFIX = Regex("^(\\d{4})-(\\d{2})-(\\d{2})")

/** Espelho de fmtValue() em data-table.tsx: colunas "origem" viram rótulo
 * amigável, colunas "date" viram dd/MM/yyyy (extraído direto da string ISO
 * -- sem passar por java.util.Date/fuso-horário, pra nunca "voltar um dia"
 * dependendo do fuso do aparelho, mesmo motivo do site usar getters UTC em
 * vez de toLocaleDateString) e colunas "number" (não-money) ganham o
 * separador de milhar/decimal pt-BR. "colType" só chega quando o chamador
 * tem o ColumnConfig à mão (StatusBadge não tem -- por isso é opcional e
 * default null, sem alterar esse outro uso). */
fun displayValueFor(key: String, rawValue: String, colType: String? = null): String {
    if (key == "origem") {
        ORIGEM_LABELS[rawValue.lowercase()]?.let { return it }
    }
    if (colType == "date") {
        ISO_DATE_PREFIX.find(rawValue)?.let { m ->
            val (y, mo, d) = m.destructured
            return "$d/$mo/$y"
        }
    }
    if (colType == "number") {
        rawValue.toDoubleOrNull()?.let { n ->
            return NumberFormat.getNumberInstance(PT_BR_MONEY).apply { maximumFractionDigits = 2 }.format(n)
        }
    }
    return rawValue
}

private val PT_BR_MONEY = Locale("pt", "BR")

/** Formata um valor monetário sincronizado (sempre chega como número puro do
 * servidor, ex.: "1234.56") como "R$ 1.234,56" -- usada em TODOS os lugares
 * que mostram uma coluna "money" (evita cada tela reinventar a formatação
 * na mão, que era a causa do "R$" aparecer duas vezes quando o valor bruto
 * já vinha com algum "R$"/espaço embutido). Também tolera valores digitados
 * com vírgula ou já contendo "R$" -- limpa tudo antes de reformatar do zero,
 * então nunca duplica o símbolo, não importa a origem do texto. */
fun formatMoneyValue(rawValue: String): String {
    val stripped = rawValue.replace("R$", "", ignoreCase = true).trim()
    // O servidor manda Decimal.toString() puro (ex.: "1234.56", ponto como
    // decimal, SEM separador de milhar) -- só quando o texto já tem vírgula
    // (alguem digitou/colou no formato pt-BR, ex.: "1.234,56") é que o ponto
    // vira separador de milhar a remover; sem vírgula, o ponto já É o
    // decimal e não pode ser removido (senão "1234.56" viraria "123456").
    val normalized = if (stripped.contains(",")) stripped.replace(".", "").replace(",", ".") else stripped
    val n = normalized.toDoubleOrNull()
    return if (n == null) rawValue else NumberFormat.getCurrencyInstance(PT_BR_MONEY).format(n)
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
