package com.bragro.mobile.ui.domain

/**
 * QR Code no abastecimento (Task #602) -- paridade com o site (lib/frota-qr.ts).
 * Utilitário PURO (sem Compose/Android) pra montar/ler o payload do QR de
 * cada máquina/frota. Mesmo prefixo "BRAGRO:FROTA:" do site, pra um QR
 * impresso pelo app também funcionar se lido pelo site (e vice-versa).
 */
private const val PREFIXO = "BRAGRO:FROTA:"

fun buildFrotaQrPayload(valorFrota: String): String = "$PREFIXO$valorFrota"

/** Casa o texto lido do QR contra a lista real de frotas cadastradas --
 * nunca aceita um valor não reconhecido (evita preencher o campo com lixo
 * se o QR for de outra coisa ou estiver corrompido). Case-insensitive/trim,
 * mesmo critério do site. */
fun parseFrotaQrPayload(texto: String, frotasValidas: List<String>): String? {
    val semPrefixo = if (texto.startsWith(PREFIXO)) texto.removePrefix(PREFIXO) else texto
    val alvo = semPrefixo.trim()
    return frotasValidas.firstOrNull { it.trim().equals(alvo, ignoreCase = true) }
}
