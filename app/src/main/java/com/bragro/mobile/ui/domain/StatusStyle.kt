package com.bragro.mobile.ui.domain

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Spacer
import java.text.NumberFormat
import java.util.Locale

// Espelho de STATUS_LIKE_KEY / STATUS_TONE / statusTone() / ORIGEM_LABELS em
// data-table.tsx -- é um mecanismo 100% genérico (por TEXTO do valor, não por
// domainId), então funciona igual nos ~18 módulos sem exceção: qualquer
// coluna cujo nome bate com esse padrão vira um "badge" colorido em vez de
// texto simples, e qualquer valor reconhecido no dicionário abaixo ganha a
// cor correspondente (verde/amarelo/vermelho), igual ao site.
private val STATUS_LIKE_KEY = Regex("status|^acaoRh$|^confere$|^conferenNf$|^desvio$", RegexOption.IGNORE_CASE)

// Nao mais "private" -- reaproveitado por progressCellInfo/DomainProgressBar
// abaixo (mesmo pacote com.bragro.mobile.ui.domain, chamado de
// DomainListScreen.kt) pra colorir a barra de progresso com o mesmo criterio
// visual (verde/amarelo/vermelho) que StatusBadge ja usa pra badges de texto.
enum class Tone { GOOD, WARN, BAD }

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

/** Extrai só "AAAA-MM-DD" de um valor de data que pode chegar do servidor
 * como timestamp ISO completo com fuso ("2026-08-05T00:00:00.000Z") --
 * usado no formulário de edição (DomainFormScreen.kt), que antes mostrava
 * esse timestamp cru no campo (pedido do usuário: "as datas estão
 * incorretas junto com elas tem fuso horário"). Mesmo motivo do
 * displayValueFor() acima: extrai o prefixo direto da string, sem passar
 * por java.util.Date/fuso, pra nunca "voltar um dia". */
fun isoDateOnly(raw: String): String = ISO_DATE_PREFIX.find(raw)?.value ?: raw

private val BR_DATE = Regex("^(\\d{2})/(\\d{2})/(\\d{4})$")

/** Converte "AAAA-MM-DD" (formato que o servidor manda/espera) pro formato
 * brasileiro "DD/MM/AAAA" -- pedido do usuário ("dentro do botão de
 * adicionar novo lançamento as datas estão com padrão americano"): o campo
 * de data do formulário mostrava/pedia AAAA-MM-DD, que embora seja ISO (não
 * literalmente o padrão americano MM/DD/AAAA) tem a ordem ano-mês-dia que o
 * usuário não reconhece como o formato usado no Brasil. Devolve o texto
 * original se não bater no padrão esperado (nunca quebra em cima de um
 * valor vazio/inesperado). */
fun isoDateToBr(iso: String): String {
    if (iso.isBlank()) return iso
    val m = ISO_DATE_PREFIX.find(iso) ?: return iso
    val (y, mo, d) = m.destructured
    return "$d/$mo/$y"
}

/** Inverso de isoDateToBr() -- usado só na hora de montar o corpo enviado
 * pro servidor (save()), nunca durante a digitação (evita brigar com o
 * usuário no meio de uma data parcialmente digitada). Devolve o texto
 * original se ainda não estiver completo/no formato DD/MM/AAAA (o servidor
 * já validava e rejeitava datas malformadas antes disso existir, então não
 * piora nada deixar passar como está). */
fun brDateToIso(br: String): String {
    val m = BR_DATE.find(br.trim()) ?: return br
    val (d, mo, y) = m.destructured
    return "$y-$mo-$d"
}

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
    // "Conciliado"/"Pendente" em vez do literal true/false -- pedido do
    // usuário ("troque as palavras true e false no campo conciliar por
    // conciliado e pendente respectivamente"). Só o campo "conciliar"
    // (Financeiro, checkbox computado) -- outros checkboxes do app
    // continuam mostrando o valor cru/Sim-Não deles normalmente, essa troca
    // é só pro rótulo específico desse campo.
    if (key == "conciliar") {
        return if (rawValue == "true") "Conciliado" else "Pendente"
    }
    // Fallback GENÉRICO pra qualquer outro checkbox (Despesa Fixa, Entra na
    // Declaração, Devolução Emb. INPEV, Motorista Terceirizado, Melhor
    // Opção, Em Estoque etc.) -- bug real encontrado (usuário: "onde houver
    // ou true substitua o nome pelo que o campo corresponde"): apesar do
    // comentário antigo acima dizer que "outros checkboxes... continuam
    // mostrando Sim/Não normalmente", não existia NENHUM código fazendo essa
    // troca -- RecordFieldLine (DomainListScreen.kt) e o resto do app
    // chamavam displayValueFor pra TODO campo, inclusive checkbox, que caía
    // direto no "return rawValue" no fim da função e mostrava o literal
    // "true"/"false". Mesmo comportamento genérico que o site já tinha
    // (fmtValue/fmtComputed em data-table.tsx/record-form.tsx: "typeof v ===
    // boolean ? Sim : Não").
    if (colType == "checkbox") {
        return if (rawValue == "true") "Sim" else "Não"
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

fun statusTone(raw: String): Tone? {
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
        Text(displayValue, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        return
    }
    val (bg, fg) = when (tone) {
        Tone.GOOD -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) to MaterialTheme.colorScheme.primary
        // Antes fixo (Color(0xFFF2C037) alpha 0.20 / Color(0xFF8A6D00)) --
        // pedido do usuário ("coloque as cores das fontes preto/branco modo
        // claro/escuro"): esse par não mudava entre temas, e no Escuro o
        // amarelo em 20% de alpha sobre o fundo quase-preto produzia um tom
        // amarronzado onde o texto marrom-escuro ficava com contraste
        // baixo. secondaryContainer/onSecondaryContainer já são o par
        // "amarelo suave" do Material3 CORRETO por tema (claro: bg claro/fg
        // escuro; escuro: bg oliva/fg amarelo claro, ver Theme.kt).
        Tone.WARN -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
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

// Espelho de progressCellInfo() em data-table.tsx (site) -- pedido do
// usuario ("a barra de progresso deve ser aplicada em modulos que envolvem
// metas, limites, etapas continuas ou consumo de recursos", depois "sim"
// pra replicar no app nativo). Mesmo criterio: dominio+coluna especificos,
// tudo calculado em cima de campos que ja chegam do servidor (colhido/
// entregue/areaRealizada/areaPlanejada/custoRealizado/custoOrcado/valorR/
// valorPago/entrada/proxRevisao) -- NENHUM calculo novo/duplicado, so
// leitura + razao simples, exatamente como no site.
enum class BarTone { GOOD, WARN, BAD, NEUTRAL }

data class DomainProgressInfo(val value: Double, val barTone: BarTone, val label: String? = null)

private fun toneToBar(tone: Tone?): BarTone = when (tone) {
    Tone.GOOD -> BarTone.GOOD
    Tone.WARN -> BarTone.WARN
    Tone.BAD -> BarTone.BAD
    null -> BarTone.NEUTRAL
}

// Mesmo criterio ja usado no bloco "% do orcado ja gasto" de Analises
// (site, analises-client.tsx) -- Custo Orcado (Planejamento de Safra),
// Contratos (Valor Pago) e Frota (dias ate a proxima revisao) reaproveitam
// este mesmo threshold.
private fun budgetBarTone(pct: Double): BarTone = when {
    pct >= 100 -> BarTone.BAD
    pct >= 80 -> BarTone.WARN
    else -> BarTone.GOOD
}

// Mesmo criterio ja usado na barra de Area Total x Parcial de Operacoes
// (OperacoesScreen.kt) -- reaproveitado aqui pra Area Realizada x Planejada.
private fun areaBarTone(pct: Double): BarTone = when {
    pct > 100 -> BarTone.BAD
    pct >= 90 -> BarTone.WARN
    else -> BarTone.NEUTRAL
}

private fun fmtNum1(v: Double): String =
    NumberFormat.getNumberInstance(PT_BR_MONEY).apply { maximumFractionDigits = 2 }.format(v)

/** Extrai millis (UTC, meia-noite) de uma data ISO -- mesmo prefixo usado em
 * ISO_DATE_PREFIX/isoDateOnly acima, so que convertido pra epoch millis (pra
 * calcular "dias desde a Entrada" da barra de revisao de Frota). */
private fun isoDateMillis(raw: String): Long? {
    val m = ISO_DATE_PREFIX.find(raw) ?: return null
    val (y, mo, d) = m.destructured
    val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
    cal.clear()
    cal.set(y.toInt(), mo.toInt() - 1, d.toInt())
    return cal.timeInMillis
}

/** Retorna os dados pra renderizar uma barra de progresso neste campo
 * (dominio+coluna especificos), ou null quando esta celula nao e uma delas
 * (RecordFieldLine continua com a renderizacao normal, sem mudanca nenhuma).
 * "record" e o Map<String, String?> cru que ja chega do servidor (mesmo
 * formato usado no resto do app, ver DomainListViewModel.records). */
fun domainProgressCellInfo(domainId: String, key: String, record: Map<String, String?>): DomainProgressInfo? {
    fun num(k: String): Double? = record[k]?.toDoubleOrNull()

    if (domainId == "colheita" && key == "colhido") {
        val value = num("colhido") ?: return null
        return DomainProgressInfo(value, toneToBar(statusTone(record["status"] ?: "")))
    }
    if (domainId == "pedidos" && key == "entregue") {
        val value = num("entregue") ?: return null
        return DomainProgressInfo(value, toneToBar(statusTone(record["status"] ?: "")))
    }
    if (domainId == "planejamentosafra" && key == "areaRealizada") {
        val planejada = num("areaPlanejada")
        if (planejada == null || planejada == 0.0) return null
        val realizada = num("areaRealizada") ?: 0.0
        val value = (realizada / planejada) * 100
        return DomainProgressInfo(value, areaBarTone(value), label = "${fmtNum1(realizada)}/${fmtNum1(planejada)} ha")
    }
    if (domainId == "planejamentosafra" && key == "custoRealizado") {
        val orcado = num("custoOrcado")
        if (orcado == null || orcado == 0.0) return null
        val realizado = num("custoRealizado") ?: 0.0
        val value = (realizado / orcado) * 100
        return DomainProgressInfo(value, budgetBarTone(value))
    }
    if (domainId == "contratos" && key == "valorPago") {
        val total = num("valorR")
        if (total == null || total == 0.0) return null
        val pago = num("valorPago") ?: 0.0
        val value = (pago / total) * 100
        return DomainProgressInfo(value, budgetBarTone(value))
    }
    if (domainId == "frota" && key == "proxRevisao") {
        val entradaRaw = record["entrada"] ?: return null
        val entradaMs = isoDateMillis(entradaRaw) ?: return null
        val dias = (System.currentTimeMillis() - entradaMs) / 86400000.0
        val value = (dias / 180.0) * 100
        // Mantem a data da proxima revisao visivel no label (mesmo motivo do
        // site: a barra so acrescenta "quanto da janela ja passou", nao
        // deveria fazer a data sumir da celula). "faltam Nd"/"atrasada Nd"
        // acrescentado a pedido do usuario ("coloque na barra de progresso
        // quantos dias faltam para proxima revisao") -- mesmo calculo do
        // site (data-table.tsx), contando a partir de HOJE ate proxRevisao.
        val proxRevisaoRaw = record["proxRevisao"]
        val proxRevisaoMs = proxRevisaoRaw?.let { isoDateMillis(it) }
        val label = proxRevisaoRaw?.let {
            val dataFmt = isoDateToBr(isoDateOnly(it))
            val diasRestantes = proxRevisaoMs?.let { ms -> Math.round((ms - System.currentTimeMillis()) / 86400000.0) }
            if (diasRestantes != null) {
                val restante = if (diasRestantes >= 0) "faltam ${diasRestantes}d" else "atrasada ${-diasRestantes}d"
                "$dataFmt — $restante"
            } else dataFmt
        }
        return DomainProgressInfo(value, budgetBarTone(value), label = label)
    }
    return null
}

/** Barra de progresso dentro de um card de lançamento (RecordFieldLine) --
 * mesmo "Rótulo: valor" das outras linhas, so que com uma barrinha embaixo
 * em vez de so texto. Cores seguem BarTone (GOOD=primary, WARN=tertiary,
 * BAD=error, NEUTRAL=secondary), mesmo padrao ja usado em
 * OperacaoProgressBar/AreaProgressBar (OperacoesScreen.kt). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DomainProgressCell(colLabel: String, info: DomainProgressInfo) {
    val fracao = (info.value / 100.0).coerceIn(0.0, 1.0).toFloat()
    val cor = when (info.barTone) {
        BarTone.GOOD -> MaterialTheme.colorScheme.primary
        BarTone.WARN -> MaterialTheme.colorScheme.tertiary
        BarTone.BAD -> MaterialTheme.colorScheme.error
        BarTone.NEUTRAL -> MaterialTheme.colorScheme.secondary
    }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            "$colLabel: ${info.label ?: "${Math.round(info.value)}%"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier.basicMarquee(),
        )
        Spacer(Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(3.dp)),
        ) {
            Box(modifier = Modifier.fillMaxWidth(fracao).height(5.dp).background(cor, RoundedCornerShape(3.dp)))
        }
    }
}
