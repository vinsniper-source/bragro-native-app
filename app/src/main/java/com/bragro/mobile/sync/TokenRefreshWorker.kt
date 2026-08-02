package com.bragro.mobile.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.bragro.mobile.data.TokenStore
import com.bragro.mobile.data.repo.TokenRefresher
import java.util.concurrent.TimeUnit

/** Renovacao PROATIVA de sessao em segundo plano (Fase 2, Task #37 -- item
 * "Renovacao automatica de sessao em segundo plano" do README). Antes,
 * nada disparava a renovacao do access token sozinho: cada tela/fila so
 * tentava renovar REATIVAMENTE, depois de ja levar um 401 (ver
 * TokenRefresher). Este worker roda a cada ~45min (menor que a vida util
 * do access token, ~1h) e troca o access token pelo refresh_token antes
 * dele expirar de fato -- reduz a chance de qualquer tela precisar do
 * caminho reativo, sem substitui-lo (continua existindo como rede de
 * seguranca pro caso do aparelho ficar mais tempo sem rede que o
 * intervalo deste worker). Nao faz nada (retorna sucesso sem chamar a
 * rede) se ninguem estiver logado neste aparelho. */
class TokenRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val tokenStore = TokenStore(applicationContext)
        val tokens = tokenStore.current() ?: return Result.success()
        val newAccess = TokenRefresher.refreshAccessToken(tokenStore, tokens.second)
        return if (newAccess != null) Result.success() else Result.retry()
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "bragro-token-refresh"

        fun enqueuePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<TokenRefreshWorker>(45, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
