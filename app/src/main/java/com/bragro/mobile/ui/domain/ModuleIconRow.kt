package com.bragro.mobile.ui.domain

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bragro.mobile.ui.theme.Card

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
 * Um ícone individual da fileira. O nome do bloco não aparece mais num
 * Toast ao tocar -- pedido do usuário ("retire aquele blazinho") -- em vez
 * disso, o título do módulo (na barra do topo) alterna pro nome do bloco
 * enquanto ele estiver aberto (ver DomainListScreen.kt). Exposto separado
 * de [ModuleIconRow] pra poder ser misturado com outros controles (ex.: o
 * dropdown de Período) na mesma fileira, quando a tela precisa montar a
 * linha na mão em vez de usar a lista genérica.
 */
// Altura fixa (ícone 22dp + rótulo labelSmall) -- pedido do usuário
// ("coloque rótulos nos ícones... alinhe também a altura de todos os
// ícones, tem alguns que não estão na mesma altura"). Antes era só um
// BadgedBox de 40dp com o ícone, sem nenhum texto visível (o `label` só
// virava contentDescription, pra leitor de tela). Agora ícone+rótulo
// formam uma coluna com largura própria (não é mais uma caixa fixa de
// 40dp) -- se o texto não couber no espaço do bloco, o bloco é quem
// alarga (ver os `weight()` dos Row que os contêm em DomainListScreen.kt/
// FinanceiroScreen.kt), não o ícone que encolhe.
private val MODULE_ICON_SIZE = 22.dp

// Fundo verde translúcido nos blocos de ícone -- pedido do usuário ("nos
// blocos de ícones preencha o fundo de verde"). Só aqui (ModuleIconButton/
// LabeledIconButton), não no Card padrão do app inteiro (com.bragro.mobile.
// ui.theme.Card, AppCard.kt) -- lá o usuário já tinha pedido o oposto antes
// ("retire o preenchimento verde e deixe apenas a borda em verde, em todo
// app"). alpha baixo (12%) mantém o ícone/texto legíveis por cima (alguns
// ícones já usam tint=primary/verde no estado ativo) e não briga com o verde
// mais forte da borda (cardBorderColor, em AppCard.kt).
private val MODULE_ICON_BG_ALPHA = 0.12f

// Cada ícone virou seu próprio bloco (Card individual) -- pedido do usuário
// ("em todos os módulos, por categorias: dados, operações, arquivos torne-os
// blocos com ícones individuais"), mesmo tratamento já aplicado à seleção de
// visão do Financeiro (Todos/Pagar/Receber/...). Como TODOS os blocos
// Dados/Operações/Arquivos (genéricos em DomainListScreen.kt e os próprios do
// Financeiro em FinanceiroScreen.kt) montam seu conteúdo chamando este
// composable, a mudança aqui já vale pra "todos os módulos" de uma vez, sem
// precisar editar bloco por bloco.
@Composable
fun ModuleIconButton(item: ModuleIconItem, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.widthIn(min = 44.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = MODULE_ICON_BG_ALPHA)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BadgedBox(badge = { if (item.badgeCount > 0) Badge { Text("${item.badgeCount}") } }) {
                Icon(
                    item.icon,
                    contentDescription = null,
                    tint = if (item.active) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                    modifier = Modifier.size(MODULE_ICON_SIZE),
                )
            }
            Text(
                item.label,
                style = MaterialTheme.typography.labelSmall,
                color = if (item.active) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Mesmo padrão de ícone+rótulo do [ModuleIconButton], mas pra ações que não
 * usam [ModuleIconItem] (Atualizar, Armazenamento, Exportar CSV/PDF,
 * Imprimir etc.) -- essas eram só `IconButton { Icon(...) }` sem nenhum
 * texto visível, espalhadas em vários blocos de DomainListScreen.kt/
 * FinanceiroScreen.kt. Ícone sempre [MODULE_ICON_SIZE] e rótulo sempre na
 * mesma posição (embaixo) pra ficar com a MESMA altura do [ModuleIconButton]
 * -- pedido do usuário ("alinhe também a altura de todos os ícones").
 * `loading = true` troca o ícone por um spinner do mesmo tamanho (usado no
 * ícone Atualizar enquanto `refreshing` está true).
 */
@Composable
fun LabeledIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: androidx.compose.ui.graphics.Color = LocalContentColor.current,
    loading: Boolean = false,
) {
    Card(
        onClick = onClick,
        enabled = !loading,
        modifier = modifier.widthIn(min = 44.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = MODULE_ICON_BG_ALPHA)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(MODULE_ICON_SIZE), strokeWidth = 2.dp)
            } else {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(MODULE_ICON_SIZE))
            }
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = tint,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
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
