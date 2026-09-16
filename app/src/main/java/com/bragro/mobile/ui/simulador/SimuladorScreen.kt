package com.bragro.mobile.ui.simulador

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
                title = { Text("Simulador \"E se?\"", color = MaterialTheme.colorScheme.primary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.restaurarPadrao() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Restaurar padrão", tint = MaterialTheme.colorScheme.primary)
                    }
                },
            )
        },
    ) { padding ->
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
                Card(modifier = Modifier.fillMaxWidth()) {
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
                SecaoTitulo("Premissas do Cenário")
            }
            item {
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
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Câmbio projetado (R$/USD)", style = MaterialTheme.typography.labelSmall)
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
                            hint = "Fatia do seu custo em insumo importado/indexado ao dólar (fertilizante, defensivo). Estimativa sua -- ajuste conforme sua realidade.",
                        )
                    }
                }
            }

            item {
                SecaoTitulo("Resultado Projetado")
            }
            item {
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
                                if (positivo) Icons.Filled.TrendingUp else Icons.Filled.TrendingDown,
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

@Composable
private fun SecaoTitulo(texto: String) {
    Text(texto, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
}

@Composable
private fun CampoPercentual(
    label: String,
    value: Int,
    onChange: (Int) -> Unit,
    hint: String? = null,
    min: Int = -50,
    max: Int = 50,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("$label (%)", style = MaterialTheme.typography.labelSmall)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onChange((value - 5).coerceIn(min, max)) }, modifier = Modifier.size(40.dp)) { Text("−") }
            OutlinedTextField(
                value = value.toString(),
                onValueChange = { txt -> txt.toIntOrNull()?.let { onChange(it.coerceIn(min, max)) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = { onChange((value + 5).coerceIn(min, max)) }, modifier = Modifier.size(40.dp)) { Text("+") }
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
