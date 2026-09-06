package com.bragro.mobile.ui.domain

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
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

// Borda um pouco mais ESCURA que o fundo do bloco (em vez de outlineVariant,
// que num tema escuro fica quase idêntico ao fundo -- pedido do usuário
// (achado de auditoria: "os icones não estão centralizados e sem aborda
// vertical para separar, coloque a borda um pouco mais escura do que a cor
// do retangulo"): mistura 18% de preto na própria cor do bloco em vez de
// usar um tom fixo do tema, então a borda SEMPRE contrasta com o fundo dela
// mesma, no claro e no escuro. Usado tanto pela borda externa quanto pelas
// divisórias verticais do EqualWidthBlockRow, e reaproveitado pelas células
// da vista Tabela (ver RecordTable.kt, mesmo pacote).
fun darkerBorderColor(base: Color, amount: Float = 0.18f): Color = lerp(base, Color.Black, amount)

// Cada ícone virou seu próprio bloco (Card individual) -- pedido do usuário
// ("em todos os módulos, por categorias: dados, operações, arquivos torne-os
// blocos com ícones individuais"), mesmo tratamento já aplicado à seleção de
// visão do Financeiro (Todos/Pagar/Receber/...). Como TODOS os blocos
// Dados/Operações/Arquivos (genéricos em DomainListScreen.kt e os próprios do
// Financeiro em FinanceiroScreen.kt) montam seu conteúdo chamando este
// composable, a mudança aqui já vale pra "todos os módulos" de uma vez, sem
// precisar editar bloco por bloco.
// SEM Card próprio -- pedido do usuário (achado de auditoria: "os blocos
// icones e rotulos continuam desconfigurados... não foi aplicado a forma de
// retângulo com divisão em bordas verticais"): antes cada ícone tinha seu
// PRÓPRIO Card (cantos arredondados + fundo), dentro do retângulo já
// arredondado/bordado do [EqualWidthBlockRow] -- um Card dentro de outro,
// then cada célula ainda parecia um "chip" flutuante separado em vez de uma
// fatia lisa de UM retângulo único. Agora o "bloco" é só o container
// [EqualWidthBlockRow] (fundo + borda leve + divisórias verticais); cada
// ícone aqui dentro é só conteúdo (ícone+rótulo) clicável, sem fundo/forma
// próprios.
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ModuleIconButton(item: ModuleIconItem, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .widthIn(min = 44.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 8.dp),
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
            overflow = TextOverflow.Clip,
            // Letreiro (marquee) em vez de "..." -- pedido do usuário
            // ("tem como aparecer como um letreiro se movendo? aplique
            // em todo app que tiver fontes cortadas"). Só anima quando o
            // texto realmente não cabe no espaço disponível -- rótulo
            // que cabe fica parado normal, sem nenhum efeito.
            modifier = Modifier.basicMarquee(),
        )
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
@OptIn(ExperimentalFoundationApi::class)
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
    // Sem Card próprio -- mesmo motivo do ModuleIconButton acima.
    Column(
        modifier = modifier
            .widthIn(min = 44.dp)
            .clickable(enabled = !loading, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 8.dp),
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
            overflow = TextOverflow.Clip,
            modifier = Modifier.basicMarquee(),
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

/**
 * Fileira de blocos INTEIROS: mesma largura entre si, nunca quebra pra uma
 * 2ª linha (rótulo que não couber vira "..." em vez de empurrar o bloco pra
 * baixo), dentro de um contorno único com uma borda vertical fina separando
 * cada célula -- pedido do usuário (achado de auditoria: "nos blocos
 * individuais de dentro dos módulos das categorias dados, operações e
 * arquivos, crie bloco inteiro separados por bordas na vertical na mesma
 * linha, com as mesmas medidas entre eles, se algum rótulo não couber
 * coloque como gerador de caracteres"). Usado por ModuleCategoryBlock/
 * ModuleCategoryTabs (DomainListScreen.kt) e FinanceiroCategoryBlock/
 * FinanceiroCategoryTabs (FinanceiroScreen.kt) -- os ÚNICOS 4 pontos que
 * renderizam o conteúdo horizontal de Dados/Operações/Arquivos (e blocos
 * equivalentes) em TODOS os módulos, então trocar só ali (de FlowRow pra
 * este composable) já vale pro app inteiro, sem precisar editar bloco por
 * bloco em cada módulo.
 *
 * Não é um SegmentedButtonRow do Material3 (mesmo efeito visual: células
 * iguais + borda entre elas) porque aqui dentro nem todo item é uma escolha
 * única/exclusiva -- tem ação pura (Imprimir, Atualizar, Exportar) misturada
 * com toggle (Gráficos, Filtros) e até dropdown (Banco, Colunas Visíveis),
 * então não cabe a semântica de "selected" do SegmentedButton.
 *
 * Layout customizado (em vez de Row + weight por item) porque o número real
 * de células só é conhecido depois de compor o conteúdo -- vários ícones são
 * condicionais (ex.: "Gráficos" some quando isQuickView), então cada bloco
 * tem uma contagem diferente e variável; este Layout mede quantos filhos
 * realmente vieram e divide a largura igualmente entre eles.
 */
@Composable
fun EqualWidthBlockRow(modifier: Modifier = Modifier, drawBackground: Boolean = true, content: @Composable () -> Unit) {
    // Retângulo reto (SEM cantos arredondados) com fundo preenchido -- pedido
    // do usuário (achado de auditoria: "retire as bordas redondas e torne um
    // retângulo com borda bem leve na tonalidade do retângulo inteiro").
    // Fundo = surface (mesmo tom neutro que cada ícone tinha individualmente
    // antes) -- precisa de fundo AQUI agora porque ModuleIconButton/
    // LabeledIconButton deixaram de ter Card/fundo próprios (ver comentário
    // lá) -- sem isso o conteúdo ficaria "boiando" sem nenhum contorno
    // visível de bloco. Borda = outlineVariant (já era o tom mais leve
    // disponível no Material3 -- "na tonalidade do retângulo inteiro" quer
    // dizer sutil/próxima do fundo, não um contraste forte).
    // "drawBackground=false" -- caso do bloco Faturamento (Cobranças), que já
    // vive dentro de um Card com fundo verde translúcido próprio (pedido
    // anterior do usuário): sem essa opção, o fundo opaco "surface" daqui
    // cobriria o verde do Card pai. Continua desenhando borda/divisórias
    // normalmente, só pula o preenchimento de fundo.
    val blockBg = MaterialTheme.colorScheme.surface
    val dividerColor = darkerBorderColor(blockBg)
    // Contagem de células só é conhecida durante a medição (measurables.size
    // abaixo) -- guardada aqui pra o drawBehind (fase de desenho, que roda
    // DEPOIS da medição desse mesmo nó, no mesmo frame) saber onde
    // desenhar as bordas verticais entre as células.
    val itemCount = remember { mutableIntStateOf(0) }
    Layout(
        content = content,
        modifier = modifier
            .fillMaxWidth()
            .then(if (drawBackground) Modifier.background(blockBg) else Modifier)
            .border(1.dp, dividerColor)
            .drawBehind {
                val n = itemCount.intValue
                if (n > 1) {
                    val itemWidthPx = size.width / n
                    val strokeWidthPx = 1.dp.toPx()
                    for (i in 1 until n) {
                        val x = itemWidthPx * i
                        drawLine(dividerColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = strokeWidthPx)
                    }
                }
            },
    ) { measurables, constraints ->
        if (measurables.isEmpty()) {
            itemCount.intValue = 0
            return@Layout layout(constraints.minWidth, 0) {}
        }
        itemCount.intValue = measurables.size
        val totalWidth = constraints.maxWidth
        val itemWidth = totalWidth / measurables.size
        val itemConstraints = Constraints(minWidth = itemWidth, maxWidth = itemWidth, minHeight = 0, maxHeight = constraints.maxHeight)
        val placeables = measurables.map { it.measure(itemConstraints) }
        val height = placeables.maxOf { it.height }
        layout(totalWidth, height) {
            // Centralizado nos dois eixos -- BUG real de auditoria
            // encontrado ("os icones não estão centralizados"): o item já
            // recebia Constraints(minWidth=maxWidth=itemWidth), então em
            // teoria devia preencher a célula sozinho, mas alguns filhos
            // (ex.: BancoDropdown/ColumnsPickerButton, que não são só
            // ModuleIconButton/LabeledIconButton) medem mais estreito que
            // isso -- antes cada placeable ia pro CANTO ESQUERDO da sua
            // célula (x = índice * itemWidth), sobrando o espaço vazio
            // inteiro à direita. Agora X também centraliza o placeable
            // dentro da largura da própria célula, igual já acontecia com Y.
            placeables.forEachIndexed { index, placeable ->
                val cellX = index * itemWidth
                val x = cellX + (itemWidth - placeable.width) / 2
                val y = (height - placeable.height) / 2
                placeable.placeRelative(x, y)
            }
        }
    }
}
