package com.bragro.mobile.ui.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File

/** Grava um texto (JSON do backup, CSV de exportação) num arquivo temporário
 * da pasta de cache do app e abre o menu "Compartilhar" do Android -- mesmo
 * mecanismo já usado pela foto de romaneio (FileProvider + fileprovider do
 * AndroidManifest), só que pra texto em vez de imagem. Usado pelo botão
 * "Backup" do cabeçalho do Início e pelo botão CSV das listas de módulo.
 *
 * Isso SÓ abre o menu "Compartilhar" -- se o usuário fechar o menu sem
 * escolher um app (ex.: WhatsApp, Drive, e-mail), o arquivo fica só na
 * pasta de cache do app (invisível no Files/Meus Arquivos do aparelho) e
 * nada é de fato salvo no armazenamento -- pedido do usuário ("o ícone
 * nuvem... não está registrando armazenamento"). Por isso [saveToDownloads]
 * abaixo GRAVA de verdade na pasta Downloads pública antes de abrir o menu,
 * pra garantir que o backup sempre fique salvo no aparelho independente do
 * que o usuário fizer no menu de compartilhar. */
fun shareTextFile(context: Context, fileName: String, mimeType: String, content: String) {
    val dir = File(context.cacheDir, "exports").apply { mkdirs() }
    val file = File(dir, fileName)
    file.writeText(content)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(intent, "Compartilhar $fileName")
    // O botão "Backup" do cabeçalho (ver HomeScreen.kt) dispara isso a partir
    // do ViewModel (Application, não Activity) -- sem essa flag o Android
    // lança "startActivity() from outside of an Activity context requires
    // FLAG_ACTIVITY_NEW_TASK" e derruba o app. Inofensivo quando chamado a
    // partir de uma Activity também, então fica sempre ligada aqui.
    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(chooser)
}

/** Grava de fato o arquivo na pasta Downloads pública do aparelho (visível
 * no app Arquivos/Files, sobrevive mesmo que o usuário feche o menu de
 * compartilhar sem escolher nada) -- Android 10+ usa MediaStore (não
 * precisa de permissão), versões mais antigas gravam direto na pasta
 * pública (precisa a permissão de armazenamento, já concedida em apps
 * legados). Retorna true se conseguiu gravar. */
fun saveToDownloads(context: Context, fileName: String, mimeType: String, content: String): Boolean {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return false
            context.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
        } else {
            @Suppress("DEPRECATION")
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            dir.mkdirs()
            File(dir, fileName).writeText(content)
        }
        true
    } catch (e: Exception) {
        false
    }
}
