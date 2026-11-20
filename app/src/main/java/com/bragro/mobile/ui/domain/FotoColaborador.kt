package com.bragro.mobile.ui.domain

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.bragro.mobile.BuildConfig
import com.bragro.mobile.data.local.AppDatabase
import com.bragro.mobile.data.TokenStore
import com.bragro.mobile.data.remote.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * v271 (mockup v322 aprovado, igual ao site) -- foto do colaborador do RH.
 * Fica no armazenamento (bucket lookup-fotos) em `<orgId>/rh/<id>.jpg`, o
 * mesmo caminho usado pelo site, sem campo novo no banco. Sem foto, aparece
 * a silhueta. `editavel` mostra a câmera (escolher/tirar foto).
 */
private const val BUCKET_RH = "lookup-fotos"
private val versoesFoto = mutableMapOf<String, Long>()

@Composable
fun FotoColaborador(id: String, mini: Boolean = false, editavel: Boolean = false) {
    val context = LocalContext.current
    val escopo = rememberCoroutineScope()
    var orgId by remember { mutableStateOf<String?>(null) }
    var versao by remember(id) { mutableStateOf(versoesFoto[id] ?: 0L) }
    var enviando by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { orgId = AppDatabase.get(context).sessionDao().get()?.orgId }

    val seletor = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val org = orgId ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        enviando = true
        escopo.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    val original = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return@withContext false
                    val escala = minOf(1f, 1280f / maxOf(original.width, original.height))
                    val bmp = if (escala < 1f) Bitmap.createScaledBitmap(original, (original.width * escala).toInt(), (original.height * escala).toInt(), true) else original
                    val bytes = java.io.ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
                    val tokens = TokenStore(context).current() ?: return@withContext false
                    NetworkModule.supabaseStorageApi.upload(
                        bucket = BUCKET_RH,
                        path = "$org/rh/$id.jpg",
                        authorization = "Bearer ${tokens.first}",
                        apiKey = BuildConfig.SUPABASE_ANON_KEY,
                        contentType = "image/jpeg",
                        upsert = "true",
                        body = bytes.toRequestBody("image/jpeg".toMediaType()),
                    ).isSuccessful
                } catch (e: Exception) {
                    false
                }
            }
            enviando = false
            if (ok) {
                val v = System.currentTimeMillis()
                versoesFoto[id] = v
                versao = v
            } else {
                android.widget.Toast.makeText(context, "Falha ao enviar a foto.", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    val forma = if (mini) CircleShape else RoundedCornerShape(12.dp)
    val tamanho = if (mini) Modifier.size(36.dp) else Modifier.size(width = 112.dp, height = 140.dp)
    Box(modifier = tamanho) {
        Box(modifier = Modifier.fillMaxSize().clip(forma).background(MaterialTheme.colorScheme.surfaceVariant)) {
            val org = orgId
            val silhueta: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit = {
                Icon(
                    Icons.Outlined.Person, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.align(Alignment.Center).size(if (mini) 26.dp else 84.dp),
                )
            }
            if (org != null) {
                SubcomposeAsyncImage(
                    model = "${BuildConfig.SUPABASE_URL}/storage/v1/object/public/$BUCKET_RH/$org/rh/$id.jpg" + (if (versao > 0) "?v=$versao" else ""),
                    contentDescription = "Foto do colaborador",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    loading = { silhueta() },
                    error = { silhueta() },
                )
            } else silhueta()
        }
        if (editavel) {
            Surface(
                shape = CircleShape,
                shadowElevation = 3.dp,
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp).size(32.dp).clickable(enabled = !enviando) { seletor.launch("image/*") },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (enviando) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Outlined.CameraAlt, contentDescription = "Tirar ou escolher foto", modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

