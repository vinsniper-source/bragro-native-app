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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

// Fundo dos blocos de ícone = surface (sem preenchimento verde) -- ver
// BottomNavBar.kt. Ícone/rótulo em preto/branco (onSurface, adapta sozinho
// ao tema claro/escuro) -- pedido do usuário ("coloque fontes e ícones
// branco/preto e tire o fundo verde de todos os blocos individuais do app
// native"): reverte o esquema anterior (fundo = surface, ícone/rótulo =
// primary/verde) pro ícone/rótulo também deixarem de ser verdes. Como TODOS
// os blocos individuais (Dados/Operações/Arquivos, Filtros, Período,
// Gráficos, Calculadoras, Imprimir, Nuvem, Copiar etc., em todos os
// módulos) passam por ModuleIconButton/LabeledIconButton, essa única
// mudança já vale pra "todos os blocos individuais" de uma vez.
private val MODULE_ICON_FG: Color
    @Composable get() = MaterialTheme.colorScheme.onSurface

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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        // elevation zerada -- ESCOPO FINAL (usuário: "isso agora nos blocos
        // individuais será apenas tirar a cor verde dos icones e rotulos"):
        // o tom de fundo verde (3.dp de tonal elevation) fica reservado só
        // pra barra inferior/dropdown (ver BottomNavBar.kt) -- aqui nos
        // blocos individuais o fundo continua neutro (flat), só ícone/
        // rótulo (MODULE_ICON_FG) mudam pra onSurface. Sem zerar, o
        // Material3 mistura um pouco de "primary" por cima do
        // containerColor sempre que ele é EXATAMENTE colorScheme.surface e
        // a elevação é > 0.
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            focusedElevation = 0.dp,
            hoveredElevation = 0.dp,
            draggedElevation = 0.dp,
            disabledElevation = 0.dp,
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BadgedBox(badge = { if (item.badgeCount > 0) Badge { Text("${item.badgeCount}") } }) {
                Icon(
                    item.icon,
                    contentDescription = null,
                    tint = MODULE_ICON_FG,
                    modifier = Modifier.size(MODULE_ICON_SIZE),
                )
            }
            Text(
                item.label,
                style = MaterialTheme.typography.labelSmall,
                color = MODULE_ICON_FG,
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
    // Preto/branco (onSurface) por padrão -- BUG real encontrado (usuário:
    // "nao houve alteraçao nenhuma", print mostrando Gráficos/Filtros/
    // Colunas/Expandir ainda verdes mesmo com o apk novo confirmado
    // instalado): eu só removia o `tint = primary` EXPLÍCITO de call sites
    // específicos (Período/Filtros em alguns arquivos), mas nunca troquei
    // esse valor padrão aqui -- todo call site que NÃO passa `tint` (a
    // maioria: Gráficos, Colunas, Expandir, Imprimir, Nuvem, Copiar,
    // Atualizar, Exportar CSV/PDF etc., em todos os módulos) continuava
    // caindo em verde por causa deste default, mesmo com MODULE_ICON_FG já
    // corrigido em ModuleIconButton (que é uma função diferente, sem esse
    // parâmetro). Call sites que já passam um `tint` próprio (ex.: alternar
    // cor quando "ativo") continuam funcionando normalmente.
    tint: Color = MaterialTheme.colorScheme.onSurface,
    loading: Boolean = false,
) {
    Card(
        onClick = onClick,
        enabled = !loading,
        modifier = modifier.widthIn(min = 44.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        // elevation zerada -- mesmo escopo final do ModuleIconButton acima:
        // fundo neutro nos blocos individuais, o tom verde fica só na barra
        // inferior/dropdown (BottomNavBar.kt).
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            focusedElevation = 0.dp,
            hoveredElevation = 0.dp,
            draggedElevation = 0.dp,
            disabledElevation = 0.dp,
        ),
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
