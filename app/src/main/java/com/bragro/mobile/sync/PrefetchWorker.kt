package com.bragro.mobile.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.bragro.mobile.data.AppLog
import com.bragro.mobile.data.local.AppDatabase
import com.bragro.mobile.data.repo.RecordRepository
import kotlinx.coroutines.flow.first

/** Baixa pro cache local (Room) os registros dos modulos ANTES do usuario
 * abrir cada um -- pedido do usuario ("as vezes o app nao foi aberto antes
 * de perder a conexao"). Sem isso, o cache de um modulo so era preenchido
 * ao abrir aquele modulo com internet (DomainListScreen -> refreshFromServer),
 * entao um modulo nunca aberto aparecia vazio offline.
 *
 * Disparado: (1) logo apos o login, (2) na abertura do app com internet e
 * (3) depois de uma importacao de CSV (so do modulo importado, via
 * [enqueue] com domainId). Respeita NetworkType.CONNECTED -- so roda de fato
 * com internet. Falha de um modulo nao interrompe os demais. */
class PrefetchWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            // Sem sessao (nunca logou / fez logout) nao ha o que pre-carregar --
            // sucesso, senao ficaria em retry infinito antes do primeiro login.
            if (AppDatabase.get(applicationContext).sessionDao().get() == null) return Result.success()
            val repo = RecordRepository(applicationContext)
            val only = inputData.getString(KEY_DOMAIN)
            val domainIds = if (only != null) {
                listOf(only)
            } else {
                AppDatabase.get(applicationContext).domainConfigDao().observeAll().first().map { it.domainId }
            }
            var anyFailed = false
            for (id in domainIds) {
                if (!repo.refreshFromServer(id)) anyFailed = true
            }
            // Falha (sem token/servidor fora) -> tenta de novo com o backoff
            // padrao do WorkManager; nao fica em loop porque o proprio
            // WorkManager limita tentativas por backoff crescente.
            if (anyFailed) Result.retry() else Result.success()
        } catch (e: Exception) {
            AppLog.e("PrefetchWorker", "Falha ao pre-carregar registros dos módulos para uso offline", e)
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_ALL = "bragro-prefetch-all"
        private const val KEY_DOMAIN = "domainId"

        /** [domainId] = null pre-carrega todos os modulos; senao so aquele. */
        fun enqueue(context: Context, domainId: String? = null) {
            val builder = OneTimeWorkRequestBuilder<PrefetchWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            if (domainId != null) builder.setInputData(workDataOf(KEY_DOMAIN to domainId))
            val name = if (domainId == null) UNIQUE_ALL else "bragro-prefetch-$domainId"
            WorkManager.getInstance(context).enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE, builder.build())
        }
    }
}
