package com.bragro.mobile

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.bragro.mobile.sync.SyncWorker
import com.bragro.mobile.ui.nav.BRAgroNavHost
import com.bragro.mobile.ui.theme.BRAgroTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Precisa ser chamado ANTES de super.onCreate() (Task #38, contrato
        // da API androidx.core.splashscreen) -- troca pro tema normal
        // (Theme.BRAgro, ver themes.xml "postSplashScreenTheme") assim que
        // a primeira tela desenhar.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // SystemBarStyle.auto (o padrão de enableEdgeToEdge() sem argumentos)
        // desenha um "scrim" semi-transparente atrás da barra de status/
        // navegação no modo claro -- suficiente pra legibilidade, mas cria
        // uma faixa com tom levemente diferente do resto do app, dando
        // impressão de que o conteúdo não ocupa a tela inteira (pedido do
        // usuário: "app tem que ocupar toda tela até acima da barra de
        // status"). Transparent nos dois modos remove esse scrim -- o app
        // desenha por baixo da barra de status/navegação sem nenhuma faixa
        // visível, cor nenhuma "sobrando" no topo/rodapé.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        setContent {
            BRAgroTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    BRAgroNavHost()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Tenta esvaziar a fila de sincronizacao toda vez que o app volta pro
        // primeiro plano -- cobre o caso comum de campo: usuario lancou
        // dados sem sinal, saiu do app, e quando volta ja esta com internet.
        SyncWorker.enqueue(this)
    }
}
