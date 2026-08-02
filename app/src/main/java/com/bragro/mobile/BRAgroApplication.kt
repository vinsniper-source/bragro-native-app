package com.bragro.mobile

import android.app.Application
import com.bragro.mobile.sync.SyncWorker
import com.bragro.mobile.sync.TokenRefreshWorker

class BRAgroApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Tenta esvaziar a fila de sincronizacao pendente toda vez que o app
        // abre (o WorkManager so executa de fato quando ha rede, ver
        // SyncWorker) -- cobre o caso comum de campo: usuario lancou dados
        // offline ontem, hoje abre o app ja com internet.
        SyncWorker.enqueue(this)
        // Renovacao periodica de sessao em segundo plano (Task #37) --
        // ExistingPeriodicWorkPolicy.KEEP dentro do worker faz este enqueue
        // ser barato de chamar toda vez que o app abre (nao recria o
        // agendamento se ja existir um rodando).
        TokenRefreshWorker.enqueuePeriodic(this)
    }
}
