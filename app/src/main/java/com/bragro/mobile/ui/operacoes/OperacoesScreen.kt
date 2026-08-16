package com.bragro.mobile.ui.operacoes

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wallet
import com.bragro.mobile.ui.theme.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import com.bragro.mobile.data.model.OperacaoAgrupadaData
import com.bragro.mobile.data.repo.OperacoesRepository
import com.bragro.mobile.ui.domain.FarmSelectorButton
import com.bragro.mobile.ui.domain.LabeledIconButton
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
    var loading = mutableStateOf(false)
        private set
    var offline = mutableStateOf(false)
        private set
    var temCache = mutableStateOf(false)
        private set
    var janela = mutableStateOf(90)
        private set

    init {
        viewModelScope.launch {
            repository.observeCached().collectLatest { entity ->
                if (entity != null) {
                    temCache.value = true
                    if (entity.janela == janela.value) operacoes.value = repository.parse(entity)
                }
            }
        }
        refresh()
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

private val JANELAS = listOf(30, 60, 90, 180)
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
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .background(bg, RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

// Barra de progresso simples (Box proporcional) -- não existe um componente
// Progress compartilhado no app nativo ainda (só no site, ver
// components/ui/progress.tsx); duplicado aqui em vez de criar um
// compartilhado novo, mesmo critério já usado pelas outras telas
// (DreScreen/FinanceiroScreen) de não arriscar mexer em código usado por
// outros módulos.
@Composable
private fun JanelaProgressBar(pct: Double) {
    val fracao = (pct / 100.0).coerceIn(0.0, 1.0).toFloat()
    val cor = when {
        pct >= 100 -> MaterialTheme.colorScheme.error
        pct >= 80 -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.primary
    }
    Box(modifier = Modifier.fillMaxWidth().height(6.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(3.dp))) {
        Box(modifier = Modifier.fillMaxWidth(fracao).height(6.dp).background(cor, RoundedCornerShape(3.dp)))
    }
}

@Composable
private fun OperacaoCard(op: OperacaoAgrupadaData, onVerEmSafra: () -> Unit) {
    var abrirFinanceiro by remember { mutableStateOf(false) }
    var abrirEstoque by remember { mutableStateOf(false) }

    val progresso = remember(op.dataInicio, op.dataFim) {
        if (op.dataFim == null) return@remember null
        try {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            val inicio = parser.parse(op.dataInicio)?.time ?: return@remember null
            val fim = parser.parse(op.dataFim)?.time ?: return@remember null
            if (fim <= inicio) return@remember null
            val diasTotal = ((fim - inicio) / 86400000.0).let { Math.round(it) }
            val diasDecorridos = ((System.currentTimeMillis() - inicio) / 86400000.0).let { Math.round(it) }
            Triple((diasDecorridos.toDouble() / diasTotal.toDouble()) * 100.0, maxOf(0L, diasDecorridos), diasTotal)
        } catch (e: Exception) {
            null
        }
    }

    val variacaoCor = when {
        op.variacaoMedia == null -> MaterialTheme.colorScheme.onSurfaceVariant
        abs(op.variacaoMedia) <= 5 -> MaterialTheme.colorScheme.primary
        abs(op.variacaoMedia) <= 20 -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.error
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "${op.cultura} · ${op.safra}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        op.local + (op.hectare?.let { " — ${fmtNum(it)} ha" } ?: ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                EstagioChip(op.estagio)
            }

            if (progresso != null) {
                val (pct, decorridos, total) = progresso
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Progresso da janela", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$decorridos/$total dias", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    JanelaProgressBar(pct)
                    Text("${Math.round(pct)}%", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 2.dp))
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column {
                    Text("Realizado", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(fmtMoney(op.realizadoTotal), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
                Column {
                    Text("Planejado", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(fmtMoney(op.planejadoTotal), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
                if (op.variacaoMedia != null) {
                    Column {
                        Text("Variação", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${fmtNum(op.variacaoMedia)}%", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = variacaoCor)
                    }
                }
            }

            if (op.financeiroTotal > 0 || op.estoqueConsumido.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (op.financeiroTotal > 0) {
                        Column(
                            modifier = Modifier.fillMaxWidth().clickable { abrirFinanceiro = !abrirFinanceiro },
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Wallet, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    "  Financeiro vinculado: ${fmtMoney(op.financeiroTotal)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    "${op.financeiroDetalhe.size} lanç.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                )
                                Icon(
                                    Icons.Filled.ExpandMore,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp).rotate(if (abrirFinanceiro) 180f else 0f),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                            if (abrirFinanceiro) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 20.dp, top = 4.dp)
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                        .padding(8.dp),
                                ) {
                                    op.financeiroDetalhe.forEach { f ->
                                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                            Text(
                                                "${fmtDataCurta(f.data)} · ${f.categoria}${f.subcategoria?.let { " / $it" } ?: ""}${f.entidade?.let { " — $it" } ?: ""}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f),
                                            )
                                            Text(fmtMoney(f.valor), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (op.estoqueConsumido.isNotEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxWidth().clickable { abrirEstoque = !abrirEstoque },
                        ) {
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(Icons.Filled.Inventory2, contentDescription = null, modifier = Modifier.size(14.dp).padding(top = 2.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                val resumo = op.estoqueConsumido.take(3).joinToString(", ") { "${it.item} (${fmtNum(it.qtd)}${it.unidade?.let { u -> " $u" } ?: ""})" } +
                                    if (op.estoqueConsumido.size > 3) " +${op.estoqueConsumido.size - 3}" else ""
                                Text(
                                    "  Estoque baixado: $resumo",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    "${op.estoqueConsumido.size} it.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                )
                                Icon(
                                    Icons.Filled.ExpandMore,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp).rotate(if (abrirEstoque) 180f else 0f),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                            if (abrirEstoque) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 20.dp, top = 4.dp)
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                        .padding(8.dp),
                                ) {
                                    op.estoqueConsumido.forEach { e ->
                                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                            Text(e.item, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                                            Text("${fmtNum(e.qtd)}${e.unidade?.let { " $it" } ?: ""}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (op.timeline.isNotEmpty()) {
                Column {
                    op.timeline.takeLast(6).forEach { ev ->
                        Row(modifier = Modifier.padding(vertical = 3.dp)) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(" ${fmtDataCurta(ev.data)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (!ev.responsavel.isNullOrBlank()) {
                                        Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(12.dp).padding(start = 4.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(" ${ev.responsavel}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Text(
                                    ev.operacao + (ev.os?.let { " (O.S. $it)" } ?: ""),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }

            // "Ver em Safra" continua indo pro módulo (único jeito de editar/
            // excluir os lançamentos que formam esta operação) -- mesmo
            // critério do site.
            TextButton(onClick = onVerEmSafra, modifier = Modifier.padding(0.dp)) {
                Text("Ver em Safra →", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OperacoesScreen(onBack: () -> Unit, onVerEmSafra: () -> Unit, viewModel: OperacoesViewModel = viewModel()) {
    val operacoes by viewModel.operacoes
    val loading by viewModel.loading
    val offline by viewModel.offline
    val temCache by viewModel.temCache
    val janela by viewModel.janela
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Operações", color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
                actions = {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row {
                            FarmSelectorButton()
                            IconButton(onClick = {
                                val msg = if (offline) NetworkStatus.failureMessage(context) else "Conectado -- dados sincronizados com o servidor."
                                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(if (offline) Icons.Filled.CloudOff else Icons.Filled.Cloud, contentDescription = "Nuvem", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
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
                        "Cada card é uma operação de safra (Safra + Cultura + Local) com a linha do tempo de tudo que foi lançado nela.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
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
                                    label = { Text("${d}d", maxLines = 1) },
                                )
                            }
                        }
                        LabeledIconButton(
                            icon = Icons.Filled.Refresh,
                            label = "Atualizar",
                            loading = loading,
                            onClick = { viewModel.refresh() },
                        )
                    }
                }
            }
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
            } else {
                items(operacoes, key = { it.chave }) { op ->
                    OperacaoCard(op, onVerEmSafra = onVerEmSafra)
                }
            }
        }
    }
}
