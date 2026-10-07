package com.bragro.mobile

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.bragro.mobile.sync.PrefetchWorker
import com.bragro.mobile.sync.SyncWorker
import com.bragro.mobile.sync.TokenRefreshWorker
import io.sentry.android.core.SentryAndroid

class BRAgroApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initSentry()
        // Tenta esvaziar a fila de sincronizacao pendente toda vez que o app
        // abre (o WorkManager so executa de fato quando ha rede, ver
        // SyncWorker) -- cobre o caso comum de campo: usuario lancou dados
        // offline ontem, hoje abre o app ja com internet.
        SyncWorker.enqueue(this)
        // Pre-carrega o cache offline de TODOS os modulos (so roda com
        // internet e sessao ativa) -- ver PrefetchWorker.
        PrefetchWorker.enqueue(this)
        // Renovacao periodica de sessao em segundo plano (Task #37) --
        // ExistingPeriodicWorkPolicy.KEEP dentro do worker faz este enqueue
        // ser barato de chamar toda vez que o app abre (nao recria o
        // agendamento se ja existir um rodando).
        TokenRefreshWorker.enqueuePeriodic(this)
        // Bug real reportado pelo usuario ("aparece a mensagem de sync
        // pendente mesmo com o app online"): antes so tentava sincronizar
        // na ABERTURA do app -- se a conexao caiu e voltou com o app ja
        // aberto (rede de fazenda instavel e o caso mais comum aqui), a
        // fila so era reprocessada no proximo fechar/abrir. Agora reagenda
        // o SyncWorker toda vez que a conectividade volta, com o app
        // aberto ou nao.
        registerNetworkCallback()
    }

    // Sentry: travamentos (crash nao tratado) e ANR do app vao pro mesmo
    // projeto Sentry do site -- aparecem no Painel BRAgro do /admin. So em
    // build de RELEASE (o debug do Android Studio nao polui o painel).
    // Excecoes TRATADAS (AppLog.e) entram so como "breadcrumb" (contexto do
    // que aconteceu antes do crash), nao como erro proprio -- senao cada
    // timeout de rede da fazenda viraria um erro aberto no painel.
    private fun initSentry() {
        runCatching {
            SentryAndroid.init(this) { options ->
                options.dsn = BuildConfig.SENTRY_DSN
                options.isEnabled = !BuildConfig.DEBUG
                options.environment = "android"
                options.release = "com.bragro.mobile@${BuildConfig.VERSION_NAME}+${BuildConfig.VERSION_CODE}"
                options.isAnrEnabled = true
                options.tracesSampleRate = 0.0
                options.isSendDefaultPii = false
            }
        }
    }

    private fun registerNetworkCallback() {
        val connectivityManager = getSystemService(ConnectivityManager::class.java) ?: return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                SyncWorker.enqueue(applicationContext)
                PrefetchWorker.enqueue(applicationContext)
            }
        })
    }
}
