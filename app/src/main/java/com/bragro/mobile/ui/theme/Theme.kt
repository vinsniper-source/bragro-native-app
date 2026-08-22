package com.bragro.mobile.ui.theme

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.bragro.mobile.data.ThemeMode
import com.bragro.mobile.data.ThemeStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

// Paleta com as cores da bandeira do Brasil, mesmo espirito visual do site
// (ver Task #62/#70 no historico do projeto) -- verde como cor principal.
// Nao-privadas (usadas tambem em ui/home/DomainVisuals.kt pra colorir cada
// secao de modulos igual ao agrupamento do site -- ver sidebar-nav.tsx).
// BrGreen (cor de categoria, sem relação com claro/escuro) continua igual.
val BrGreen = Color(0xFF2F6F4F)
val BrYellow = Color(0xFFF2C037)
val BrBlue = Color(0xFF1E4B8A)
// Quarta cor de apoio (fora da bandeira) so pra diferenciar a 4a secao
// ("Pessoas") das outras 3, que ja usam verde/amarelo/azul.
val BrOrange = Color(0xFFD9822B)

// Duas paletas de verde -- uma por tema -- pedido do usuário ("o ideal é
// trabalhar com duas variáveis de verde diferentes... uma paleta mais
// suave e luminosa para o Dark Mode e uma mais densa e contrastante para
// o Light Mode"). No claro, verde mais escuro/saturado (tom esmeralda
// fechado) pra não perder contraste sob luz ambiente forte; no escuro,
// verde mais suave/acinzentado (evita o efeito de "vibração" que um verde
// muito saturado causa em cima de fundo quase-preto).
val BrGreenPrimaryLight = Color(0xFF1B4D33)
val BrGreenPrimaryDark = Color(0xFF6FA98C)

// Mesmo problema do verde (ver acima), agora pro azul (BrBlue) -- pedido do
// usuário ("coloque as cores das fontes preto/branco modo claro/escuro"):
// `tertiary` usava BrBlue cru (#1E4B8A) nos DOIS temas, e esse azul escuro
// praticamente some em cima do fundo quase-preto do Escuro (baixíssimo
// contraste). Igual ao par Light/Dark do verde, uma versão mais clara só
// pro tema escuro -- qualquer `Text`/`Icon` que usava BrBlue direto (não
// MaterialTheme.colorScheme.tertiary) foi trocado pra usar o token do tema,
// que agora resolve certo nos dois casos.
val BrBlueTertiaryDark = Color(0xFF7FAEEA)

// Antes só `primary/secondary/tertiary` eram definidos -- lightColorScheme()/
// darkColorScheme() NÃO derivam os outros ~20 tokens (surface, surfaceVariant,
// primaryContainer, outline etc.) a partir deles; o resto ficava no roxo
// neutro padrão do Material3, sem nenhum verde -- exatamente o que o usuário
// notou ("a tonalidade do verde... não foi aplicado nos blocos cinzas, kpis
// e botão +"). Definindo esses tokens manualmente (mistura simples de
// BrGreen com branco/preto, já que não temos a lib material-color-utilities
// pra gerar a paleta tonal de verdade) pra todo Card/FAB/superfície do app
// carregar o mesmo tom de verde da barra inferior, em vez do roxo genérico.
// Revertido -- pedido do usuário ("volte a letra preta e branca no modo
// escuro/claro"): a troca fundo<->texto de uma rodada anterior ficou
// ilegível nos testes, então letra/ícone voltam ao padrão convencional
// (claro = letra escura, escuro = letra clara).
//
// Textura única por tema -- pedido do usuário ("a textura do app será
// apenas uma em cada módulo, uma textura branca / parecida com preta e
// todas as bordas finas contornadas com verde", depois refinado pra "use
// as cores off white e cinza chumbo", depois "coloque a textura do modo
// escuro quase preto"). `background` e `surface`/`surfaceVariant` usam A
// MESMA cor plana -- off-white no claro, quase preto no escuro. Os
// blocos deixam de ter um tom de verde próprio; quem separa um bloco do
// fundo é só a borda fina verde (ver AppCard.kt/cardBorderColor, que usa
// MaterialTheme.colorScheme.primary = BrGreen).
private val LightColors = lightColorScheme(
    // Verde mais escuro/saturado no claro -- pedido do usuário ("aumente o
    // contraste... use um verde mais profundo, tom folha/esmeralda
    // fechado"), em vez do BrGreen puro (mais claro, perdia contraste no
    // fundo off-white sob luz forte).
    primary = BrGreenPrimaryLight,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD1DFD8),
    onPrimaryContainer = Color(0xFF0E2B1C),
    secondary = BrYellow,
    secondaryContainer = Color(0xFFFCEFCD),
    onSecondaryContainer = Color(0xFF4D3C00),
    tertiary = BrBlue,
    // Fundo verde bem mais perceptível -- pedido do usuário ("deixe o tom
    // bem mais forte/perceptível, puxando mais pro verde... a cor da barra
    // inferior seja verde também"). primary=BrGreenPrimaryLight misturado a
    // 22% sobre o branco quase puro do site -- verde-sálvia claramente
    // visível. A barra inferior usa esta MESMA cor (ver BottomNavBar.kt,
    // containerColor = colorScheme.background, não mais .surface).
    background = Color(0xFFCBD5CE),
    // "surface" (cor dos Cards -- AppCard.kt/CardDefaults.cardColors() usam
    // este token, não "background") virou uma cor DIFERENTE do fundo --
    // pedido do usuário ("os blocos não tem contorno e não se destacam por
    // a cor ser a mesma"): antes eu tinha igualado surface=background pra
    // bater com a barra inferior, só que isso também apagou a diferença
    // Card vs fundo da tela (sem borda E sem contraste de cor, o bloco
    // sumia visualmente). Agora surface é um tom bem mais claro/branco
    // (só 6% de verde) -- os Cards ficam claramente mais claros que o
    // fundo verde da página, sem precisar trazer a borda de volta.
    surface = Color(0xFFEFF1EE),
    surfaceVariant = Color(0xFFEFF1EE),
    outline = Color(0xFF4A5C57),
    outlineVariant = Color(0xFFC3D0CB),
)

private val DarkColors = darkColorScheme(
    // Verde mais suave/acinzentado no escuro -- pedido do usuário
    // ("reduza a saturação... prefira tons de verde mais pastel,
    // acinzentados... sem poluir a tela"), em vez do BrGreen puro (mais
    // saturado, "vibrava" em cima do fundo quase-preto).
    primary = BrGreenPrimaryDark,
    onPrimary = Color(0xFF0C2317),
    primaryContainer = Color(0xFF243A2F),
    onPrimaryContainer = Color(0xFFB7D9C7),
    secondary = BrYellow,
    secondaryContainer = Color(0xFF5E4F26),
    onSecondaryContainer = Color(0xFFF4DFA5),
    tertiary = BrBlueTertiaryDark,
    // Mesmo raciocínio do LightColors acima (ver comentário lá) -- 22% de
    // BrGreenPrimaryDark sobre o grafite quase-preto do site (0xFF16100B),
    // um verde musgo escuro nitidamente visível.
    background = Color(0xFF2A3227),
    // Cards mais CLAROS que o fundo (convenção Material de elevação no
    // escuro: quem "flutua" por cima fica mais claro, não mais escuro) --
    // mesmo motivo do LightColors acima, pra não ficarem invisíveis sem
    // borda e com a cor igual ao fundo.
    surface = Color(0xFF434B40),
    surfaceVariant = Color(0xFF434B40),
    outline = Color(0xFF7A9186),
    outlineVariant = Color(0xFF3F4A46),
)

// Variantes de verde por tema -- mantidas prontas caso precise de um tom
// diferente do BrGreen puro em algum lugar específico (ex.: botão +,
// ícones de destaque). A borda dos Cards usa BrGreen direto (ver
// AppCard.kt).
val BrGreenLight = Color(0xFF8FE0B5)
val BrGreenDark = Color(0xFF0C2317)

/** É "noite" (18h-6h) -- mesmo critério de isNightTime() em
 * theme-toggle.tsx, usado no modo Automático em conjunto com a preferência
 * de tema escuro do próprio Android. */
private fun isNightTimeNow(): Boolean {
    val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return h >= 18 || h < 6
}

/** Estado do tema compartilhado pelo app inteiro -- espelho do hook
 * useState + localStorage("sa-theme") de theme-toggle.tsx, só que
 * persistido via DataStore (ThemeStore) por não existir localStorage no
 * Android. Um objeto (não uma classe) porque só existe uma Activity/tela
 * de conteúdo no app, então não há necessidade de mais de uma instância. */
object ThemeController {
    private val _mode = MutableStateFlow(ThemeMode.AUTO)
    val mode: StateFlow<ThemeMode> = _mode.asStateFlow()

    private var loaded = false
    private val scope: CoroutineScope = MainScope()

    fun ensureLoaded(context: Context) {
        if (loaded) return
        loaded = true
        scope.launch(Dispatchers.IO) {
            _mode.value = ThemeStore(context.applicationContext).current()
        }
    }

    /** Avança Automático -> Claro -> Escuro -> Automático (mesmo ciclo de
     * CYCLE em theme-toggle.tsx) e persiste a escolha. */
    fun cycle(context: Context) {
        val next = when (_mode.value) {
            ThemeMode.AUTO -> ThemeMode.LIGHT
            ThemeMode.LIGHT -> ThemeMode.DARK
            ThemeMode.DARK -> ThemeMode.AUTO
        }
        _mode.value = next
        scope.launch(Dispatchers.IO) { ThemeStore(context.applicationContext).save(next) }
    }
}

@Composable
fun BRAgroTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { ThemeController.ensureLoaded(context) }
    val mode by ThemeController.mode.collectAsState()

    // No modo Automático, o site reavalia "é noite?" a cada 15 minutos
    // (setInterval) pra trocar de claro pra escuro sozinho ao anoitecer, sem
    // precisar reabrir o app -- réplica aqui com um contador que força
    // recomposição no mesmo intervalo, só enquanto o modo for AUTO.
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(mode) {
        if (mode != ThemeMode.AUTO) return@LaunchedEffect
        while (true) {
            delay(15 * 60 * 1000L)
            tick++
        }
    }

    // BUG investigado (usuário: "modo automático não está identificando se é
    // dia ou noite no mobile"): o `|| systemDark` abaixo replicava
    // computeAutoDark() do site (isNightTime() || prefersDarkDevice()) ao pé
    // da letra, mas no navegador `prefers-color-scheme` é um sinal de
    // ambiente (o SO raramente fica travado num dos dois valores pra
    // sempre). No Android é o oposto: a enorme maioria dos usuários deixa o
    // tema do sistema fixo em Claro OU Escuro nas Configurações (sem
    // "agendar por horário do dia") -- ou seja, `isSystemInDarkTheme()`
    // normalmente devolve SEMPRE o mesmo valor, dia e noite. Como o OR faz
    // `dark` virar `true` sempre que QUALQUER lado for `true`, num aparelho
    // com tema do sistema fixado em Escuro o app nunca saía do escuro (nem
    // às 10h da manhã), e num aparelho fixado em Claro o horário noturno só
    // vencia depois das 18h -- em ambos os casos dava a impressão de que o
    // "Automático" simplesmente não olhava pro relógio. Fix: no modo
    // Automático o critério de dia/noite passa a ser SÓ o horário do
    // aparelho (isNightTimeNow(), faixa 6h-18h = dia), sem depender do
    // toggle de tema do sistema -- quem quiser o tema sempre-escuro
    // independente da hora já tem a opção manual "Escuro" no próprio ciclo
    // do botão (Automático -> Claro -> Escuro).
    val dark = when (mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.AUTO -> {
            @Suppress("UNUSED_EXPRESSION") tick
            isNightTimeNow()
        }
    }
    val colors = if (dark) DarkColors else LightColors
    // Fonte Geist em todo o app -- pedido do usuario ("implemente a fonte
    // Geist no app"), ver ui/theme/Type.kt (AppTypography/GeistFontFamily).
    MaterialTheme(colorScheme = colors, typography = AppTypography, content = content)
}
