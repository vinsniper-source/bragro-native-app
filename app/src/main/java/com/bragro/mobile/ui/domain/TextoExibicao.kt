package com.bragro.mobile.ui.domain

import java.util.Calendar
import java.util.Locale

// v1.6.59 -- espelho de src/lib/texto-exibicao.ts (site, v293/v294/v300):
// texto gravado TODO EM MAIÚSCULO aparece com só as iniciais maiúsculas.
// Siglas e códigos continuam em maiúsculo. Só muda a exibição: o valor
// gravado no banco continua igual.
private val PT = Locale("pt", "BR")

private val SIGLAS = setOf(
    "NF", "NFE", "NF-E", "NFS", "NFS-E", "NFSE", "CT-E", "MDF-E", "CPF", "CNPJ", "RG", "CNH", "IE", "CEP", "UF",
    "OS", "O.S.", "O.S", "NPK", "IPRO", "RR", "RR2", "BT", "SC", "TON", "KG", "L", "ML", "HA", "KM", "UN", "UND", "CX", "PC", "LT", "GL", "MT",
    "ICMS", "IPI", "PIS", "COFINS", "ISS", "IR", "INSS", "FGTS", "ITR", "CAR", "CCIR", "ART", "EPI", "INPEV", "MAPA", "ANVISA", "IBAMA", "INMET",
    "PIX", "TED", "DOC", "CIF", "FOB", "USD", "BRL", "ARS", "R$", "US$", "S/A", "SA", "ME", "MEI", "EPP", "EIRELI", "LTDA.", "CIA", "CIA.",
    "BA", "DF", "GO", "MG", "MS", "PB", "PR", "RJ", "RN", "RS", "SP",
    "II", "III", "IV", "VI", "VII", "VIII", "IX", "XI", "XII", "GPS", "RTK", "VRA", "ISO", "XML", "SHP", "PDF", "CSV", "API", "TI", "RH", "DRE", "CEPEA", "ESALQ", "B3", "CBOT",
)
private val MINUSCULAS = setOf("de", "da", "do", "das", "dos", "e", "em", "na", "no", "nas", "nos", "para", "por", "com", "a", "o", "as", "os", "ao", "à")
private val BORDAS = Regex("^[(\"'\\[]+|[)\"'\\],;:.]+$")
private val PRIMEIRA_LETRA = Regex("\\p{L}")

private fun palavra(p: String, maiuscula: Boolean, proprio: Boolean): String {
    if (p.isEmpty()) return p
    val up = p.uppercase(PT)
    val nucleo = up.replace(BORDAS, "")
    if (up in SIGLAS || nucleo in SIGLAS) {
        return when (up) {
            "NF-E", "NFE" -> p.replace(Regex("NF-?E", RegexOption.IGNORE_CASE), "NF-e")
            "NFS-E" -> "NFS-e"
            "CT-E" -> "CT-e"
            "MDF-E" -> "MDF-e"
            else -> p
        }
    }
    if (p.any { it.isDigit() }) return p
    val low = p.lowercase(PT)
    if (!maiuscula) return low
    if (proprio && low in MINUSCULAS) return low
    val m = PRIMEIRA_LETRA.find(low) ?: return low
    return low.substring(0, m.range.first) + m.value.uppercase(PT) + low.substring(m.range.last + 1)
}

private val CAMPO_PROPRIO = Regex(
    "^(local|fazenda|fazendas|propriedade|sitio|destino|origemFazenda|entidade|entidades|fornecedor|fornecedores|cliente|clientes|responsavel|responsaveis|pessoa|pessoas|colaborador|colaboradores|funcionario|motorista|operador|tecnico|agronomo|comprador|vendedor|transportadora|emitente|destinatario|favorecido|banco|bancos|marca|marcas|fabricante|cidade|municipio|nome)",
    RegexOption.IGNORE_CASE,
)

/** Campos de nome próprio: cada palavra com inicial maiúscula. */
fun ehCampoProprio(chave: String?): Boolean = !chave.isNullOrEmpty() && CAMPO_PROPRIO.containsMatchIn(chave)

private val TEM_MAIUSCULA = Regex("\\p{Lu}")
private val TEM_MINUSCULA = Regex("\\p{Ll}")
private val EMAIL = Regex("^[\\w.+-]+@[\\w.-]+$")
private val COMPOSTA = Regex("^\\p{L}+([-/]\\p{L}+)+$")
private val TOKEN = Regex("\\S+")
private val SEPARA = Regex("(?<=[-/])|(?=[-/])")
private val LETRA_OU_DIGITO = Regex("[\\p{L}\\d]")

fun exibirTexto(s: String, proprio: Boolean = false): String {
    if (s.isEmpty() || !TEM_MAIUSCULA.containsMatchIn(s) || TEM_MINUSCULA.containsMatchIn(s)) return s
    if (EMAIL.matches(s) || s.startsWith("http:", true) || s.startsWith("https:", true)) return s
    var inicio = true
    return TOKEN.replace(s) { m ->
        val p = m.value
        val cap = proprio || inicio
        val r = if (COMPOSTA.matches(p) && p.uppercase(PT) !in SIGLAS) {
            p.split(SEPARA).mapIndexed { i, x -> if (i % 2 == 1) x else palavra(x, cap && i == 0, proprio) }.joinToString("")
        } else palavra(p, cap, proprio)
        if (LETRA_OU_DIGITO.containsMatchIn(p)) inicio = p.endsWith(";")
        r
    }
}

/** Títulos montados com " · ": cada pedaço como nome próprio. */
fun exibirSegmentos(s: String?): String = s?.split(" · ")?.joinToString(" · ") { exibirTexto(it, true) } ?: ""

private val ANO_ATUAL = Calendar.getInstance().get(Calendar.YEAR).toString()
private val DATA_BR = Regex("^\\d{2}/\\d{2}/\\d{4}$")

/** Nas listas, data do ano atual sem o ano (12/11 em vez de 12/11/2026). */
fun semAnoAtual(v: String): String = if (DATA_BR.matches(v) && v.endsWith("/$ANO_ATUAL")) v.substring(0, 5) else v

/** displayValueFor para listas (sem o ano atual nas datas). */
fun displayValueLista(key: String, rawValue: String, colType: String? = null): String = semAnoAtual(displayValueFor(key, rawValue, colType))

/** v1.6.59 (site v302) -- registro a copiar para o próximo "Novo registro"
 * (ícone Copiar da janela Ver). Usado uma vez e limpo. */
object CopiaParaNovo {
    private var dominio: String? = null
    private var registro: Map<String, String?>? = null
    fun guardar(domainId: String, r: Map<String, String?>) { dominio = domainId; registro = r }
    fun pegar(domainId: String): Map<String, String?>? {
        val r = if (dominio == domainId) registro else null
        dominio = null; registro = null
        return r
    }
}
