package com.bragro.mobile.ui.domain

import com.bragro.mobile.data.model.LoteQrData
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * QR Code de Rastreabilidade de Colheita (Task #773) -- paridade com o site
 * (src/lib/rastreabilidade-qr.ts). Utilitário PURO (sem Compose/Android) pra
 * montar o payload do QR de cada lote (registro de Colheita já colhido),
 * mesmo prefixo "BRAGRO:LOTE:" e mesmos campos do site, pra um QR impresso
 * pelo app também ser lido corretamente por qualquer leitor que entenda o
 * formato do site (e vice-versa).
 */
private const val PREFIXO = "BRAGRO:LOTE:"

private val dataFormatterSaida = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))

/** Parseia uma data ISO (como vem da API, ex. "2026-09-01T00:00:00.000Z")
 * pro formato dd/MM/aaaa usado no QR -- mesmo toLocaleDateString("pt-BR")
 * do site. */
private fun formatarDataIso(iso: String?): String {
    if (iso.isNullOrBlank()) return "-"
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val data = parser.parse(iso.substringBefore(".").removeSuffix("Z")) ?: return "-"
        dataFormatterSaida.format(data)
    } catch (e: Exception) {
        "-"
    }
}

/** Mesmo critério do site (`${numero} kg`, sem casas decimais artificiais):
 * um valor inteiro sai "500 kg", um valor quebrado sai "500.5 kg". */
private fun formatarQuantidade(valor: Double?): String {
    if (valor == null) return "-"
    val semCasas = if (valor == Math.floor(valor)) valor.toLong().toString() else valor.toString()
    return "$semCasas kg"
}

fun buildLoteQrPayload(lote: LoteQrData): String {
    val data = formatarDataIso(lote.dataFim)
    val qtd = formatarQuantidade(lote.colhida)
    val loteCodigo = lote.id.takeLast(8).uppercase(Locale("pt", "BR"))
    return "${PREFIXO}Produto=${lote.cultura}|Safra=${lote.safra}|Origem=${lote.local}|Data=${data}|Qtd=${qtd}|Lote=${loteCodigo}"
}
