package com.bragro.mobile.ui.domain

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import com.bragro.mobile.ui.theme.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.util.Locale

// Réplica das calculadoras de apoio de agronomic-calculators.tsx -- pura
// ferramenta de cálculo (não grava nada no banco), por isso é 100% Kotlin
// local, sem endpoint novo nenhum. Mesmas fórmulas, mesmos rótulos/unidades.

/** Campo numérico com estado próprio -- espelho de useField() no site
 * (aceita vírgula OU ponto decimal). null quando vazio/inválido. */
@Composable
fun rememberCalcField(): CalcFieldState = remember { CalcFieldState() }

class CalcFieldState {
    var text by mutableStateOf("")
    val n: Double?
        get() {
            if (text.isBlank()) return null
            val v = text.replace(",", ".").toDoubleOrNull()
            return if (v != null && v.isFinite()) v else null
        }
}

private val PT_BR = Locale("pt", "BR")

fun fmtCalc(n: Double?, decimals: Int = 2): String {
    if (n == null || !n.isFinite()) return "—"
    val nf = NumberFormat.getNumberInstance(PT_BR)
    nf.maximumFractionDigits = decimals
    nf.minimumFractionDigits = 0
    return nf.format(n)
}

fun fmtCalcBrl(n: Double?): String {
    if (n == null || !n.isFinite()) return "—"
    return NumberFormat.getCurrencyInstance(PT_BR).format(n)
}

@Composable
fun CalcNumField(label: String, unit: String? = null, field: CalcFieldState) {
    OutlinedTextField(
        value = field.text,
        onValueChange = { field.text = it },
        label = { Text(if (unit != null) "$label ($unit)" else label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun CalcResultField(label: String, unit: String? = null, value: String) {
    Column {
        Text(
            if (unit != null) "$label ($unit)" else label,
            style = MaterialTheme.typography.labelSmall,
        )
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** Card individual de UMA calculadora, com título+ícone (emoji, igual ao
 * site) no topo e os campos empilhados embaixo. */
@Composable
fun CalcCard(icon: String, title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("$icon $title", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            content()
        }
    }
}

private val CALC_TITLES = mapOf(
    "safra" to "Calculadoras agronômicas",
    "colheita" to "Calculadoras agronômicas",
    "financeiro" to "Calculadoras financeiras",
)

/** Espelho de AgronomicCalculators({domainId, compact}) -- bloco recolhível
 * (fechado por padrão), só aparece nos 3 domínios que têm calculadora no
 * site (safra/colheita/financeiro); em qualquer outro módulo não renderiza
 * nada, igual ao "return null" do componente web. */
@Composable
fun CalculatorsCard(domainId: String) {
    val title = CALC_TITLES[domainId] ?: return
    var open by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { open = !open }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Calculate, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = if (open) "Recolher" else "Expandir")
            }
            if (open) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    when (domainId) {
                        "safra" -> SafraCalculators()
                        "colheita" -> ColheitaCalculators()
                        "financeiro" -> FinanceiroCalculators()
                    }
                }
            }
        }
    }
}
