package com.bragro.mobile.ui.simulador

import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.remember
import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.TrendingDown
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import com.bragro.mobile.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bragro.mobile.data.model.SimuladorBaseData
import com.bragro.mobile.data.repo.SimuladorRepository
import com.bragro.mobile.ui.theme.Card
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

/** Simulador de Cenários "E se?" (Task #600/#616, paridade com
 * simulador-client.tsx do site): calculadora "e se" pra FRENTE -- o
 * usuário varia premissas (preço, produtividade, câmbio) em cima da base
 * REAL da organização (vinda do mesmo motor do DRE) e vê o resultado
 * projetado na hora. A base vem UMA vez do servidor
 * (/api/mobile/simulador, ver SimuladorRepository); TODO o recálculo
 * abaixo (produtividadeProjetada/precoProjetado/receitaProjetada/
 * custoProjetado/margens) replica -- fórmula por fórmula -- o useMemo de
 * simulador-client.tsx, sem round-trip nenhum por slider/input alterado.
 * Nada aqui é salvo em lugar nenhum. */
private val PADRAO_VAR_PRECO = 0
private val PADRAO_VAR_PRODUTIVIDADE = 0
private val PADRAO_PCT_EXPOSICAO_CAMBIO = 15

class SimuladorViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = SimuladorRepository(app)

    var carregando = mutableStateOf(true)
        private set
    var erro = mutableStateOf<String?>(null)
        private set
    var base = mutableStateOf<SimuladorBaseData?>(null)
        private set

    var varPrecoPct = mutableStateOf(PADRAO_VAR_PRECO)
    var varProdutividadePct = mutableStateOf(PADRAO_VAR_PRODUTIVIDADE)
    var cambioProjetado = mutableStateOf(5.0)
    var pctExposicaoCambio = mutableStateOf(PADRAO_PCT_EXPOSICAO_CAMBIO)

    init { carregar() }

    fun carregar() {
        carregando.value = true
        erro.value = null
        viewModelScope.launch {
            val resultado = repository.fetchBase()
            carregando.value = false
            if (resultado == null) {
                erro.value = "Sem conexão -- não foi possível carregar a base do simulador agora."
                return@launch
            }
            base.value = resultado
            cambioProjetado.value = resultado.cambioBaseUsdBrl ?: 5.0
        }
    }

    fun restaurarPadrao() {
        varPrecoPct.value = PADRAO_VAR_PRECO
        varProdutividadePct.value = PADRAO_VAR_PRODUTIVIDADE
        cambioProjetado.value = base.value?.cambioBaseUsdBrl ?: 5.0
        pctExposicaoCambio.value = PADRAO_PCT_EXPOSICAO_CAMBIO
    }
}

/** Espelho EXATO do "resultado" (useMemo) de simulador-client.tsx -- ver
 * comentário no arquivo original pra cada linha. */
private data class ResultadoSimulacao(
    val receitaProjetada: Double,
    val custoProjetado: Double,
    val margemBase: Double,
    val margemProjetada: Double,
    val deltaMargem: Double,
)

private fun calcularResultado(
    base: SimuladorBaseData,
    varPrecoPct: Int,
    varProdutividadePct: Int,
    cambioProjetado: Double,
    pctExposicaoCambio: Int,
): ResultadoSimulacao {
    val produtividadeProjetada = base.produtividadeBaseScHa?.let { it * (1 + varProdutividadePct / 100.0) }
    val precoProjetado = base.precoBaseRs?.let { it * (1 + varPrecoPct / 100.0) }
    val totalSacasProjetado = produtividadeProjetada?.let { base.areaHa * it }

    val receitaProjetada = if (totalSacasProjetado != null && precoProjetado != null) {
        totalSacasProjetado * precoProjetado
    } else {
        base.receitaBaseTotal * (1 + varPrecoPct / 100.0) * (1 + varProdutividadePct / 100.0)
    }

    val cambioBase = base.cambioBaseUsdBrl
    val fatorCambio = if (cambioBase != null && cambioBase > 0) cambioProjetado / cambioBase else 1.0
    val custoProjetado = base.custoBaseTotal * (1 + (fatorCambio - 1) * (pctExposicaoCambio / 100.0))

    val margemBase = base.receitaBaseTotal - base.custoBaseTotal
    val margemProjetada = receitaProjetada - custoProjetado
    val deltaMargem = margemProjetada - margemBase

    return ResultadoSimulacao(receitaProjetada, custoProjetado, margemBase, margemProjetada, deltaMargem)
}

private fun formatMoneyBrl(value: Double): String =
    NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(value)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SimuladorScreen(onBack: () -> Unit, viewModel: SimuladorViewModel = viewModel()) {
    val carregando by viewModel.carregando
    val erro by viewModel.erro
    val base by viewModel.base
    val varPrecoPct by viewModel.varPrecoPct
    val varProdutividadePct by viewModel.varProdutividadePct
    val cambioProjetado by viewModel.cambioProjetado
    val pctExposicaoCambio by viewModel.pctExposicaoCambio

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { com.bragro.mobile.ui.domain.BackNavButton() },
                title = { androidx.compose.material3.ProvideTextStyle(androidx.compose.material3.LocalTextStyle.current.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)) {
                    // v21 -- ícone verde do módulo ao lado do título.
                    androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
                        com.bragro.mobile.ui.domain.IconeNegrito(androidx.compose.material.icons.Icons.Outlined.Calculate, contentDescription = null, tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurface, size = 22.dp, modifier = androidx.compose.ui.Modifier.padding(end = 8.dp, bottom = 3.dp))
 Text("Simulador \"E se?\"", color = MaterialTheme.colorScheme.onSurface) 
                    }
                } },
                actions = { com.bragro.mobile.ui.domain.HomeNavButton() },
            )
        },
    ) { padding0 ->
        // v19 -- ícones do cabeçalho movidos para a faixa Ações.
        Column(Modifier.fillMaxSize().padding(padding0)) {
            val padding = PaddingValues(0.dp)
            Box(Modifier.fillMaxWidth().weight(1f)) {
        if (carregando) {
            Column(modifier = Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        if (erro != null || base == null) {
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(erro ?: "Sem dados.", style = MaterialTheme.typography.bodyMedium)
            }
            return@Scaffold
        }

        val b = base!!
        val resultado = calcularResultado(b, varPrecoPct, varProdutividadePct, cambioProjetado, pctExposicaoCambio)

        var premissasAbertas by remember { mutableStateOf(true) }
        var resultadoAberto by remember { mutableStateOf(true) }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    "Varie preço, produtividade e câmbio e veja o resultado projetado na hora -- nada aqui é salvo, é só uma calculadora em cima dos dados reais da sua operação.",
                    style = MaterialTheme.typography.labelSmall,
                )
            }

            item {
                com.bragro.mobile.ui.theme.FormBlockCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                        val partes = buildList {
                            add("${NumberFormat.getNumberInstance(Locale("pt", "BR")).format(b.areaHa)} ha")
                            b.produtividadeBaseScHa?.let { add("produtividade atual ${NumberFormat.getNumberInstance(Locale("pt", "BR")).format(it)} sc/ha") }
                            b.precoBaseRs?.let { add("preço de referência ${formatMoneyBrl(it)}/saca (${b.precoBaseCultura ?: ""})") }
                            b.cambioBaseUsdBrl?.let { add("câmbio atual R$ ${"%.2f".format(it)}/USD") }
                        }
                        Text("Base real usada: ${partes.joinToString(" · ")}.", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            item {
                com.bragro.mobile.ui.domain.TituloRecolhivel("Premissas do Cenário", premissasAbertas, acoes = {
                    // v20 -- Restaurar padrão na linha das Premissas.
                    androidx.compose.material3.IconButton(onClick = { viewModel.restaurarPadrao() }, modifier = Modifier.size(36.dp)) {
                        androidx.compose.material3.Icon(Icons.Outlined.Refresh, contentDescription = "Restaurar padrão", tint = MaterialTheme.colorScheme.onSurface)
                    }
                }) { premissasAbertas = !premissasAbertas }
            }
            if (premissasAbertas) item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CampoPercentual(
                            label = "Variação de preço de venda",
                            value = varPrecoPct,
                            onChange = { viewModel.varPrecoPct.value = it },
                            hint = "Ex.: -10 se a saca cair 10%, +10 se subir 10%.",
                        )
                        CampoPercentual(
                            label = "Variação de produtividade",
                            value = varProdutividadePct,
                            onChange = { viewModel.varProdutividadePct.value = it },
                            hint = "Ex.: -15 numa quebra de safra, +10 numa safra recorde.",
                        )
                        // Câmbio projetado e % do custo exposto na MESMA linha.
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("Câmbio projetado (R$/USD)", style = MaterialTheme.typography.labelSmall, maxLines = 2)
                                OutlinedTextField(
                                    value = if (cambioProjetado == cambioProjetado.toLong().toDouble()) cambioProjetado.toLong().toString() else cambioProjetado.toString(),
                                    onValueChange = { txt -> txt.replace(",", ".").toDoubleOrNull()?.let { viewModel.cambioProjetado.value = it } },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            CampoPercentual(
                                label = "% do custo exposto ao câmbio",
                                value = pctExposicaoCambio,
                                onChange = { viewModel.pctExposicaoCambio.value = it },
                                min = 0,
                                max = 100,
                                compact = true,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Text(
                            "Fatia do seu custo em insumo importado/indexado ao dólar (fertilizante, defensivo). Estimativa sua -- ajuste conforme sua realidade.",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }

            item {
                SecaoTitulo("Resultado Projetado", resultadoAberto) { resultadoAberto = !resultadoAberto }
            }
            if (resultadoAberto) item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        LinhaComparativo("Receita", b.receitaBaseTotal, resultado.receitaProjetada)
                        LinhaComparativo("Custo", b.custoBaseTotal, resultado.custoProjetado, inverso = true)
                        androidx.compose.material3.HorizontalDivider()
                        LinhaComparativo("Margem", resultado.margemBase, resultado.margemProjetada, destaque = true)
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            val positivo = resultado.deltaMargem >= 0
                            Icon(
                                if (positivo) Icons.Outlined.TrendingUp else Icons.Outlined.TrendingDown,
                                contentDescription = null,
                                tint = if (positivo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            )
                            Text(
                                "Neste cenário, a margem ${if (positivo) "melhora em" else "piora em"} ${formatMoneyBrl(kotlin.math.abs(resultado.deltaMargem))} em relação ao resultado atual.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
        }
    }
}

@Composable
private fun SecaoTitulo(texto: String, aberto: Boolean, onToggle: () -> Unit) {
    com.bragro.mobile.ui.domain.TituloRecolhivel(texto, aberto, onToggle = onToggle)
}

@Composable
private fun CampoPercentual(
    label: String,
    value: Int,
    onChange: (Int) -> Unit,
    hint: String? = null,
    min: Int = -50,
    max: Int = 50,
    // Compacto: só o campo (sem botões +/-), pra caber ao lado de outro campo
    // na mesma linha (Câmbio projetado + % do custo exposto).
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(if (compact) label else "$label (%)", style = MaterialTheme.typography.labelSmall)
        if (compact) {
            OutlinedTextField(
                value = value.toString(),
                onValueChange = { txt -> txt.toIntOrNull()?.let { onChange(it.coerceIn(min, max)) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                suffix = { Text("%") },
                modifier = Modifier.fillMaxWidth(),
            )
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Ícones Add/Remove (vetoriais) em vez de Text("+")/Text("−") --
            // bug real de auditoria ("faltou colocar os sinais de + e -"):
            // o glifo Unicode U+2212 (MINUS SIGN) usado antes não é coberto
            // pela fonte padrão em alguns aparelhos, renderizando em branco
            // dentro do botão (o "+" ASCII normalmente aparece, mas ficava
            // estranho um botão com símbolo e outro sem). Ícones Material
            // são vetores, nunca dependem de glifo de fonte.
            OutlinedButton(
                onClick = { onChange((value - 5).coerceIn(min, max)) },
                modifier = Modifier.size(40.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            ) { Icon(Icons.Outlined.Remove, contentDescription = "Diminuir") }
            OutlinedTextField(
                value = value.toString(),
                onValueChange = { txt -> txt.toIntOrNull()?.let { onChange(it.coerceIn(min, max)) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(
                onClick = { onChange((value + 5).coerceIn(min, max)) },
                modifier = Modifier.size(40.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            ) { Icon(Icons.Outlined.Add, contentDescription = "Aumentar") }
        }
        if (hint != null) Text(hint, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun LinhaComparativo(label: String, base: Double, projetado: Double, inverso: Boolean = false, destaque: Boolean = false) {
    val delta = projetado - base
    val bom = if (inverso) delta <= 0 else delta >= 0
    val cor = if (bom) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = if (destaque) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
            fontWeight = if (destaque) FontWeight.Bold else FontWeight.Normal,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(formatMoneyBrl(base), style = MaterialTheme.typography.labelSmall, textDecoration = TextDecoration.LineThrough)
            Text(
                formatMoneyBrl(projetado),
                style = if (destaque) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = cor,
            )
        }
    }
}
