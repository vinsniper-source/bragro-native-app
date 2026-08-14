package com.bragro.mobile.ui.util

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.os.Environment
import com.bragro.mobile.data.AppLog

/**
 * Baixa o APK de atualização usando o DownloadManager NATIVO do Android, em
 * vez de abrir o link num Custom Tab (openInCustomTab, CustomTabs.kt) --
 * corrige um bug real relatado pelo usuário ("quando coloco para baixar ele
 * começa o download, vai até o final, mas não aparece a mensagem
 * concluído, parece que fica em looping infinito, aí tenho que cancelar e
 * tentar de novo"). A causa: dentro de um Custom Tab, o download acontece
 * inteiramente no processo do NAVEGADOR (Chrome) -- o app nunca é avisado
 * quando termina, então a tela ficava "pendurada" esperando um sinal que
 * nunca chegava (às vezes o Chrome mostrava a notificação dele próprio, às
 * vezes não, dependendo do estado do navegador -- daí o comportamento
 * inconsistente "às vezes dá certo na segunda vez, às vezes não").
 *
 * Com o DownloadManager: (1) o Android mostra uma notificação de sistema
 * nativa com progresso E uma notificação "Download concluído" garantida ao
 * final (VISIBILITY_VISIBLE_NOTIFY_COMPLETED); (2) o app recebe de volta o
 * aviso de conclusão via BroadcastReceiver (ACTION_DOWNLOAD_COMPLETE, ver
 * SettingsScreen.kt) e pode abrir o instalador automaticamente, sem
 * depender de nada no navegador.
 */
fun enqueueApkDownload(context: Context, apkUrl: String, versao: String): Long {
    val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    val fileName = "BRAgro-$versao.apk"
    val request = DownloadManager.Request(android.net.Uri.parse(apkUrl))
        .setTitle("BRAgro $versao")
        .setDescription("Baixando atualização do aplicativo...")
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        .setMimeType("application/vnd.android.package-archive")
        .setAllowedOverMetered(true)
        .setAllowedOverRoaming(true)
    return downloadManager.enqueue(request)
}

/**
 * Abre o instalador de pacotes do Android pro APK que acabou de baixar.
 * `getUriForDownloadedFile` devolve um content:// URI gerenciado pelo
 * próprio content provider de Downloads do sistema -- já compatível com
 * FLAG_GRANT_READ_URI_PERMISSION sem precisar de um FileProvider próprio
 * (esse é o caminho documentado pela própria API do DownloadManager).
 */
fun openApkInstaller(context: Context, downloadManager: DownloadManager, downloadId: Long) {
    try {
        val localUri = downloadManager.getUriForDownloadedFile(downloadId) ?: run {
            AppLog.e("ApkInstaller", "getUriForDownloadedFile retornou null pra downloadId=$downloadId")
            return
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(localUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        AppLog.e("ApkInstaller", "Falha ao abrir o instalador pro downloadId=$downloadId", e)
    }
}
