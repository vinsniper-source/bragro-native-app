package com.bragro.mobile.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Agriculture
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private data class TourStep(val icon: androidx.compose.ui.graphics.vector.ImageVector, val title: String, val body: String)

// 3 passos -- pedido do usuário (mesmo número do wizard do site, task
// #135), mas conteúdo diferente: aqui não é "criar organização" (quem entra
// pelo app já tem conta pronta, ver OnboardingStore.kt), é um tour rápido
// de orientação pelas 3 áreas centrais da Início: o Canvas/filtros (onde
// fica o resumo visual das fazendas), a barra inferior (onde ficam todos os
// módulos) e a captura rápida (Romaneio/Abastecimento e "Copiar último
// lançamento", os atalhos que poupam mais tempo no dia a dia).
private val TOUR_STEPS = listOf(
    TourStep(
        Icons.Filled.Agriculture,
        "Bem-vindo ao BRAgro",
        "Na tela Início você vê o resumo visual das suas fazendas (Canvas) e pode filtrar por Fazenda, Safra ou Cultura a qualquer momento -- toque nos filtros logo abaixo do slogan.",
    ),
    TourStep(
        Icons.Filled.Groups,
        "Todos os módulos, num toque",
        "A barra inferior leva pra Financeiro, Safra, Estoque, Frota e todos os outros módulos. O menu (ícone de grade) mostra a lista completa quando precisar de algo que não está fixo ali.",
    ),
    TourStep(
        Icons.Filled.Bolt,
        "Atalhos que poupam tempo",
        "Em cada módulo, o botão \"Copiar último lançamento\" pré-preenche um registro novo a partir do anterior. Romaneio Rápido e Abastecimento ficam nos atalhos do topo da Início.",
    ),
)

/** Tour de orientação (Task #296/#344) -- ver TOUR_STEPS acima pro
 * conteúdo. Mostrado uma vez só (OnboardingStore), sem opção de pular no
 * meio de propósito: são só 3 telas curtas, então "Pular" ganharia pouco e
 * tiraria a chance da pessoa ver o essencial na primeira sessão -- ela pode
 * fechar a qualquer momento pelo "X" do sistema (dismissible = false só no
 * clique fora, ver onDismissRequest abaixo) se realmente não quiser ver. */
@Composable
fun OnboardingTourDialog(onFinish: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    val current = TOUR_STEPS[step]
    val isLast = step == TOUR_STEPS.lastIndex

    AlertDialog(
        onDismissRequest = onFinish,
        confirmButton = {
            TextButton(onClick = { if (isLast) onFinish() else step++ }) {
                Text(if (isLast) "Começar" else "Próximo")
            }
        },
        dismissButton = {
            if (!isLast) {
                TextButton(onClick = onFinish) { Text("Pular") }
            }
        },
        icon = {
            Column(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
            ) {
                Icon(
                    current.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(12.dp),
                )
            }
        },
        title = { Text(current.title, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
        text = {
            Column {
                Text(current.body, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
                // Indicador de progresso (3 bolinhas) -- espelha visualmente
                // o OnboardingSteps do site (onboarding-steps.tsx), só que
                // como indicador de posição em vez de passo obrigatório.
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
                    TOUR_STEPS.indices.forEach { i ->
                        Column(
                            modifier = Modifier
                                .padding(horizontal = 3.dp)
                                .size(if (i == step) 8.dp else 6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (i == step) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant,
                                ),
                        ) {}
                    }
                }
            }
        },
    )
}
