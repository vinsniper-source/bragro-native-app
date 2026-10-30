package com.bragro.mobile.ui.domain

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.bragro.mobile.data.AppLog
import com.bragro.mobile.data.repo.OrcamentoUploadRepository
import com.bragro.mobile.ui.i18n.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Botão reutilizável "Preencher com foto" (paridade com o site, Task
 * #889-895): abre a câmera (mesmo fluxo de permissão/forçar abertura de
 * PragaFotoScreen), comprime a foto (maior lado 1600px, JPEG 75), sobe pro
 * Storage e entrega a URL pública pra [onFotoUrl], que chama a leitura por IA
 * no servidor (OcrFotoRepository) e devolve uma mensagem de status (ou null).
 * Nada é gravado sozinho -- quem usa o botão só pré-preenche campos pro
 * usuário conferir.
 */
@Composable
fun OcrFotoButton(
    modifier: Modifier = Modifier,
    label: String = "Preencher com foto",
    onFotoUrl: suspend (String) -> String?,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var mensagem by remember { mutableStateOf<String?>(null) }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var permanentementeNegada by remember { mutableStateOf(false) }

    fun processar(uri: Uri) {
        busy = true
        mensagem = null
        scope.launch {
            val bytes = withContext(Dispatchers.IO) { comprimirFotoOcr(context, uri) }
            if (bytes == null) {
                busy = false
                mensagem = "Não foi possível processar a foto. Tente novamente ou preencha manualmente."
                return@launch
            }
            val url = OrcamentoUploadRepository(context).uploadFoto(bytes, "ocr")
            if (url == null) {
                busy = false
                mensagem = "Sem conexão. Não foi possível enviar a foto agora. Preencha manualmente."
                return@launch
            }
            mensagem = try {
                onFotoUrl(url)
            } catch (e: Exception) {
                AppLog.e("OcrFotoButton", "Falha ao ler foto por IA", e)
                "Falha na leitura automática. Preencha manualmente."
            }
            busy = false
        }
    }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = pendingUri
        if (success && uri != null) processar(uri)
        else if (!success) mensagem = "Nenhuma foto capturada."
    }

    fun launchCamera() {
        if (!temAppDeCameraDisponivel(context)) {
            avisarSemAppDeCamera(context)
            mensagem = "Não foi possível abrir a câmera neste aparelho. Preencha manualmente."
            return
        }
        try {
            val file = File(File(context.cacheDir, "ocr").apply { mkdirs() }, "ocr_${System.currentTimeMillis()}.jpg")
            file.createNewFile()
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            pendingUri = uri
            takePicture.launch(uri)
        } catch (e: Exception) {
            AppLog.e("OcrFotoButton", "Falha ao abrir a câmera", e)
            mensagem = "Não foi possível abrir a câmera neste aparelho. Preencha manualmente."
        }
    }

    LaunchedEffect(Unit) { permanentementeNegada = isCameraPermanentementeNegada(context) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concedida ->
        if (concedida) {
            permanentementeNegada = false
            launchCamera()
        } else {
            val activity = context as? android.app.Activity
            permanentementeNegada = activity != null &&
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
            mensagem = "Permissão da câmera negada. Preencha manualmente."
        }
    }

    fun abrir() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            permanentementeNegada = false
            launchCamera()
        } else if (permanentementeNegada) {
            openAppSettings(context)
        } else {
            marcarCameraPermissaoJaPedida(context)
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { abrir() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp).padding(end = 0.dp), strokeWidth = 2.dp)
                Text("  Lendo foto...")
            } else {
                Icon(Icons.Outlined.CameraAlt, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text(label)
            }
        }
        if (mensagem != null) {
            Text(mensagem ?: "", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
        }
        if (permanentementeNegada) {
            Text(
                "Permissão da câmera bloqueada pelo sistema. Toque abaixo para liberar.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
            TextButton(onClick = { openAppSettings(context) }) { Text("Abrir Configurações do app") }
        }
    }
}

private fun comprimirFotoOcr(context: Context, uri: Uri): ByteArray? {
    return try {
        val original = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return null
        val rotated = aplicarRotacaoExif(context, uri, original)
        val escala = minOf(1f, 1600f / maxOf(rotated.width, rotated.height))
        val w = (rotated.width * escala).toInt().coerceAtLeast(1)
        val h = (rotated.height * escala).toInt().coerceAtLeast(1)
        val redim = Bitmap.createScaledBitmap(rotated, w, h, true)
        val out = ByteArrayOutputStream()
        redim.compress(Bitmap.CompressFormat.JPEG, 75, out)
        out.toByteArray()
    } catch (e: Exception) {
        AppLog.e("OcrFotoButton", "Falha ao comprimir foto", e)
        null
    }
}

private fun aplicarRotacaoExif(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
    return try {
        val exif = context.contentResolver.openInputStream(uri)?.use { ExifInterface(it) } ?: return bitmap
        val graus = when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (graus == 0f) return bitmap
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(graus) }, true)
    } catch (e: Exception) {
        bitmap
    }
}
