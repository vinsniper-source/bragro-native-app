package com.bragro.mobile.ui.domain

import java.util.Locale

// Espelho 1:1 de src/lib/services/bankimport.ts -- roda no aparelho porque é
// puramente aritmética/regex sobre o texto do CSV (sem acesso a banco de
// dados), igual ao preview client-side que o site já faz antes de confirmar.
// A dedup (getExistingSignatures) e a gravação (confirmBankImportAction)
// continuam no servidor -- ver BankImportRepository.kt/ o novo endpoint
// /api/mobile/bank-import.

fun detectDelimiter(sample: String): Char {
    val counts = mapOf(
        ';' to sample.count { it == ';' },
        ',' to sample.count { it == ',' },
        '\t' to sample.count { it == '\t' },
    )
    return counts.maxByOrNull { it.value }?.key ?: ';'
}

/** "1.234,56" | "(1.234,56)" | "R$ 1.234,56" -> -1234.56 / 1234.56 */
fun parseBrNumber(raw: String): Double {
    var s = raw.trim().replace(Regex("R\\$\\s?", RegexOption.IGNORE_CASE), "")
    var negative = false
    if (s.startsWith("(") && s.endsWith(")")) {
        negative = true
        s = s.substring(1, s.length - 1)
    }
    if (s.startsWith("-")) {
        negative = true
        s = s.substring(1)
    }
    s = s.replace(".", "").replace(",", ".")
    val n = s.toDoubleOrNull() ?: return 0.0
    return if (negative) -n else n
}

private val DATE_DDMMYYYY_SLASH = Regex("^(\\d{2})/(\\d{2})/(\\d{4})$")
private val DATE_DDMMYYYY_DASH = Regex("^(\\d{2})-(\\d{2})-(\\d{4})$")
private val DATE_YYYYMMDD_DASH = Regex("^(\\d{4})-(\\d{2})-(\\d{2})$")

/** Retorna a data ISO (yyyy-MM-dd) ou null se não reconhecida. */
fun parseBrDate(raw: String): String? {
    val s = raw.trim()
    DATE_DDMMYYYY_SLASH.matchEntire(s)?.let { m ->
        val (d, mo, y) = m.destructured
        return isoOrNull(y.toInt(), mo.toInt(), d.toInt())
    }
    DATE_DDMMYYYY_DASH.matchEntire(s)?.let { m ->
        val (d, mo, y) = m.destructured
        return isoOrNull(y.toInt(), mo.toInt(), d.toInt())
    }
    DATE_YYYYMMDD_DASH.matchEntire(s)?.let { m ->
        val (y, mo, d) = m.destructured
        return isoOrNull(y.toInt(), mo.toInt(), d.toInt())
    }
    return null
}

private fun isoOrNull(y: Int, mo: Int, d: Int): String? {
    if (mo !in 1..12 || d !in 1..31 || y < 1900) return null
    return "%04d-%02d-%02d".format(y, mo, d)
}

data class ColumnRoles(val dateIdx: Int, val descIdx: Int, val valueIdx: Int?, val debitIdx: Int?, val creditIdx: Int?)

/** Auto-detecta o papel de cada coluna pelo nome do cabeçalho -- mesmos
 * padrões de guessColumnRoles (bankimport.ts). */
fun guessColumnRoles(headers: List<String>): ColumnRoles {
    val norm = headers.map { it.lowercase(Locale.ROOT).trim() }
    fun find(patterns: List<Regex>) = norm.indexOfFirst { h -> patterns.any { it.containsMatchIn(h) } }

    val dateIdx = find(listOf(Regex("data"), Regex("date")))
    val descIdx = find(listOf(Regex("hist[oó]rico"), Regex("descri[cç][aã]o"), Regex("memo"), Regex("lan[cç]amento")))
    val valueIdx = find(listOf(Regex("^valor$"), Regex("valor \\(r\\$\\)"), Regex("amount")))
    val debitIdx = find(listOf(Regex("d[eé]bito"), Regex("sa[ií]da")))
    val creditIdx = find(listOf(Regex("cr[eé]dito"), Regex("entrada")))

    return ColumnRoles(
        dateIdx = if (dateIdx >= 0) dateIdx else 0,
        descIdx = if (descIdx >= 0) descIdx else 1,
        valueIdx = if (valueIdx >= 0) valueIdx else null,
        debitIdx = if (debitIdx >= 0) debitIdx else null,
        creditIdx = if (creditIdx >= 0) creditIdx else null,
    )
}

data class ParsedBankRow(val dataIso: String, val descricao: String, val valor: Double)

/** Mesma lógica de parseRows (bankimport.ts). */
fun parseBankRows(rows: List<List<String>>, roles: ColumnRoles): List<ParsedBankRow> {
    val out = mutableListOf<ParsedBankRow>()
    for (row in rows) {
        val dateRaw = row.getOrNull(roles.dateIdx) ?: continue
        val desc = row.getOrNull(roles.descIdx) ?: continue
        if (dateRaw.isBlank() || desc.isBlank()) continue
        val dataIso = parseBrDate(dateRaw) ?: continue

        var valor = 0.0
        if (roles.valueIdx != null) {
            valor = parseBrNumber(row.getOrNull(roles.valueIdx) ?: "0")
        } else if (roles.debitIdx != null || roles.creditIdx != null) {
            val debito = roles.debitIdx?.let { parseBrNumber(row.getOrNull(it) ?: "0") } ?: 0.0
            val credito = roles.creditIdx?.let { parseBrNumber(row.getOrNull(it) ?: "0") } ?: 0.0
            valor = credito - kotlin.math.abs(debito)
        }
        if (valor == 0.0) continue

        out.add(ParsedBankRow(dataIso = dataIso, descricao = desc.trim(), valor = valor))
    }
    return out
}

/** Assinatura para dedup: banco + data + valor em centavos + direção -- mesmo
 * critério de rowSignature (bankimport.ts), usada só pra comparar contra o
 * que o servidor já devolveu em getExistingSignatures. */
fun rowSignature(banco: String, row: ParsedBankRow): String {
    val cents = Math.round(kotlin.math.abs(row.valor) * 100)
    val dir = if (row.valor >= 0) "C" else "D"
    return "$banco|${row.dataIso}|$cents|$dir"
}

/** Parser de linha CSV bem simples (sem aspas escapadas complexas) -- cobre o
 * caso comum de extrato bancário exportado (sem campos multi-linha). Splita
 * pelo delimitador detectado, removendo aspas ao redor de cada campo. */
fun parseCsvLine(line: String, delimiter: Char): List<String> =
    line.split(delimiter).map { it.trim().trim('"') }
