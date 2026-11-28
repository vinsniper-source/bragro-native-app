package com.bragro.mobile

import androidx.compose.ui.graphics.toArgb
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.background
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowInsetsControllerCompat
import com.bragro.mobile.sync.SyncWorker
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.bragro.mobile.ui.nav.BRAgroNavHost
import com.bragro.mobile.ui.theme.BRAgroTheme

class MainActivity : ComponentActivity() {
    private val pedirNotificacao = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }
    // v72b -- Android 7/8 (sem BiometricPrompt): tela de senha do próprio sistema.
    private val senhaDoSistema = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == RESULT_OK) com.bragro.mobile.ui.seguranca.AppLock.travado.value = false
    }
    private fun pedirSenhaDoSistema() {
        val km = getSystemService(KEYGUARD_SERVICE) as android.app.KeyguardManager
        @Suppress("DEPRECATION")
        km.createConfirmDeviceCredentialIntent("Desbloquear o BRAgro", "Use a senha do celular")?.let { senhaDoSistema.launch(it) }
    }
    private suspend fun sessaoAtiva() = com.bragro.mobile.data.repo.AuthRepository(this).isLoggedIn()

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
        // Oculta de vez a barra de status (não só transparente) -- pedido
        // do usuário ("ocultar barra de status do celular, suba mais o
        // cabeçalho"): o cabeçalho de cada tela ganha o espaço todo que
        // antes era reservado pro relógio/ícones do sistema. BEHAVIOR_SHOW_
        // TRANSIENT_BY_SWIPE deixa o usuário arrastar uma vez a partir do
        // topo pra ver a hora/notificações rapidamente, sem precisar sair
        // do modo imersivo pra isso. A barra de navegação (botões de
        // voltar/início do Android) continua igual -- só a de status muda.
        // v426 -- solução alternativa para a faixa branca: em vez de esconder a barra de
        // status (o teclado a trazia de volta em alguns aparelhos e aparecia a faixa
        // branca em cima, mesmo com FLAG_FULLSCREEN e o "hide" repetido -- v293/v407),
        // a barra fica sempre visível e TRANSPARENTE, com o fundo do app por baixo
        // (ver statusBarsPadding no setContent). Como ela não some nem volta, não há
        // mais o que deixar faixa: relógio e ícones aparecem sobre a cor do app.
        // v55 -- Android 13+: pede uma vez a permissão de notificação, para
        // os alertas da fazenda aparecerem com o app fechado.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            pedirNotificacao.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        // v72b -- bloqueio por digital/rosto ao abrir o app.
        lifecycleScope.launch { com.bragro.mobile.ui.seguranca.AppLock.aoAbrir(this@MainActivity, sessaoAtiva()) }
        setContent {
            BRAgroTheme {
                // v287 (mockup v365 item 2) -- fundo da janela na cor do tema: quando a barra
                // do sistema aparece (lista suspensa, teclado), não fica mais a faixa branca em cima.
                val fundoJanela = androidx.compose.material3.MaterialTheme.colorScheme.background
                androidx.compose.runtime.SideEffect {
                    window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(fundoJanela.toArgb()))
                    window.decorView.setBackgroundColor(fundoJanela.toArgb())
                    // v293 (mockup v387) -- ao abrir o teclado o Android pode mostrar a barra de
                    // status: ela passa a ter a cor do tema (sem faixa branca) e é escondida de novo.
                    window.statusBarColor = fundoJanela.toArgb()
                    window.navigationBarColor = fundoJanela.toArgb()
                    val escuro = androidx.core.graphics.ColorUtils.calculateLuminance(fundoJanela.toArgb()) < 0.5
                    WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = !escuro
                    WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightNavigationBars = !escuro
                }
                Surface(modifier = Modifier.fillMaxSize(), color = fundoJanela) {
                    // v407 -- o conteúdo encolhe acima do teclado (em vez de a tela inteira subir e
                    // deixar a faixa em cima); o campo tocado rola para ficar à vista.
                    // v426 -- fundo do app atrás da barra de status (sempre visível) e o
                    // conteúdo começa logo abaixo dela; nenhuma área fica com outra cor.
                    androidx.compose.foundation.layout.Box(
                        Modifier.fillMaxSize()
                            .background(fundoJanela)
                            .statusBarsPadding()
                            .imePadding(),
                    ) {
                        BRAgroNavHost()
                        if (com.bragro.mobile.ui.seguranca.AppLock.travado.value) {
                            com.bragro.mobile.ui.seguranca.TelaBloqueio(pedirSenhaAntiga = { pedirSenhaDoSistema() })
                        }
                    }
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
        // v72b -- volta do segundo plano: bloqueio por tempo parado e aviso
        // de uso deste aparelho (desconectado remotamente = sai da conta).
        lifecycleScope.launch {
            val ativa = sessaoAtiva()
            com.bragro.mobile.ui.seguranca.AppLock.aoVoltar(this@MainActivity, ativa)
            if (ativa && com.bragro.mobile.data.repo.AparelhoRepository(this@MainActivity).avisarUso() == true) {
                com.bragro.mobile.data.repo.AuthRepository(this@MainActivity).logout()
                recreate()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        com.bragro.mobile.ui.seguranca.AppLock.aoSair(this)
    }

    // v426 -- sem onWindowFocusChanged: a barra de status não é mais escondida (ver onCreate).
}
