package com.bragro.mobile.ui.operacoes

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.Wallet
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
    // Lançamentos revisados nesta sessão -- o botão Revisar continua
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
private fun fmtMoney(v: Double): String = NumberFormat.getCurrencyInstance(PT_BR).format(v)
private fun fmtNum(v: Double): String = NumberFormat.getNumberInstance(PT_BR).apply { maximumFractionDigits = 2 }.format(v)
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
        maxLines = 1,
        overflow = TextOverflow.Clip,
        modifier = Modifier
            .background(bg, RoundedCornerShape(50))
            .basicMarquee()
            .padding(horizontal = 8.dp, vertical = 3.dp),
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
        pct >= 90 -> MaterialTheme.colorScheme.tertiary
        MaterialTheme.colorScheme.background.luminance() < 0.5f -> androidx.compose.ui.graphics.Color(0xFF2F6B4A)
        else -> MaterialTheme.colorScheme.secondary
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
// do lançamento (mesma rota de edição do módulo) e marcando como revisada.
// Números (área utilizada, % da fazenda, custo/ha) vêm prontos do servidor.
private val GRUPOS_ATIVIDADE = listOf(
    "Análise de solo", "Preparo do solo", "Dessecação", "Adubação de base", "Tratamento de sementes",
    "Plantio", "Cobertura", "Tratos culturais", "Monitoramento", "Pulverização", "Controle de pragas",
    "Aplicação foliar", "Ponto de maturação", "Colheita", "Outras",
)
private val NOME_ATIVIDADE = mapOf(
    "AMOSTRAGEM SOLO" to "Amostragem e análise de solo",
    "AMOSTRAGEM" to "Ponto de maturação (amostragem)",
    "TRAT. SEMENTES" to "Tratamento de sementes",
    "TRATAMENTO" to "Tratamento de sulco",
    "MONITOR. PRAGAS" to "Monitoramento de pragas",
)
private fun tituloAtividade(op: String): String = NOME_ATIVIDADE[op] ?: (op.take(1) + op.drop(1).lowercase(PT_BR))
private const val ACIMA_LIMITE = 0.05

private fun fmtNum1(v: Double): String = NumberFormat.getNumberInstance(PT_BR).apply { minimumFractionDigits = 1; maximumFractionDigits = 1 }.format(v)
private fun fmtNum0(v: Double): String = NumberFormat.getNumberInstance(PT_BR).apply { maximumFractionDigits = 0 }.format(v)
private fun fmtPct(v: Double): String = (if (v > 0) "+" else "") + fmtNum1(v) + "%"

@Composable
private fun FiltroLista(label: String, valor: String, opcoes: List<Pair<String, String>>, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    var aberto by remember { mutableStateOf(false) }
    Column(modifier = modifier) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            androidx.compose.material3.OutlinedButton(
                onClick = { aberto = true },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(opcoes.firstOrNull { it.first == valor }?.second ?: valor, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.ExpandMore, contentDescription = null, modifier = Modifier.size(16.dp))
            }
            androidx.compose.material3.DropdownMenu(expanded = aberto, onDismissRequest = { aberto = false }) {
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
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(l, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(v, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = cor ?: MaterialTheme.colorScheme.onSurface)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OperacaoCard(
    op: OperacaoAgrupadaData,
    pendingIds: Set<String> = emptySet(),
    revisados: Set<String> = emptySet(),
    onReview: (String, String) -> Unit = { _, _ -> },
    onEditRecord: (String, String) -> Unit = { _, _ -> },
) {
    var etapa by remember(op.chave) { mutableStateOf("Todas") }
    var atividade by remember(op.chave) { mutableStateOf("Todas") }
    var talhao by remember(op.chave) { mutableStateOf("Todos") }
    var revisao by remember(op.chave) { mutableStateOf("Todas") }
    val cardsFechados = remember(op.chave) { androidx.compose.runtime.mutableStateMapOf<String, Boolean>() }
    val osAbertas = remember(op.chave) { androidx.compose.runtime.mutableStateMapOf<String, Boolean>() }
    var blocoAberto by remember(op.chave) { mutableStateOf(false) }

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
                        com.bragro.mobile.ui.domain.PainelKpi("Atividades", atividades.size.toDouble(), "numero", null, "${op.timeline.size} lançamentos", "Atividades"),
                    ),
                    barras = listOf(com.bragro.mobile.ui.domain.PainelBarra("O.S. finalizadas", op.progressoPct.toDouble(), "${op.osConcluidas}/${op.osTotal}")),
                ),
                onKpi = { blocoAberto = true },
            )
            com.bragro.mobile.ui.domain.CabecalhoLancamentos(total = atividades.size, aberto = blocoAberto, titulo = "Atividades") { blocoAberto = !blocoAberto }

            if (blocoAberto) {
                // Filtros em listas suspensas
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FiltroLista(
                        "Etapa", etapa,
                        listOf("Todas" to "Todas as etapas") + etapas.map { g -> g to "$g (${atividades.count { grupoDe(it) == g }})" },
                        { etapa = it; atividade = "Todas" },
                        Modifier.weight(1f),
                    )
                    FiltroLista(
                        "Atividade", atividade,
                        listOf("Todas" to "Todas as atividades") + atividades.filter { etapa == "Todas" || grupoDe(it) == etapa }
                            .map { a -> a to "${tituloAtividade(a)} (${op.timeline.count { it.operacao == a }})" },
                        { atividade = it },
                        Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FiltroLista(
                        "Talhão", talhao,
                        listOf("Todos" to "Todos os talhões") + op.talhoes.map { it.talhao to "Talhão ${it.talhao} · ${fmtNum0(it.hectare)} ha" },
                        { talhao = it },
                        Modifier.weight(1f),
                    )
                    FiltroLista(
                        "Revisão", revisao,
                        listOf("Todas" to "Todas as O.S.", "Pendentes" to "Pendentes de revisão", "Revisadas" to "Revisadas"),
                        { revisao = it },
                        Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    androidx.compose.material3.TextButton(onClick = { etapa = "Todas"; atividade = "Todas"; talhao = "Todos"; revisao = "Todas" }) {
                        Text("Limpar filtros", style = MaterialTheme.typography.labelSmall)
                    }
                    androidx.compose.material3.TextButton(onClick = { cardsFechados.clear(); op.timeline.forEach { osAbertas[it.id] = true } }) {
                        Text("▾ Abrir todas", style = MaterialTheme.typography.labelSmall)
                    }
                    androidx.compose.material3.TextButton(onClick = { atividades.forEach { cardsFechados[it] = true }; osAbertas.clear() }) {
                        Text("▴ Recolher tudo", style = MaterialTheme.typography.labelSmall)
                    }
                }

                if (cards.isEmpty()) {
                    Text("Nenhuma O.S. para os filtros escolhidos.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                // Um card por atividade, um abaixo do outro
                cards.forEach { (a, lista) ->
                    val aberto = cardsFechados[a] != true
                    val colheita = lista.first().tipo == "colheita"
                    val area = lista.groupBy { it.talhao ?: it.id }.values.sumOf { g -> g.maxOf { it.hectare ?: 0.0 } }
                    val custo = lista.sumOf { it.realizado }
                    val orcado = lista.sumOf { it.planejado }
                    val desvio = if (orcado > 0) (custo - orcado) / orcado * 100 else 0.0
                    val producao = lista.sumOf { it.producao ?: 0.0 }
                    val un = lista.first().unidadeProducao ?: ""
                    val nOS = lista.mapNotNull { it.os }.distinct().size
                    val equipe = lista.map { Triple(it.responsavel ?: "Não lançado", it.funcao ?: "—", it.frota ?: "Não lançada") }.distinctBy { it.first + it.third }
                    val ordem = (atividades.indexOf(a) + 1).toString().padStart(2, '0')
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                                .clickable { cardsFechados[a] = aberto }
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "$ordem · ${grupoDe(a).uppercase()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                                Icon(if (aberto) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = if (aberto) "Recolher" else "Abrir", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(tituloAtividade(a), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                (if (colheita) "${lista.size} colheita(s)" else "${lista.size} lançamento(s) · $nOS O.S.") +
                                    " · ${fmtDataCurta(lista.first().data)} a ${fmtDataCurta(lista.last().dataFim ?: lista.last().data)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            // Responsável, função e frota em destaque
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                equipe.forEach { (nome, func, frota) ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(" $nome", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                    }
                                    Text("$func · 🚜 $frota", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                KpiBox("Área", "${fmtNum0(area)} ha", modifier = Modifier.weight(1f))
                                KpiBox(if (colheita) "Produção" else "Custo", if (colheita) "${fmtNum0(producao)} $un" else fmtMoney(custo), modifier = Modifier.weight(1f))
                            }
                            KpiBox(
                                if (colheita) "Produtividade" else "Custo/ha (área utilizada)",
                                if (colheita) "${fmtNum1(if (area > 0) producao / area else 0.0)} $un/ha" else fmtMoney(if (area > 0) custo / area else 0.0),
                                if (!colheita && orcado > 0) "Desvio ${if (abs(desvio) <= ACIMA_LIMITE) "dentro do orçado" else fmtPct(desvio)}" else null,
                                null,
                                Modifier.fillMaxWidth(),
                            )
                        }
                        if (aberto) {
                            lista.forEach { ev ->
                                val osAberta = osAbertas[ev.id] == true
                                val revisado = ev.id in revisados
                                val pendente = ev.id in pendingIds && !revisado
                                val dominio = if (ev.tipo == "colheita") "colheita" else "safra"
                                androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                Column(modifier = Modifier.fillMaxWidth().clickable { osAbertas[ev.id] = !osAberta }.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                    Row(verticalAlignment = Alignment.Top) {
                                        Icon(if (osAberta) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                                            Text(
                                                (if (ev.tipo == "colheita") "Colheita" else "O.S. ${ev.os ?: "—"}") + " · " + (ev.talhao?.let { "Talhão $it" } ?: "Sem talhão"),
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
                                            )
                                            Text(
                                                "${fmtDataCurta(ev.data)}${ev.dataFim?.let { if (it.take(10) != ev.data.take(10)) " a ${fmtDataCurta(it)}" else "" } ?: ""} · ${ev.hectare?.let { "${fmtNum0(it)} ha" } ?: "—"}" +
                                                    if (ev.tipo != "colheita") " · ${fmtMoney(ev.realizado)}" else "",
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
                                        Column(horizontalAlignment = Alignment.End) {
                                            com.bragro.mobile.ui.domain.StatusBadge(ev.situacao)
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
                                            // revisada e abre o formulário de edição do lançamento.
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
                                                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(14.dp))
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
                        val valorFormatado = if (col.money) formatMoneyValue(valorCru) else displayValueFor(col.key, valorCru, col.type)
                        Text(
                            "${col.label}: $valorFormatado",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                title = {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Operações", color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Clip, modifier = Modifier.basicMarquee())
                    }
                },
                actions = {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row {
                            // Fazenda removida -- pedido do usuário. Ordem
                            // agora: Imprimir, Nuvem, Atualizar -- pedido do
                            // usuário ("coloque apenas o ícone atualizar no
                            // topo ao lado da nuvem e na frente da nuvem o
                            // ícone imprimir"). Atualizar saiu da linha da
                            // barra de ciclo (ver mais abaixo), que agora
                            // expande até o limite da tela.
                            if (operacoes.isNotEmpty()) {
                                IconButton(onClick = { HtmlPrinter.printList(context, operacoesExportConfig(), operacoesExportRecords(operacoes)) }) {
                                    Icon(Icons.Filled.Print, contentDescription = "Imprimir", tint = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                            IconButton(onClick = {
                                val msg = if (offline) NetworkStatus.failureMessage(context) else "Conectado -- dados sincronizados com o servidor."
                                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(if (offline) Icons.Filled.CloudOff else Icons.Filled.Cloud, contentDescription = "Nuvem", tint = MaterialTheme.colorScheme.onSurface)
                            }
                            IconButton(onClick = { viewModel.refresh() }) {
                                Icon(Icons.Filled.Refresh, contentDescription = "Atualizar", tint = MaterialTheme.colorScheme.onSurface)
                            }
                            IconButton(onClick = { tableView = !tableView }) {
                                Icon(
                                    if (tableView) Icons.Filled.ViewAgenda else Icons.Filled.TableChart,
                                    contentDescription = if (tableView) "Ver em Coluna" else "Ver em Tabela",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            // Botão global "recolher/expandir tudo" REMOVIDO do
                            // cabeçalho -- pedido do usuário (imagem 1: "exclua
                            // a setinha do cabeçalho do lado superior direito").
                            // A setinha individual de cada O.S. (OperacaoCard,
                            // ao lado do ícone editar) continua controlando
                            // cada linha por conta própria.
                        }
                    }
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        com.bragro.mobile.ui.domain.HomeNavButton()
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "operacoes-header") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Um bloco por fazenda com todas as atividades, do preparo do solo à colheita. Deslize os lançamentos para o lado.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // Atualizar saiu daqui (subiu pro TopAppBar, ao lado da
                    // Nuvem) -- pedido do usuário: a barra de ciclo agora
                    // expande sozinha até o limite da tela.
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        JANELAS.forEachIndexed { index, d ->
                            SegmentedButton(
                                selected = janela == d,
                                onClick = { viewModel.setJanela(d) },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = JANELAS.size),
                                colors = SegmentedButtonDefaults.colors(
                                    activeContainerColor = MaterialTheme.colorScheme.primary,
                                    activeContentColor = MaterialTheme.colorScheme.onPrimary,
                                    inactiveContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                ),
                                label = { Text(if (d == 0) "Safra" else "${d}d", maxLines = 1) },
                            )
                        }
                    }
                }
            }
            run {
                if (offline) {
                    item { Text(NetworkStatus.failureMessage(context), style = MaterialTheme.typography.bodySmall) }
                }
                if (operacoes.isEmpty()) {
                    item {
                        Text(
                            if (loading) "Carregando..." else if (!temCache) "Sem dados ainda. Conecte-se à internet e atualize." else "Nenhuma operação de safra lançada nesta janela.",
                            modifier = Modifier.padding(vertical = 24.dp),
                        )
                    }
                } else if (tableView) {
                    item(key = "table-header") { RecordTableHeader(OPERACOES_EXPORT_COLUMNS, tableHScroll, showActions = false) }
                    items(operacoesExportRecords(operacoes), key = { it["cultura"] + "|" + it["safra"] + "|" + it["local"] }) { row ->
                        RecordTableRow(columns = OPERACOES_EXPORT_COLUMNS, record = row, domainId = "", hScroll = tableHScroll)
                    }
                } else {
                    items(operacoes, key = { it.chave }) { op ->
                        OperacaoCard(
                            op,
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
