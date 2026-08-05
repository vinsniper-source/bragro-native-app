package com.bragro.mobile.ui.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
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
val BrGreen = Color(0xFF2F6F4F)
val BrYellow = Color(0xFFF2C037)
val BrBlue = Color(0xFF1E4B8A)
// Quarta cor de apoio (fora da bandeira) so pra diferenciar a 4a secao
// ("Pessoas") das outras 3, que ja usam verde/amarelo/azul.
val BrOrange = Color(0xFFD9822B)

private val LightColors = lightColorScheme(
    primary = BrGreen,
    secondary = BrYellow,
    tertiary = BrBlue,
)

private val DarkColors = darkColorScheme(
    primary = BrGreen,
    secondary = BrYellow,
    tertiary = BrBlue,
)

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
    val systemDark = isSystemInDarkTheme()

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

    val dark = when (mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.AUTO -> {
            @Suppress("UNUSED_EXPRESSION") tick
            isNightTimeNow() || systemDark
        }
    }
    val colors = if (dark) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}
