package com.bragro.mobile.ui.domain

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Um item da fileira de ícones do módulo -- pedido do usuário ("reduza os
 * blocos que são compatíveis a ícones, distribua numa linha só abaixo do
 * título do módulo, cabem até uns 9"). Cada bloco que antes ocupava uma
 * linha inteira só pra mostrar seu próprio cabeçalho (ícone + título + seta
 * de expandir) agora vira UM ícone aqui; tocar abre/fecha o conteúdo do
 * bloco correspondente logo abaixo desta fileira.
 */
data class ModuleIconItem(
    val key: String,
    val icon: ImageVector,
    val label: String,
    val active: Boolean = false,
    val badgeCount: Int = 0,
)

/**
 * Um ícone individual da fileira, com toque longo mostrando o nome num
 * Toast rápido -- pedido do usuário ("aparecer o nome do botão quando
 * passar o dedo em cima do ícone"), já que o texto não cabe mais do lado.
 * Exposto separado de [ModuleIconRow] pra poder ser misturado com outros
 * controles (ex.: o dropdown de Período) na mesma fileira, quando a tela
 * precisa montar a linha na mão em vez de usar a lista genérica.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ModuleIconButton(item: ModuleIconItem, onClick: () -> Unit) {
    val context = LocalContext.current
    BadgedBox(
        badge = { if (item.badgeCount > 0) Badge { Text("${item.badgeCount}") } },
        modifier = Modifier
            .size(40.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = { android.widget.Toast.makeText(context, item.label, android.widget.Toast.LENGTH_SHORT).show() },
            ),
    ) {
        Icon(
            item.icon,
            contentDescription = item.label,
            tint = if (item.active) MaterialTheme.colorScheme.primary else LocalContentColor.current,
            modifier = Modifier
                .padding(8.dp)
                .size(22.dp),
        )
    }
}

/**
 * Fileira compacta e distribuída dos ícones de um módulo. Usa FlowRow em vez
 * de Row simples pra, se não couberem todos numa linha só num aparelho mais
 * estreito, quebrar pra uma segunda linha em vez de espremer os ícones a
 * ponto de ficarem difíceis de tocar.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ModuleIconRow(items: List<ModuleIconItem>, onClick: (String) -> Unit) {
    if (items.isEmpty()) return
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items.forEach { item -> ModuleIconButton(item) { onClick(item.key) } }
    }
}
