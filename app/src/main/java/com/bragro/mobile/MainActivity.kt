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
import androidx.core.view.WindowInsetsCompat
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
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.statusBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
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
                Surface(modifier = Modifier.fillMaxSize()) {
                    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
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

    // O Android às vezes devolve a barra de status sozinho (ex.: usuário
    // trocou de app e voltou) -- reaplica o "hide" quando a janela recupera
    // o foco, senão a barra reaparece de vez sem precisar de gesto nenhum.
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            WindowInsetsControllerCompat(window, window.decorView).hide(WindowInsetsCompat.Type.statusBars())
        }
    }
}
