package com.bragro.mobile.ui.domain

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import com.bragro.mobile.ui.theme.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bragro.mobile.data.repo.ModuleActionsRepository
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// Réplica mobile dos botões sem formulário: "Recalcular Área" (Safra/Frota),
// "Recalcular Vencimentos" (Financeiro, ver FinanceiroScreen.kt) e o gráfico
// "Eficiência de Frota" -- todos batem em /api/mobile/module-actions.

class ModuleActionButtonViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = ModuleActionsRepository(app)
    var running = mutableStateOf(false)
        private set
    var resultMessage = mutableStateOf<String?>(null)
        private set

    fun run(action: String, successMessage: String) {
        if (running.value) return
        running.value = true
        resultMessage.value = null
        viewModelScope.launch {
            val result = repo.run(action)
            running.value = false
            resultMessage.value = if (result != null) successMessage else "Sem conexão -- não foi possível executar agora."
        }
    }
}

/** Botão "Recalcular Área" -- reprocessa a Área(ha) de todo o histórico do
 * módulo a partir do cadastro ATUAL de Fazendas. Mesmo texto/motivo do site. */
@Composable
fun RecalcularAreaButton(domainId: String, viewModel: ModuleActionButtonViewModel = viewModel(), showHeader: Boolean = true) {
    val running by viewModel.running
    val message by viewModel.resultMessage
    val action = if (domainId == "safra") "recalcular-area-safra" else "recalcular-area-frota"

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (showHeader) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Recalcular Área", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    // So icone (sem texto no botao) -- pedido do usuario
                    // ("transforme os botoes de operacao em so icone"), o
                    // titulo acima ja diz o que o botao faz.
                    IconButton(onClick = { viewModel.run(action, "Área recalculada com sucesso.") }, enabled = !running) {
                        if (running) CircularProgressIndicator(modifier = Modifier.padding(2.dp)) else Icon(Icons.Filled.Autorenew, contentDescription = "Recalcular Área")
                    }
                }
            } else {
                // Fileira de ícones do módulo já revelou este bloco -- aqui
                // dispara a ação direto, sem precisar de outro toque.
                LaunchedEffect(Unit) { viewModel.run(action, "Área recalculada com sucesso.") }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Recalcular Área", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    if (running) CircularProgressIndicator(modifier = Modifier.padding(2.dp).size(18.dp))
                }
            }
            Text(
                "Reprocessa a Área (ha) de todo o histórico a partir do cadastro atual de Fazendas -- útil depois de corrigir uma fazenda cadastrada após o lançamento.",
                style = MaterialTheme.typography.bodySmall,
            )
            if (message != null) Text(message!!, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Botão "Recalcular Vencimentos" do Financeiro -- só ícone (sem texto no
 * botão), pedido do usuário ("transforme os botões de operação em só
 * ícone"); mesmo critério do site (recalcular-vencimentos-button.tsx). */
@Composable
fun RecalcularVencimentosButton(viewModel: ModuleActionButtonViewModel = viewModel(), showHeader: Boolean = true) {
    val running by viewModel.running
    val message by viewModel.resultMessage

    Column {
        if (showHeader) {
            IconButton(onClick = { viewModel.run("recalcular-vencimentos", "Vencimentos recalculados com sucesso.") }, enabled = !running) {
                if (running) CircularProgressIndicator(modifier = Modifier.padding(2.dp)) else Icon(Icons.Filled.Autorenew, contentDescription = "Recalcular Vencimentos")
            }
        } else {
            // Fileira de ícones do módulo já revelou este bloco -- dispara a
            // ação direto, sem precisar de outro toque.
            LaunchedEffect(Unit) { viewModel.run("recalcular-vencimentos", "Vencimentos recalculados com sucesso.") }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Recalcular Vencimentos", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (running) CircularProgressIndicator(modifier = Modifier.padding(2.dp).size(18.dp))
            }
        }
        if (message != null) Text(message!!, style = MaterialTheme.typography.bodySmall)
    }
}

class FleetEfficiencyViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = ModuleActionsRepository(app)
    var data = mutableStateOf<JsonObject?>(null)
        private set
    var loaded = mutableStateOf(false)
        private set

    fun load() {
        if (loaded.value) return
        viewModelScope.launch {
            data.value = repo.run("fleet-efficiency")
            loaded.value = true
        }
    }
}

/** Gráfico "Eficiência de Frota" (L/h por máquina vs média da frota) --
 * espelho de fleet-efficiency-chart.tsx. */
@Composable
fun FleetEfficiencyCard(viewModel: FleetEfficiencyViewModel = viewModel()) {
    LaunchedEffect(Unit) { viewModel.load() }
    val data by viewModel.data
    val loaded by viewModel.loaded

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Eficiência de Frota (L/h)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            when {
                !loaded -> Text("Carregando...", style = MaterialTheme.typography.bodySmall)
                data == null -> Text("Sem conexão -- não foi possível carregar.", style = MaterialTheme.typography.bodySmall)
                else -> {
                    val maquinas = data!!["maquinas"]?.jsonArray.orEmpty().mapNotNull { it as? JsonObject }
                    val mediaGeral = data!!["mediaGeral"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                    if (maquinas.isEmpty()) {
                        Text("Sem abastecimentos suficientes ainda para calcular a eficiência.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        val categories = maquinas.map { it["frota"]?.jsonPrimitive?.content ?: "—" }
                        val values = maquinas.map { it["litrosPorHora"]?.jsonPrimitive?.doubleOrNull ?: 0.0 }
                        val acimaMedia = maquinas.map { it["acimaMedia"]?.jsonPrimitive?.content == "true" }
                        SimpleBarChart(
                            categories = categories,
                            series = listOf(
                                BarSeries(
                                    "L/h",
                                    values,
                                    // Cor por barra não é suportado pelo SimpleBarChart genérico
                                    // (série única = mesma cor) -- mantemos o vermelho só como
                                    // destaque textual abaixo, em vez de colorir barra a barra.
                                    Color(0xFF2F6F4F),
                                ),
                            ),
                            isMoney = false,
                        )
                        Text("Média da frota: ${formatChartValue(mediaGeral, false)} L/h", style = MaterialTheme.typography.bodySmall)
                        val acima = maquinas.filterIndexed { i, _ -> acimaMedia.getOrElse(i) { false } }.map { it["frota"]?.jsonPrimitive?.content }
                        if (acima.isNotEmpty()) {
                            Text(
                                "Acima da média (+15%): ${acima.joinToString(", ")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }
}
