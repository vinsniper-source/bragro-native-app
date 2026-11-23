package com.bragro.mobile.ui.operacoes

import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material.icons.outlined.Agriculture
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.outlined.FilterAlt
import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.outlined.Wallet
import com.bragro.mobile.ui.domain.RecordTableHeader
import com.bragro.mobile.ui.domain.RecordTableRow
import com.bragro.mobile.ui.domain.StatusBadge
import com.bragro.mobile.ui.domain.displayValueFor
import com.bragro.mobile.ui.domain.formatMoneyValue
import com.bragro.mobile.ui.domain.isStatusLikeColumn
import com.bragro.mobile.ui.theme.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import com.bragro.mobile.ui.i18n.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bragro.mobile.data.NetworkStatus
import com.bragro.mobile.data.model.ColumnConfig
import com.bragro.mobile.data.model.DomainConfig
import com.bragro.mobile.data.model.OperacaoAgrupadaData
import com.bragro.mobile.data.repo.OperacoesRepository
import com.bragro.mobile.ui.print.HtmlPrinter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs

// Visão "Operação" agrupada (gap encontrado na auditoria módulo-a-módulo
// contra o site, pedido do usuário "implemente tudo que falta ainda para o
// app native da plataforma") -- réplica de src/app/(app)/operacoes/page.tsx
// + operacao-card.tsx: cada card = 1 combinação Safra+Cultura+Local, com a
// linha do tempo das operações de campo lançadas nela, cruzando com
// Financeiro/Estoque. Via /api/mobile/operacoes, que reaproveita a MESMA
// getOperacoes() (lib/services/operacoes.ts) que o site usa.
class OperacoesViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = OperacoesRepository(app)

    var operacoes = mutableStateOf<List<OperacaoAgrupadaData>>(emptyList())
        private set
    var pendingIds = mutableStateOf<Set<String>>(emptySet())
        private set
    var loading = mutableStateOf(false)
        private set
    var offline = mutableStateOf(false)
        private set
    var temCache = mutableStateOf(false)
        private set
    // 0 = safra inteira (padrão desde a rodada "um bloco por fazenda").
    var janela = mutableStateOf(0)
        private set
    // Registros revisados nesta sessão -- o botão Revisar continua
    // habilitado (pedido do usuário), só muda o rótulo pra "Revisada".
    var revisados = mutableStateOf<Set<String>>(emptySet())
        private set

    init {
        viewModelScope.launch {
            repository.observeCached().collectLatest { entity ->
                if (entity != null) {
                    temCache.value = true
                    if (entity.janela == janela.value) {
                        operacoes.value = repository.parse(entity)
                        pendingIds.value = repository.parsePending(entity).toSet()
                    }
                }
            }
        }
        refresh()
    }

    fun marcarRevisado(recordId: String, dominio: String = "safra") {
        // Otimista: some o selo na hora; refresh confirma com o servidor.
        pendingIds.value = pendingIds.value - recordId
        revisados.value = revisados.value + recordId
        viewModelScope.launch {
            repository.marcarRevisado(recordId, dominio)
            loading.value = false
            refresh()
        }
    }

    fun setJanela(value: Int) {
        if (janela.value == value) return
        janela.value = value
        operacoes.value = emptyList()
        refresh()
    }

    fun refresh() {
        if (loading.value) return
        loading.value = true
        viewModelScope.launch {
            val ok = repository.refresh(janela.value)
            offline.value = !ok
            loading.value = false
        }
    }
}

private val JANELAS = listOf(0, 30, 60, 90, 180)
private val PT_BR = Locale("pt", "BR")
private fun fmtMoney(v: Double): String = com.bragro.mobile.ui.i18n.MoedaApp.formatar(v)
private fun fmtNum(v: Double): String = NumberFormat.getNumberInstance(PT_BR).apply { maximumFractionDigits = 2 }.format(v)
/** v276 -- data completa (dd/mm/aaaa). */
private fun fmtDataCompleta(iso: String): String =
    try {
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        val d = parser.parse(iso)
        if (d != null) SimpleDateFormat("dd/MM/yyyy", PT_BR).format(d) else com.bragro.mobile.ui.domain.diaMesBr(iso)
    } catch (e: Exception) {
        com.bragro.mobile.ui.domain.diaMesBr(iso)
    }

private fun fmtDataCurta(iso: String): String =
    try {
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        val d = parser.parse(iso)
        if (d != null) SimpleDateFormat("dd/MM", PT_BR).format(d) else iso.take(10)
    } catch (e: Exception) {
        iso.take(10)
    }

private val ESTAGIO_LABEL = mapOf(
    "plantio" to "Plantio", "vegetativo" to "Vegetativo", "colheita" to "Colheita", "indefinido" to "Sem operação na janela",
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EstagioChip(estagio: String) {
    val (bg, fg) = when (estagio) {
        "plantio" -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f) to MaterialTheme.colorScheme.tertiary
        "vegetativo" -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) to MaterialTheme.colorScheme.primary
        "colheita" -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        ESTAGIO_LABEL[estagio] ?: estagio,
        style = MaterialTheme.typography.labelSmall,
        color = fg,
        maxLines = 2,
        overflow = TextOverflow.Clip,
        // v275 (mockup v330) -- sem fundo colorido.
    )
}

// Barra de progresso simples (Box proporcional) -- não existe um componente
// Progress compartilhado no app nativo ainda (só no site, ver
// components/ui/progress.tsx); duplicado aqui em vez de criar um
// compartilhado novo, mesmo critério já usado pelas outras telas
// (DreScreen/FinanceiroScreen) de não arriscar mexer em código usado por
// outros módulos.
// Progresso OPERACIONAL (O.S. concluídas/totais) -- substitui a antiga
// JanelaProgressBar (tempo decorrido vs "janela", que dava 650% em
// operações concluídas há muito tempo, ver comentário em OperacaoCard
// acima). "pct" já vem 0-100 do servidor (razão de contagens, nunca
// ultrapassa 100 por construção), mas o coerceIn(0.0, 1.0) fica como trava
// defensiva mesmo assim -- mesmo raciocínio do "Cap/Clamp" pedido pelo
// usuário, só que aqui é redundante (não decorativo) porque a métrica já
// nasceu limitada.
@Composable
private fun OperacaoProgressBar(pct: Double, concluida: Boolean, atrasada: Boolean) {
    val fracao = (pct / 100.0).coerceIn(0.0, 1.0).toFloat()
    // Verde mais fechado no modo escuro (pedido do usuário: trocar a
    // tonalidade do verde da barra de progresso no modo escuro).
    val escuro = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val verde = if (escuro) androidx.compose.ui.graphics.Color(0xFF2F6B4A) else MaterialTheme.colorScheme.primary
    val cor = when {
        concluida -> verde
        atrasada -> MaterialTheme.colorScheme.error
        else -> if (escuro) verde else MaterialTheme.colorScheme.secondary
    }
    Box(modifier = Modifier.fillMaxWidth().height(6.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(3.dp))) {
        Box(modifier = Modifier.fillMaxWidth(fracao).height(6.dp).background(cor, RoundedCornerShape(3.dp)))
    }
}

// Barra de área utilizada (hectare da operação) vs. Área Total da fazenda --
// pedido do usuário ("crie uma barra de progresso da area total com a areas
// parcial. e aplique o mesmo padrao da plataforma em native"). Cores
// seguem o mesmo padrão de threshold usado no site (insumos-arvore-client.tsx
// STATUS_BAR_COLOR: OK/ATENCAO/CRITICO) em vez do padrão concluida/atrasada
// da barra de O.S. acima -- aqui não há um "concluído", é só uma comparação
// de área, então >100% (possível divergência de cadastro) vira vermelho,
// >=90% amarelo, resto verde/cor padrão.
@Composable
private fun AreaProgressBar(pct: Int) {
    val fracao = (pct / 100.0).coerceIn(0.0, 1.0).toFloat()
    val cor = when {
        pct > 100 -> MaterialTheme.colorScheme.error
        // v1.6.5 (mockup v106b, item 4) -- sem azul: atenção amarelo, em dia verde.
        pct >= 90 -> MaterialTheme.colorScheme.secondary
        else -> com.bragro.mobile.ui.domain.progressGreen()
    }
    Box(modifier = Modifier.fillMaxWidth().height(6.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(3.dp))) {
        Box(modifier = Modifier.fillMaxWidth(fracao).height(6.dp).background(cor, RoundedCornerShape(3.dp)))
    }
}

// Bloco por fazenda -- layout aprovado no mockup (réplica do site,
// components/domain/operacao-card.tsx): 1 card por ATIVIDADE com todas as
// O.S. dentro (setinhas para recolher o card e cada O.S.), cards um abaixo
// do outro (sem rolagem lateral), filtros em listas suspensas (Etapa,
// Atividade, Talhão, Revisão), responsável/função/frota em destaque em cada
// card e em cada O.S., e "Revisar e editar" abrindo o formulário de edição
// do registro (mesma rota de edição do módulo) e marcando como revisada.
// Números (área utilizada, % da fazenda, custo/ha) vêm prontos do servidor.
private val GRUPOS_ATIVIDADE = listOf(
    "Análise de solo", "Preparo do solo", "Dessecação", "Adubação de base", "Tratamento de sementes",
    "Plantio", "Cobertura", "Tratos culturais", "Monitoramento", "Pulverização", "Controle de pragas",
    "Aplicação foliar", "Ponto de maturação", "Colheita", "Outras",
)
private val NOME_ATIVIDADE = mapOf(
    "AMOSTRAGEM SOLO" to "Amostragem e análise de solo",
    "AMOSTRAGEM" to "Ponto de maturação (amostragem)",
    "AMOSTRAGEM GRÃOS" to "Ponto de maturação (amostragem)",
    "TRAT. SEMENTES" to "Tratamento de sementes",
    "TRATAMENTO" to "Tratamento de sulco",
    "TRATAMENTO DE SULCO" to "Tratamento de sulco",
    "MONITOR. PRAGAS" to "Monitoramento de pragas",
)
private fun tituloAtividade(op: String): String = NOME_ATIVIDADE[op] ?: (op.take(1) + op.drop(1).lowercase(PT_BR))
private const val ACIMA_LIMITE = 0.05

private fun fmtNum1(v: Double): String = NumberFormat.getNumberInstance(PT_BR).apply { minimumFractionDigits = 1; maximumFractionDigits = 1 }.format(v)
private fun fmtNum0(v: Double): String = NumberFormat.getNumberInstance(PT_BR).apply { maximumFractionDigits = 0 }.format(v)
private fun fmtPct(v: Double): String = (if (v > 0) "+" else "") + fmtNum1(v) + "%"

/** Chave da opção "Limpar filtros" nas listas suspensas (v40). */
private const val LIMPAR = "__limpar"

@Composable
private fun FiltroLista(label: String, valor: String, opcoes: List<Pair<String, String>>, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    var aberto by remember { mutableStateOf(false) }
    Column(modifier = modifier) {
        Text(label /* v273 -- sem caixa alta */, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            // v285 (mockup v359 item 7) -- sem caixa: campo de linha igual aos do app
            // (valor à direita, linha embaixo com o canto).
            val corLinha = MaterialTheme.colorScheme.outline
            Row(
                modifier = Modifier.fillMaxWidth().clickable { aberto = true }
                    .drawBehind {
                        val y = size.height - 1.dp.toPx()
                        drawLine(corLinha, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), 1.dp.toPx())
                        drawLine(corLinha, androidx.compose.ui.geometry.Offset(size.width - 0.5.dp.toPx(), y), androidx.compose.ui.geometry.Offset(size.width - 0.5.dp.toPx(), y - 5.dp.toPx()), 1.dp.toPx())
                    }
                    .padding(top = 6.dp, bottom = 5.dp, end = 4.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                Text(opcoes.firstOrNull { it.first == valor }?.second ?: valor, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            com.bragro.mobile.ui.theme.MenuSuspenso(expanded = aberto, onDismissRequest = { aberto = false }) {
                opcoes.forEach { (v, l) ->
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(l, style = MaterialTheme.typography.bodySmall, fontWeight = if (v == valor) FontWeight.Bold else FontWeight.Normal) },
                        onClick = { onSelect(v); aberto = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun KpiBox(label: String, valor: String, sub: String? = null, cor: androidx.compose.ui.graphics.Color? = null, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Text(valor, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = cor ?: MaterialTheme.colorScheme.onSurface, maxLines = 1)
        if (sub != null) Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun LinhaCampo(l: String, v: String, cor: androidx.compose.ui.graphics.Color? = null) {
    com.bragro.mobile.ui.domain.RotuloValor(rotulo = l, valor = v, corValor = cor ?: MaterialTheme.colorScheme.onSurface)
}

internal class FiltrosOperacao {
    var etapa by mutableStateOf("Todas")
    var atividade by mutableStateOf("Todas")
    var talhao by mutableStateOf("Todos")
    var revisao by mutableStateOf("Todas")
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OperacaoCard(
    op: OperacaoAgrupadaData,
    pendingIds: Set<String> = emptySet(),
    revisados: Set<String> = emptySet(),
    onReview: (String, String) -> Unit = { _, _ -> },
    onEditRecord: (String, String) -> Unit = { _, _ -> },
    mostrarFiltros: Boolean = true,
    meio: (@Composable () -> Unit)? = null,
    // v285 (mockup v359 item 7) -- filtros comuns a todas as fazendas: o bloco
    // aparece uma vez só (no primeiro cartão) e vale para todos.
    filtros: FiltrosOperacao = remember(op.chave) { FiltrosOperacao() },
) {
    var etapa by filtros::etapa
    var atividade by filtros::atividade
    var talhao by filtros::talhao
    var revisao by filtros::revisao
    val cardsFechados = remember(op.chave) { androidx.compose.runtime.mutableStateMapOf<String, Boolean>() }
    val osAbertas = remember(op.chave) { androidx.compose.runtime.mutableStateMapOf<String, Boolean>() }
    var blocoAberto by remember(op.chave) { mutableStateOf(true) }

    val atividades = op.timeline.map { it.operacao }.distinct()
    fun grupoDe(a: String): String = op.timeline.firstOrNull { it.operacao == a }?.grupo ?: "Outras"
    val etapas = (GRUPOS_ATIVIDADE + atividades.map { grupoDe(it) }).distinct().filter { g -> atividades.any { grupoDe(it) == g } }

    val cards = atividades
        .filter { (etapa == "Todas" || grupoDe(it) == etapa) && (atividade == "Todas" || it == atividade) }
        .map { a ->
            a to op.timeline.filter { ev ->
                ev.operacao == a &&
                    (talhao == "Todos" || ev.talhao == talhao) &&
                    (revisao == "Todas" || (revisao == "Revisadas") == (ev.id in revisados))
            }
        }
        .filter { it.second.isNotEmpty() }

    // Layout v7: o monitoramento da fazenda fica num bloco próprio (borda
    // verde, cartões 3 por linha) e as atividades vêm abaixo, depois de uma
    // divisória "ATIVIDADES" -- antes tudo ficava dentro do mesmo cartão.
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            com.bragro.mobile.ui.domain.PainelMonitoramento(
                com.bragro.mobile.ui.domain.PainelModulo(
                    titulo = op.local,
                    subtitulo = "${op.cultura} · ${op.safra} · ${op.talhoes.size} talh${if (op.talhoes.size == 1) "ão" else "ões"}",
                    kpis = listOfNotNull(
                        com.bragro.mobile.ui.domain.PainelKpi("Área total", op.areaTotal ?: 0.0, "numero", "ha", op.talhoes.joinToString(" · ") { "T${it.talhao}" }.ifBlank { "—" }, "Atividades"),
                        com.bragro.mobile.ui.domain.PainelKpi("Realizado", op.realizadoTotal, "moeda", null, "orçado ${fmtMoney(op.planejadoTotal)}", "Atividades"),
                        op.custoHaFazenda?.let { com.bragro.mobile.ui.domain.PainelKpi("Custo/ha", it, "moeda", null, op.areaTotal?.let { a -> "${fmtNum0(a)} ha" } ?: "fazenda", "Atividades") },
                        com.bragro.mobile.ui.domain.PainelKpi(
                            "Desvio x orçado", op.variacaoMedia ?: 0.0, "pct", null, "média das atividades", "Atividades",
                            tom = op.variacaoMedia?.let { if (it > ACIMA_LIMITE) "acima" else "abaixo" },
                        ),
                        com.bragro.mobile.ui.domain.PainelKpi("Atividades", atividades.size.toDouble(), "numero", null, "${op.timeline.size} registros", "Atividades"),
                    ),
                    barras = listOf(com.bragro.mobile.ui.domain.PainelBarra("O.S. finalizadas", op.progressoPct.toDouble(), "${op.osConcluidas}/${op.osTotal}")),
                ),
                onKpi = { blocoAberto = true },
            )
            // v20 (opção A) -- Monitoramento, Ações e Progresso, nesta ordem.
            meio?.invoke()
            // v40 -- o controle de Atividades é só o ícone do bloco Ações; o
            // botão solto e o texto ao lado saíram daqui.
            if (blocoAberto) {
                if (mostrarFiltros) {
                // v40 -- "Limpar filtros" virou a primeira opção das listas.
                val limpar: () -> Unit = { etapa = "Todas"; atividade = "Todas"; talhao = "Todos"; revisao = "Todas" }
                // Filtros em listas suspensas
                // v285 (mockup v359 item 7) -- bloco estreito à direita, um filtro por linha.
                com.bragro.mobile.ui.theme.Card(modifier = Modifier.fillMaxWidth().wrapContentWidth(Alignment.End).width(230.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FiltroLista(
                        "Etapa", etapa,
                        listOf(LIMPAR to "Limpar filtros", "Todas" to "Todas as etapas") + etapas.map { g -> g to "$g (${atividades.count { grupoDe(it) == g }})" },
                        { if (it == LIMPAR) limpar() else { etapa = it; atividade = "Todas" } },
                        Modifier.fillMaxWidth(),
                    )
                    FiltroLista(
                        "Atividade", atividade,
                        listOf(LIMPAR to "Limpar filtros", "Todas" to "Todas as atividades") + atividades.filter { etapa == "Todas" || grupoDe(it) == etapa }
                            .map { a -> a to "${tituloAtividade(a)} (${op.timeline.count { it.operacao == a }})" },
                        { if (it == LIMPAR) limpar() else atividade = it },
                        Modifier.fillMaxWidth(),
                    )
                    FiltroLista(
                        "Talhão", talhao,
                        listOf(LIMPAR to "Limpar filtros", "Todos" to "Todos os talhões") + op.talhoes.map { it.talhao to "Talhão ${it.talhao} · ${fmtNum0(it.hectare)} ha" },
                        { if (it == LIMPAR) limpar() else talhao = it },
                        Modifier.fillMaxWidth(),
                    )
                    FiltroLista(
                        "Revisão", revisao,
                        listOf(LIMPAR to "Limpar filtros", "Todas" to "Todas as O.S.", "Pendentes" to "Pendentes de revisão", "Revisadas" to "Revisadas"),
                        { if (it == LIMPAR) limpar() else revisao = it },
                        Modifier.fillMaxWidth(),
                    )
                }
                }
                }
                // v40 -- "Limpar filtros", "Abrir todas" e "Recolher tudo" saíram:
                // limpar virou opção das listas suspensas de filtro.

                if (cards.isEmpty()) {
                    Text("Nenhuma O.S. para os filtros escolhidos.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                // Um card por atividade, um abaixo do outro
                cards.forEach { (a, lista) ->
                    // v1.6.23 (mockup v128b, item 4) -- blocos começam fechados.
                    val aberto = cardsFechados[a] == false
                    val colheita = lista.first().tipo == "colheita"
                    // v1.6.23 -- sem talhão, cada O.S. conta uma vez (não um por item).
                    val area = lista.groupBy { it.talhao ?: it.os?.let { o -> "os:$o" } ?: it.id }.values.sumOf { g -> g.maxOf { it.hectare ?: 0.0 } }
                    val custo = lista.sumOf { it.realizado }
                    val orcado = lista.sumOf { it.planejado }
                    val desvio = if (orcado > 0) (custo - orcado) / orcado * 100 else 0.0
                    val producao = lista.sumOf { it.producao ?: 0.0 }
                    val un = lista.first().unidadeProducao ?: ""
                    val nOS = lista.mapNotNull { it.os }.distinct().size
                    val equipe = lista.map { Triple(it.responsavel ?: "Não lançado", it.funcao ?: "—", it.frota ?: "Não lançada") }.distinctBy { it.first + it.third }
                    val ordem = (atividades.indexOf(a) + 1).toString().padStart(2, '0')
                    com.bragro.mobile.ui.theme.Card(modifier = Modifier.fillMaxWidth()) {
                        // v276 (mockup v333) -- cartão minimalista: 3 linhas, sem
                        // caixinhas internas; sem equipe lançada, o aviso vai no ⓘ.
                        val semEquipe = equipe.all { it.first == "Não lançado" }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { cardsFechados[a] = aberto }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("$ordem · ${tituloAtividade(a)}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                if (semEquipe) com.bragro.mobile.ui.theme.DicaInfo("Equipe ainda não lançada nas O.S.")
                                Spacer(Modifier.weight(1f))
                                val nFin = lista.count { it.situacao == "FINALIZADO" }
                                val (stTxt, stCor) = when {
                                    nFin == lista.size -> "finalizado" to MaterialTheme.colorScheme.primary
                                    lista.all { it.situacao == "PROGRAMADO" } -> "programado" to MaterialTheme.colorScheme.onSurfaceVariant
                                    else -> "${lista.size - nFin} pendente${if (lista.size - nFin > 1) "s" else ""}" to com.bragro.mobile.ui.domain.corAtencao()
                                }
                                Text(stTxt, style = MaterialTheme.typography.labelMedium, color = stCor)
                            }
                            Text(
                                (if (colheita) "${lista.size} colheita(s)" else "$nOS O.S.") +
                                    " · ${fmtDataCompleta(lista.first().data)} a ${fmtDataCompleta(lista.last().dataFim ?: lista.last().data)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (!semEquipe) Text(
                                equipe.filter { it.first != "Não lançado" }.joinToString(" · ") { it.first },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                            Text(
                                listOf(
                                    "${fmtNum0(area)} ha",
                                    if (colheita) "${fmtNum0(producao)} $un" else fmtMoney(custo),
                                    if (colheita) "${fmtNum1(if (area > 0) producao / area else 0.0)} $un/ha" else "${fmtMoney(if (area > 0) custo / area else 0.0)}/ha",
                                ).joinToString(" · ") + if (!colheita && orcado > 0 && abs(desvio) > ACIMA_LIMITE) " · desvio ${fmtPct(desvio)}" else "",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        if (aberto) {
                            lista.forEachIndexed { idxEv, ev ->
                                // v1.6.23 (mockup v128b) -- Safra: uma linha por O.S. (período,
                                // área e total) e embaixo os itens dela.
                                val novaOs = ev.tipo != "colheita" && (idxEv == 0 || lista[idxEv - 1].os != ev.os)
                                if (novaOs) {
                                    val daOs = lista.filter { it.os == ev.os && it.tipo != "colheita" }
                                    androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            "O.S. ${ev.os ?: "—"} · ${fmtDataCompleta(ev.data)} · ${ev.hectare?.let { "${fmtNum0(it)} ha" } ?: "—"}",
                                            style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
                                        )
                                        Text(fmtMoney(daOs.sumOf { it.realizado }), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                    }
                                }
                                val osAberta = osAbertas[ev.id] == true
                                val revisado = ev.id in revisados
                                val pendente = ev.id in pendingIds && !revisado
                                val dominio = if (ev.tipo == "colheita") "colheita" else "safra"
                                if (ev.tipo == "colheita") androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                com.bragro.mobile.ui.theme.Card(modifier = Modifier.fillMaxWidth().clickable { osAbertas[ev.id] = !osAberta }.padding(horizontal = 10.dp, vertical = if (ev.tipo == "colheita") 8.dp else 4.dp), border = androidx.compose.foundation.BorderStroke(0.dp, androidx.compose.ui.graphics.Color.Transparent), colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.Transparent)) { // v285 (mockup v359 item 6) -- sem moldura em cada linha
                                    Row(verticalAlignment = Alignment.Top) {
                                        Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                                            Text(
                                                if (ev.tipo == "colheita") "Colheita · " + (ev.talhao?.let { "Talhão $it" } ?: "Sem talhão")
                                                else "${ev.item?.let { com.bragro.mobile.ui.domain.exibirTexto(it) } ?: "—"}${ev.qtd?.let { q -> " · ${fmtNum1(q)} ${ev.unidade?.lowercase() ?: ""}" } ?: ""}", // v285 (mockup v359 item 6) -- sem caixa alta
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
                                            )
                                            if (ev.tipo == "colheita") {
                                            Text(
                                                "${fmtDataCompleta(ev.data)}${ev.dataFim?.let { if (it.take(10) != ev.data.take(10)) " a ${fmtDataCompleta(it)}" else "" } ?: ""} · ${ev.hectare?.let { "${fmtNum0(it)} ha" } ?: "—"}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                            Text(
                                                "👤 ${ev.responsavel ?: "Não lançado"} · ${ev.funcao ?: "—"} · 🚜 ${ev.frota ?: "Não lançada"}",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 2,
                                            )
                                            }
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            if (ev.tipo != "colheita") Text(fmtMoney(ev.realizado), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            else com.bragro.mobile.ui.domain.StatusBadge(ev.situacao)
                                            if (revisado) Text("✓ Revisada", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            else if (pendente) Text("Pendente", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    if (osAberta) {
                                        Column(
                                            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, top = 6.dp),
                                            verticalArrangement = Arrangement.spacedBy(3.dp),
                                        ) {
                                            if (ev.tipo == "colheita") {
                                                LinhaCampo("Área colhida", ev.hectare?.let { "${fmtNum0(it)} ha" } ?: "—")
                                                LinhaCampo("% da fazenda", ev.pctFazenda?.let { "${fmtNum1(it)}%" } ?: "—")
                                                LinhaCampo("Produção", ev.producao?.let { "${fmtNum0(it)} ${ev.unidadeProducao ?: ""}" } ?: "—")
                                                LinhaCampo("Produtividade", ev.produtividade?.let { "${fmtNum1(it)} ${ev.unidadeProducao ?: ""}/ha" } ?: "—")
                                            } else {
                                                val acima = (ev.variacaoPct ?: 0.0) > ACIMA_LIMITE
                                                LinhaCampo("Item", ev.item ?: "—")
                                                LinhaCampo("Quantidade", ev.qtd?.let { "${fmtNum(it)} ${ev.unidade ?: ""}" } ?: "—")
                                                LinhaCampo("Área utilizada", (ev.hectare?.let { "${fmtNum0(it)} ha" } ?: "—") + (ev.pctFazenda?.let { " · ${fmtNum1(it)}%" } ?: ""))
                                                LinhaCampo("Dose/ha", ev.dosePorHa?.let { "${fmtNum(it)} ${ev.unidade ?: ""}/ha" } ?: "—")
                                                LinhaCampo("Custo", fmtMoney(ev.realizado))
                                                LinhaCampo("Custo/ha", ev.custoHa?.let { fmtMoney(it) } ?: "—")
                                                LinhaCampo("Orçado", fmtMoney(ev.planejado))
                                                LinhaCampo(
                                                    "Desvio",
                                                    ev.variacaoPct?.let { if (abs(it) <= ACIMA_LIMITE) "Dentro" else fmtPct(it) } ?: "—",
                                                    null,
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            // "Revisar também com a função editar": marca como
                                            // revisada e abre o formulário de edição do registro.
                                            androidx.compose.material3.Button(
                                                onClick = {
                                                    onReview(ev.id, dominio)
                                                    onEditRecord(dominio, ev.id)
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = if (revisado) {
                                                    androidx.compose.material3.ButtonDefaults.buttonColors(
                                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                } else {
                                                    androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface)
                                                },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                            ) {
                                                Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Text(if (revisado) " Revisada · revisar e editar de novo" else " Revisar e editar", style = MaterialTheme.typography.labelMedium)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Financeiro e estoque da fazenda
                if (op.financeiroTotal > 0) {
                    Text("Financeiro vinculado: ${fmtMoney(op.financeiroTotal)} · ${op.financeiroDetalhe.size} lanç.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (op.estoqueConsumido.isNotEmpty()) {
                    Text(
                        "Estoque baixado: " + op.estoqueConsumido.take(4).joinToString(", ") { "${it.item} (${fmtNum(it.qtd)}${it.unidade?.let { u -> " $u" } ?: ""})" } +
                            if (op.estoqueConsumido.size > 4) " +${op.estoqueConsumido.size - 4}" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
    }
}

// "todo o formulário expandido" (pedido do usuário) -- busca o registro
// COMPLETO de Safra no cache local (RecordRepository, mesmo dado offline-
// first de toda a plataforma) e lista cada campo preenchido com seu rótulo
// em português (DomainConfig.columns, mesmo catálogo usado no formulário de
// edição), em vez de só o nome da operação que já aparecia. Sem round-trip
// de rede: Operações já exige que Safra tenha sido aberta pelo menos uma vez
// pra essa O.S. existir no timeline, e a essa altura o registro já está no
// Room (refreshFromServer do próprio módulo Safra).
@Composable
private fun OperacaoDetalheCompleto(recordId: String, dominio: String = "safra") {
    val context = androidx.compose.ui.platform.LocalContext.current
    // Guarda a ColumnConfig inteira (não só label+valor cru) -- necessário
    // pra formatar cada campo com a MESMA lógica usada no resto do app
    // (RecordFieldLine, DomainListScreen.kt): datas ISO viram dd/MM/yyyy,
    // moeda vira "R$ X.XXX,XX", checkbox vira Sim/Não, em vez do valor cru
    // do servidor aparecendo na tela ("2026-08-12T00:00:00.000Z", "true",
    // "320" sem separador decimal pt-BR) -- bug real apontado pelo usuário
    // ("conserte erros ortograficos, data hora").
    var campos by remember(recordId) { mutableStateOf<List<com.bragro.mobile.data.model.ColumnConfig>?>(null) }
    var valoresMap by remember(recordId) { mutableStateOf<Map<String, String?>>(emptyMap()) }
    var verTudo by remember(recordId) { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(recordId) {
        val recordRepo = com.bragro.mobile.data.repo.RecordRepository(context)
        val configRepo = com.bragro.mobile.data.repo.ConfigRepository(context)
        val valores = recordRepo.getRecord(dominio, recordId)
        val config = configRepo.domainConfig(dominio)
        if (valores != null && config != null) {
            valoresMap = valores
            campos = config.columns.filter { col -> !valores[col.key].isNullOrBlank() }
        } else {
            campos = emptyList()
        }
    }
    val lista = campos
    when {
        lista == null -> Text(
            "Carregando detalhes...",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, top = 2.dp),
        )
        lista.isEmpty() -> {}
        else -> {
            // "recolha até a linha 3" (pedido do usuário): por padrão mostra só
            // as 3 primeiras linhas preenchidas, com "ver mais"/"ver menos" pra
            // alternar com a lista completa -- meio-termo entre a reclamação
            // original ("não só 2-3 linhas") e esta ("recolha até a linha 3"):
            // os dados completos continuam disponíveis, só não despejados todos
            // de uma vez por padrão.
            val visiveis = if (verTudo) lista else lista.take(3)
            Column(
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 2.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                visiveis.forEach { col ->
                    val valorCru = valoresMap[col.key] ?: ""
                    if (isStatusLikeColumn(col.key)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${col.label}: ",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            StatusBadge(valorCru)
                        }
                    } else {
                        val valorFormatado = if (col.money) formatMoneyValue(valorCru) else com.bragro.mobile.ui.domain.displayValueLista(col.key, valorCru, col.type)
                        // v39 -- o valor desce inteiro quando não cabe ao lado do rótulo.
                        com.bragro.mobile.ui.domain.RotuloValorLinha(
                            rotulo = col.label,
                            valor = valorFormatado,
                            estilo = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                if (lista.size > 3) {
                    Text(
                        if (verTudo) "Ver menos" else "Ver mais (${lista.size - 3})",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 2.dp).clickable { verTudo = !verTudo },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun OperacoesScreen(onBack: () -> Unit, onEditRecord: (String, String) -> Unit, viewModel: OperacoesViewModel = viewModel()) {
    val operacoesTodas by viewModel.operacoes
    // v14: só a fazenda escolhida no topo.
    val farmSel = com.bragro.mobile.ui.domain.FarmSelection.selected.value
    val operacoes = remember(operacoesTodas, farmSel) {
        if (farmSel == null) operacoesTodas else operacoesTodas.filter { com.bragro.mobile.ui.domain.normalizarTexto(it.local) == com.bragro.mobile.ui.domain.normalizarTexto(farmSel) }
    }
    val loading by viewModel.loading
    val pendingIds by viewModel.pendingIds
    val revisados by viewModel.revisados
    val offline by viewModel.offline
    val temCache by viewModel.temCache
    val janela by viewModel.janela
    val context = LocalContext.current
    // Tabela/Coluna -- mesmo botão único (achado de auditoria, pedido do
    // usuário: "coloque em todos os módulos"). Aqui os dados já são
    // AGRUPADOS (OperacaoAgrupadaData, uma combinação Safra+Cultura+Local
    // por linha, sem um único registro editável por trás), então a tabela
    // reaproveita as mesmas OPERACOES_EXPORT_COLUMNS/operacoesExportRecords
    // já usadas em Excel/PDF, sem coluna de ações (ver RecordTable.kt,
    // showActions).
    var tableView by remember { mutableStateOf(false) }
    var filtrosVisiveis by remember { mutableStateOf(false) }
    val filtrosOperacaoComuns = remember { FiltrosOperacao() } // v285
    var atividadesVisiveis by remember { mutableStateOf(false) }
    val tableHScroll = remember { androidx.compose.foundation.ScrollState(0) }
    // Setinha de recolher removida do cabeçalho -- pedido do usuário
    // (imagem do próprio cabeçalho de Operações: "retire a setinha do
    // cabeçalho"). Conteúdo agora sempre visível, sem botão de toggle.

    // "setinha nas atividades recolher e expandir tudo" -- pedido do usuário
    // (imagem 2): botão global que recolhe/expande TODAS as atividades
    // (linha do tempo) de TODOS os cards de uma vez, além da setinha
    // individual por atividade que já existia (ver OperacaoCard). O mapa de
    // overrides é limpo a cada toggle global pra que o novo valor padrão
    // valha pra todo mundo, inclusive atividades que o usuário já tinha
    // mexido manualmente antes.

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        com.bragro.mobile.ui.domain.BackNavButton()
                    }
                },
                title = { androidx.compose.material3.ProvideTextStyle(androidx.compose.material3.LocalTextStyle.current.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)) {
                    // v21 -- ícone verde do módulo ao lado do título.
                    androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.Bottom) {

                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Operações", color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Clip)
                    }
                
                    }
                } },
                actions = {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        /* v275 (mockup v331) -- sem casinha na página inicial do módulo */
                    }
                },
            )
        },
    ) { padding ->
    // v20 (opção A) -- Ações + Progresso: entram logo abaixo do card de
    // Monitoramento da primeira fazenda (ou no topo, sem fazendas).
    val acoesEProgresso: @Composable (Int?, String?) -> Unit = { pct, det ->
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.bragro.mobile.ui.domain.AcoesEmAbas(
                        dados = {
                            com.bragro.mobile.ui.domain.LabeledIconButton(icon = com.bragro.mobile.ui.domain.IconesVazados.FilterAlt, label = "Filtros", active = filtrosVisiveis, onClick = { filtrosVisiveis = !filtrosVisiveis })
                            com.bragro.mobile.ui.domain.LabeledIconButton(
                                icon = if (tableView) Icons.Outlined.ViewAgenda else com.bragro.mobile.ui.domain.IconesVazados.TableChart,
                                label = if (tableView) "Coluna" else "Tabela",
                                active = tableView,
                                onClick = { tableView = !tableView },
                            )
                        },
                        operacoes = {
                            com.bragro.mobile.ui.domain.LabeledIconButton(icon = com.bragro.mobile.ui.domain.IconesVazados.ViewList, label = "Atividades", active = atividadesVisiveis, onClick = { atividadesVisiveis = !atividadesVisiveis })
                            com.bragro.mobile.ui.domain.LabeledIconButton(icon = Icons.Outlined.Refresh, label = "Atualizar", onClick = { viewModel.refresh() })
                            com.bragro.mobile.ui.domain.NuvemButton()
                        },
                        arquivos = {
                            com.bragro.mobile.ui.domain.ExportarMenu {
                            com.bragro.mobile.ui.domain.LabeledIconButton(icon = com.bragro.mobile.ui.domain.IconesVazados.Print, label = "Imprimir", onClick = { if (operacoes.isNotEmpty()) HtmlPrinter.printList(context, operacoesExportConfig(), operacoesExportRecords(operacoes)) })
                            com.bragro.mobile.ui.domain.LabeledIconButton(icon = com.bragro.mobile.ui.domain.IconesVazados.PictureAsPdf, label = "PDF", onClick = { if (operacoes.isNotEmpty()) HtmlPrinter.exportPdfDirect(context, operacoesExportConfig(), operacoesExportRecords(operacoes)) })
                            }
                        },
                    )
                    // Atualizar saiu daqui (subiu pro TopAppBar, ao lado da
                    // Nuvem) -- pedido do usuário: a barra de ciclo agora
                    // expande sozinha até o limite da tela.
                    // v276 (mockup v333) -- sem o rótulo "Progresso" (minimalista).
                    // v275 (mockup v330) -- seletor sem fundo: traço verde.
                    com.bragro.mobile.ui.domain.TracoOpcoes(
                        opcoes = JANELAS.map { d -> d.toString() to (if (d == 0) "safra" else "${d}d") },
                        selecionado = janela.toString(),
                        onSelect = { v -> v.toIntOrNull()?.let { viewModel.setJanela(it) } },
                    )
                    if (pct != null) {
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { (pct / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                        Text("$pct% das O.S. finalizadas no período${det?.let { " ($it)" } ?: ""}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
        }
    }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (operacoes.isEmpty() || tableView || !atividadesVisiveis) {
                item(key = "operacoes-header") { acoesEProgresso(null, null) }
            }
            run {
                if (offline) {
                    item { Text(NetworkStatus.failureMessage(context), style = MaterialTheme.typography.bodySmall) }
                }
                if (operacoes.isEmpty()) {
                    item {
                        Text(
                            if (loading) "Carregando..." else if (!temCache) "Sem dados. Conecte e atualize." else "Nenhuma operação de safra lançada nesta janela.",
                            modifier = Modifier.padding(vertical = 24.dp),
                        )
                    }
                } else if (tableView) {
                    item(key = "table-header") { RecordTableHeader(OPERACOES_EXPORT_COLUMNS, tableHScroll, showActions = false) }
                    items(operacoesExportRecords(operacoes), key = { it["cultura"] + "|" + it["safra"] + "|" + it["local"] }) { row ->
                        RecordTableRow(columns = OPERACOES_EXPORT_COLUMNS, record = row, domainId = "", hScroll = tableHScroll)
                    }
                } else if (!atividadesVisiveis) {
                    item { Text("Toque em Atividades para exibir as operações.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 12.dp)) }
                } else {
                    items(operacoes, key = { it.chave }) { op ->
                        val filtrosComuns = filtrosOperacaoComuns
                        OperacaoCard(
                            op,
                            mostrarFiltros = filtrosVisiveis && op.chave == operacoes.first().chave, // v285 -- bloco de filtros uma vez só
                            filtros = filtrosComuns,
                            meio = if (op.chave == operacoes.first().chave) ({ acoesEProgresso(op.progressoPct, "${op.osConcluidas}/${op.osTotal}") }) else null,
                            pendingIds = pendingIds,
                            revisados = revisados,
                            onReview = { id, dominio -> viewModel.marcarRevisado(id, dominio) },
                            onEditRecord = onEditRecord,
                        )
                    }
                }
            }
        }
    }
}

private val OPERACOES_EXPORT_COLUMNS = listOf(
    ColumnConfig(key = "cultura", label = "Cultura", type = "text"),
    ColumnConfig(key = "safra", label = "Safra", type = "text"),
    ColumnConfig(key = "local", label = "Local", type = "text"),
    ColumnConfig(key = "estagio", label = "Estágio", type = "text"),
    ColumnConfig(key = "realizadoTotal", label = "Realizado", type = "number"),
    ColumnConfig(key = "planejadoTotal", label = "Planejado", type = "number"),
    ColumnConfig(key = "variacaoMedia", label = "Variação %", type = "text"),
)

private fun operacoesExportConfig(): DomainConfig = DomainConfig(id = "operacoes", label = "Operações", columns = OPERACOES_EXPORT_COLUMNS)

private fun operacoesExportRecords(operacoes: List<OperacaoAgrupadaData>): List<Map<String, String?>> =
    operacoes.map {
        mapOf(
            "cultura" to it.cultura,
            "safra" to it.safra,
            "local" to it.local,
            "estagio" to it.estagio,
            "realizadoTotal" to it.realizadoTotal.toString(),
            "planejadoTotal" to it.planejadoTotal.toString(),
            "variacaoMedia" to (it.variacaoMedia?.let { v -> "${v}%" } ?: "—"),
        )
    }
