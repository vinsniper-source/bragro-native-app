package com.bragro.mobile.ui.livrocaixa

import com.bragro.mobile.data.model.LivroCaixaData
import java.util.Locale

// Exportação do .txt do LCDPR (Livro Caixa Digital do Produtor Rural) no app
// nativo (Task #645 no site, réplica mobile pedida pelo usuário: "botão
// Exportar LCDPR (.txt) adicionado na toolbar de Livro Caixa"). PORTA FIEL
// de gerarLcdprTxt (sistema-agro-nextjs/src/lib/services/lcdpr-txt.ts) --
// mesmo leiaute de registros delimitados por pipe (família SPED/LCDPR, IN
// RFB 1.848/2018): 0000 (abertura/declarante), 0010 (forma de apuração),
// 0035 (imóveis), 0040 (contas), Q100 (lançamentos), Q200 (resumo mensal),
// 9900/9999 (contagem/encerramento). Sem runtime compartilhado entre site e
// app -- qualquer mudança de leiaute em lcdpr-txt.ts precisa ser replicada
// aqui manualmente.
//
// MESMO limite de escopo já documentado no site: leiaute geral pronto (90%
// do trabalho), mas ID_PART (CPF/CNPJ da contraparte), CAR do imóvel e
// endereço completo do declarante ainda não são capturados pelo sistema e
// saem em branco, para o contador completar antes da transmissão formal via
// PVA/e-CAC.

/** Declarante do LCDPR -- réplica exata de LcdprDeclarante (lcdpr-txt.ts). */
data class LcdprDeclarante(
    val nome: String,
    val cpf: String, // somente dígitos -- LCDPR é específico de Pessoa Física (Lei 8.023/1990)
    val cnpj: String, // usado só como identificação complementar quando não há CPF
    val inscricaoEstadual: String,
)

private fun soDigitos(v: String?): String = (v ?: "").filter { it.isDigit() }

/** Formata dia/mês/ano em "ddMMyyyy" (padrão numérico dos leiautes
 * SPED/LCDPR), a partir de um ISO já vindo do servidor (mesmo formato que
 * LivroCaixaLancamentoData.data já usa). Mesmo parser de formatDataBr
 * (LivroCaixaScreen.kt), só que sem as barras -- com o mesmo fallback pros
 * 10 primeiros caracteres (yyyy-MM-dd) se não vier com hora/offset. */
private fun ddmmaaaaFromIso(iso: String): String {
    try {
        val d = java.time.OffsetDateTime.parse(iso)
        return "%02d%02d%04d".format(d.dayOfMonth, d.monthValue, d.year)
    } catch (e: Exception) {
        val partes = iso.take(10).split("-")
        if (partes.size == 3) {
            val ano = partes[0].toIntOrNull() ?: 1970
            val mes = partes[1].toIntOrNull() ?: 1
            val dia = partes[2].toIntOrNull() ?: 1
            return "%02d%02d%04d".format(dia, mes, ano)
        }
        return "01011970"
    }
}

/** Valor em string com vírgula, padrão numérico SPED/LCDPR (ex.: 1234.5 ->
 * "1234,50"). Locale.ROOT fixo (nunca o locale do aparelho) -- senão em
 * aparelhos configurados em pt-BR o "%.2f" já sairia com vírgula e o
 * .replace(".", ",") subsequente não teria efeito nenhum, mas em outros
 * locales (ex.: en-US do emulador) sairia com ponto errado. */
private fun valorLcdpr(v: Double): String = String.format(Locale.ROOT, "%.2f", v).replace(".", ",")

private fun linha(campos: List<String>): String = "|" + campos.joinToString("|") + "|"

/** Extrai o código do registro (ex.: "Q100") do início de uma linha já
 * montada (entre o primeiro e o segundo "|") -- usado só pra contagem do
 * bloco 9900 abaixo. */
private fun regDaLinha(linhaMontada: String): String =
    linhaMontada.removePrefix("|").substringBefore("|")

/**
 * Gera o conteúdo do .txt do LCDPR pro ano/filtro já calculados em
 * [resultado] (mesmo objeto que a rota /api/mobile/livro-caixa devolve).
 * Cada IMÓVEL distinto e cada CONTA distinta viram um registro 0035/0040
 * próprio, na ordem em que aparecem nos lançamentos -- os mesmos códigos
 * sequenciais (IM001, IM002.../CT001, CT002...) são usados dentro dos
 * registros Q100 pra referenciar o imóvel/conta de cada lançamento.
 */
fun gerarLcdprTxt(declarante: LcdprDeclarante, resultado: LivroCaixaData): String {
    val linhas = mutableListOf<String>()

    val anoIni = "01" + "01" + resultado.ano.toString()
    val anoFim = "31" + "12" + resultado.ano.toString()

    val cpf = soDigitos(declarante.cpf)
    val cnpj = soDigitos(declarante.cnpj)

    // 0000 -- Abertura do arquivo digital e identificação do declarante.
    linhas.add(linha(listOf("0000", anoIni, anoFim, declarante.nome, cpf.ifBlank { cnpj }, declarante.inscricaoEstadual)))

    // 0010 -- forma de apuração "1" = Livro Caixa.
    linhas.add(linha(listOf("0010", "1")))

    // 0035 -- imóveis rurais (COD_IMOVEL sequencial: IM001, IM002...).
    val imoveisOrdenados = resultado.lancamentos.map { it.imovel }.distinct()
    val codImovel = mutableMapOf<String, String>()
    imoveisOrdenados.forEachIndexed { i, nome ->
        val cod = "IM%03d".format(i + 1)
        codImovel[nome] = cod
        linhas.add(linha(listOf("0035", cod, "BR", "", "", nome)))
    }

    // 0040 -- contas correntes (COD_CONTA sequencial: CT001, CT002...).
    val contasOrdenadas = resultado.lancamentos
        .map { it.banco?.trim().takeIf { b -> !b.isNullOrBlank() } ?: "Sem conta informada" }
        .distinct()
    val codConta = mutableMapOf<String, String>()
    contasOrdenadas.forEachIndexed { i, nome ->
        val cod = "CT%03d".format(i + 1)
        codConta[nome] = cod
        linhas.add(linha(listOf("0040", cod, nome)))
    }

    // Q100 -- lançamentos do Livro Caixa (1 registro por movimento de caixa,
    // já classificado em regime de caixa/entrada-saída/saldo acumulado pelo
    // servidor). ID_PART (CPF/CNPJ da contraparte) fica em branco -- mesmo
    // limite de escopo do site.
    for (l in resultado.lancamentos) {
        val cod = codImovel[l.imovel] ?: ""
        val contaNome = l.banco?.trim().takeIf { !it.isNullOrBlank() } ?: "Sem conta informada"
        val conta = codConta[contaNome] ?: ""
        val tipoLanc = if (l.entrada > 0) "1" else "2" // 1=receita, 2=despesa
        linhas.add(
            linha(
                listOf(
                    "Q100",
                    ddmmaaaaFromIso(l.data),
                    cod,
                    conta,
                    l.tipoDocumento ?: "",
                    "",
                    l.historico,
                    "",
                    tipoLanc,
                    valorLcdpr(l.entrada),
                    valorLcdpr(l.saida),
                    valorLcdpr(kotlin.math.abs(l.saldo)),
                    if (l.saldo >= 0) "P" else "N",
                ),
            ),
        )
    }

    // Q200 -- resumo mensal (12 meses sempre, mesmo sem movimento).
    for (m in resultado.porMes) {
        linhas.add(
            linha(
                listOf(
                    "Q200",
                    "${m.mes}${resultado.ano}",
                    valorLcdpr(m.entradas),
                    valorLcdpr(m.saidas),
                    valorLcdpr(m.saldoFinal),
                    if (m.saldoFinal >= 0) "P" else "N",
                ),
            ),
        )
    }

    // 9900 -- contagem de registros por tipo, tirada ANTES de adicionar as
    // próprias linhas 9900 (senão elas mudariam a contagem que reportam).
    // groupingBy(...).eachCount() preserva a ordem do primeiro encontro de
    // cada chave (LinkedHashMap por baixo) -- mesma ordem de inserção do
    // Map incremental usado no site.
    val contagemPorReg = linhas.map(::regDaLinha).groupingBy { it }.eachCount()
    val qtdLinhas9900 = contagemPorReg.size + 1 // +1 pq o próprio "9900" também vira uma linha
    for ((reg, qtd) in contagemPorReg) linhas.add(linha(listOf("9900", reg, qtd.toString())))
    linhas.add(linha(listOf("9900", "9900", qtdLinhas9900.toString())))

    // 9999 -- encerramento: total de linhas do arquivo (todas as anteriores + esta última).
    linhas.add(linha(listOf("9999", (linhas.size + 1).toString())))

    return linhas.joinToString("\r\n") + "\r\n"
}
