package com.bragro.mobile.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bragro.mobile.data.model.HomeBreakdownData
import com.bragro.mobile.ui.theme.BrBlue
import com.bragro.mobile.ui.theme.BrGreen
import com.bragro.mobile.ui.theme.BrYellow
import com.bragro.mobile.ui.theme.Card
import java.text.NumberFormat
import java.util.Locale

// Réplica mobile do indicador "barra segmentada por categoria" do dashboard
// web (ver dashboard-breakdown.ts/category-breakdown-card.tsx) -- pedido do
// usuário ("replique o que ainda falta da plataforma no native"). Mesmo
// visual/critério do bloco Custo médio/ha do Canvas (CanvasDetailCard em
// CanvasSection.kt): cores POSICIONAIS (por ordem de valor, não por nome de
// categoria), top-3 + "Outros" já vem pronto do backend. Arquivo próprio
// (em vez de dentro de HomeScreen.kt/CanvasSection.kt) porque as duas
// funções auxiliares dessas telas (formatMoneyBrl/BREAKDOWN_COLORS) são
// `private` a nível de arquivo -- Kotlin não deixa reaproveitar entre
// arquivos mesmo no mesmo pacote.
private val CATEGORY_BREAKDOWN_COLORS = listOf(BrGreen, BrBlue, BrYellow, Color(0xFF9E9E9E))

private val moneyFmt = NumberFormat.getCurrencyInstance(Locale("pt", "BR")).apply {
    minimumFractionDigits = 2
    maximumFractionDigits = 2
}
private fun formatMoneyCategoryCard(v: Double): String = moneyFmt.format(v)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryBreakdownCard(titulo: String, data: HomeBreakdownData, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(titulo, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Column(horizontalAlignment = Alignment.End) {
                    Text(data.valorLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        data.valor?.let { formatMoneyCategoryCard(it) } ?: "—",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            if (data.items.isNotEmpty()) {
                val total = data.items.sumOf { it.valor }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(50)),
                ) {
                    data.items.forEachIndexed { i, b ->
                        val pct = if (total > 0) (b.valor / total).toFloat() else 0f
                        if (pct > 0f) {
                            Box(
                                modifier = Modifier
                                    .weight(pct)
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .background(CATEGORY_BREAKDOWN_COLORS[i % CATEGORY_BREAKDOWN_COLORS.size]),
                            )
                        }
                    }
                }
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    data.items.forEachIndexed { i, b ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(CATEGORY_BREAKDOWN_COLORS[i % CATEGORY_BREAKDOWN_COLORS.size]),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(b.categoria, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                Text(
                    "Sem lançamentos nos últimos 30 dias.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
