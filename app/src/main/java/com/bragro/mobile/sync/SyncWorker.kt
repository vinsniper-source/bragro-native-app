package com.bragro.mobile.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.bragro.mobile.data.repo.RecordRepository
import java.util.concurrent.TimeUnit

/** Processa a fila de sincronizacao offline (outbox) assim que houver
 * conexao -- disparado automaticamente pelo WorkManager (respeita a
 * restricao NetworkType.CONNECTED abaixo, entao so roda de fato quando ha
 * internet), pela BRAgroApplication quando a conectividade volta com o app
 * ja aberto, e tambem manualmente (botao "Sincronizar agora" nas telas). */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            val repo = RecordRepository(applicationContext)
            repo.syncAll()
            // Bug real reportado pelo usuario ("aparece a mensagem de sync
            // pendente mesmo com o app online"): antes retornava sucesso
            // sempre, mesmo com item pendente sobrando (ex.: sessao
            // expirada, erro de validacao no servidor) -- o WorkManager
            // nunca era avisado que precisava tentar de novo. Agora, se
            // ainda sobrou algo na fila apos essa tentativa, pede retry
            // (com o backoff configurado no enqueue abaixo) em vez de
            // deixar o item parado ate o proximo abrir do app.
            if (repo.hasPending()) Result.retry() else Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "bragro-sync"

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                // Backoff exponencial (30s, 1min, 2min...) em vez do
                // padrao do WorkManager -- pedido implícito ao corrigir o
                // retry acima: sem isso, tentativas repetidas de um erro
                // que não vai se resolver sozinho (ex.: sessão expirada)
                // bateriam a rede sem parar.
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
