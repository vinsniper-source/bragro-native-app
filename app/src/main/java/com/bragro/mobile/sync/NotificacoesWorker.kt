package com.bragro.mobile.sync

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.bragro.mobile.MainActivity
import com.bragro.mobile.R
import com.bragro.mobile.data.model.NotificationItemData
import com.bragro.mobile.data.repo.NotificationsRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.util.concurrent.TimeUnit

/**
 * v55 -- alertas no celular com o app FECHADO, sem Firebase. A cada ~15 min
 * (o mínimo do Android) consulta o mesmo sino do sistema
 * (/api/mobile/notifications, action "list") e mostra na barra do Android
 * as notificações novas e não lidas: revisão de frota, romaneio do dia,
 * estoque mínimo e o que mais o servidor gravar. Só avisa o que chegou
 * DEPOIS da última consulta (na primeira vez só marca o ponto de partida,
 * para não despejar as antigas). Sem login ou sem internet, não faz nada.
 */
class NotificacoesWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val resultado = NotificationsRepository(ctx).run("list") ?: return Result.success()
        val itens = runCatching {
            resultado.jsonObject["items"]?.jsonArray?.map { json.decodeFromJsonElement(NotificationItemData.serializer(), it) }
        }.getOrNull() ?: return Result.success()

        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val ultima = prefs.getString(CHAVE_ULTIMA, null)
        val maisRecente = itens.maxOfOrNull { it.criadaEm }
        if (ultima == null) {
            prefs.edit().putString(CHAVE_ULTIMA, maisRecente ?: agoraIso()).apply()
            return Result.success()
        }
        val novas = itens.filter { !it.lida && it.criadaEm > ultima }.sortedBy { it.criadaEm }
        if (maisRecente != null && maisRecente > ultima) prefs.edit().putString(CHAVE_ULTIMA, maisRecente).apply()
        if (novas.isEmpty() || !podeNotificar(ctx)) return Result.success()

        criarCanal(ctx)
        val abrir = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val gerenciador = NotificationManagerCompat.from(ctx)
        novas.takeLast(MAX_INDIVIDUAIS).forEach { n ->
            val notificacao = NotificationCompat.Builder(ctx, CANAL)
                .setSmallIcon(R.drawable.ic_stat_bragro)
                .setContentTitle(n.titulo)
                .setContentText(n.mensagem)
                .setStyle(NotificationCompat.BigTextStyle().bigText(n.mensagem))
                .setContentIntent(abrir)
                .setAutoCancel(true)
                .setGroup(GRUPO)
                .build()
            runCatching { gerenciador.notify(n.id.hashCode(), notificacao) }
        }
        if (novas.size > 1) {
            val resumo = NotificationCompat.Builder(ctx, CANAL)
                .setSmallIcon(R.drawable.ic_stat_bragro)
                .setContentTitle("BRAgro")
                .setContentText("${novas.size} novos alertas")
                .setContentIntent(abrir)
                .setAutoCancel(true)
                .setGroup(GRUPO)
                .setGroupSummary(true)
                .build()
            runCatching { gerenciador.notify(ID_RESUMO, resumo) }
        }
        return Result.success()
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "bragro-notificacoes"
        const val CANAL = "alertas"
        private const val GRUPO = "bragro-alertas"
        private const val PREFS = "bragro_notificacoes"
        private const val CHAVE_ULTIMA = "ultimaVista"
        private const val MAX_INDIVIDUAIS = 5
        private const val ID_RESUMO = 7700

        fun enqueuePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<NotificacoesWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun criarCanal(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            if (nm.getNotificationChannel(CANAL) != null) return
            nm.createNotificationChannel(
                NotificationChannel(CANAL, "Alertas da fazenda", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Revisão de frota, romaneio do dia, estoque mínimo e outros avisos do BRAgro"
                },
            )
        }

        private fun podeNotificar(context: Context): Boolean {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return false
            return NotificationManagerCompat.from(context).areNotificationsEnabled()
        }

        private fun agoraIso(): String =
            java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
                .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
                .format(java.util.Date())
    }
}
