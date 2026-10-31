@file:Suppress("DEPRECATION")

package com.bragro.mobile.ui.domain

import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.TextureView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FlashlightOff
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bragro.mobile.data.AppLog
import com.google.zxing.PlanarYUVLuminanceSource
import kotlinx.coroutines.delay

/**
 * v78 -- leitor de QR AO VIVO próprio do BRAgro (mockup v78 aprovado), no
 * lugar da tela pronta do Google Play Services (GmsBarcodeScanning).
 *
 * Câmera pela API do próprio Android (android.hardware.Camera -- existe em
 * todo aparelho, sem biblioteca nova no APK) e leitura com o ZXing
 * (LeitorQr). Funciona sem Google Play e sem internet. A permissão CAMERA
 * é pedida por quem abre esta tela (QuickAbastecimentoDialog).
 *
 * [identificar] confere o texto lido contra as frotas cadastradas e devolve
 * o nome da frota, ou null se o QR não for de nenhuma -- aí avisa em laranja
 * e continua lendo.
 */
@Composable
fun LeitorQrCameraDialog(
    identificar: (String) -> String?,
    onConfirmado: (String) -> Unit,
    onFechar: () -> Unit,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var lanterna by remember { mutableStateOf(false) }
    var temLanterna by remember { mutableStateOf(false) }
    var lido by remember { mutableStateOf<String?>(null) }
    var aviso by remember { mutableStateOf<String?>(null) }
    var erroCamera by remember { mutableStateOf(false) }
    val identificarAtual by rememberUpdatedState(identificar)
    val camera = remember { CameraQr(context) }

    DisposableEffect(Unit) {
        var ultimoInvalido: String? = null
        camera.onTexto = { texto ->
            if (lido == null && texto != ultimoInvalido) {
                val nome = identificarAtual(texto)
                if (nome != null) {
                    lido = nome
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                } else {
                    ultimoInvalido = texto
                    aviso = "QR Code lido, mas não é de uma frota cadastrada."
                }
            }
        }
        camera.onAberta = { temLanterna = it }
        camera.onErro = { erroCamera = true }
        onDispose { camera.fechar() }
    }
    LaunchedEffect(lanterna) { camera.lanterna(lanterna) }
    LaunchedEffect(lido) {
        val nome = lido ?: return@LaunchedEffect
        camera.pausar()
        delay(900)
        onConfirmado(nome)
    }
    LaunchedEffect(aviso) {
        if (aviso != null) {
            delay(2500)
            aviso = null
        }
    }

    Dialog(
        onDismissRequest = onFechar,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(
                factory = { ctx -> camera.criarVisao(ctx) },
                modifier = Modifier.fillMaxSize(),
            )
            MolduraQr(lendo = lido == null)

            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                IconButton(
                    onClick = onFechar,
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.14f)),
                ) { Icon(Icons.Outlined.Close, contentDescription = "Fechar", tint = Color.White) }
                Text("Ler QR Code da frota", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }

            if (lido == null) {
                Column(
                    Modifier.align(Alignment.Center).offset(y = 150.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        if (erroCamera) "Não foi possível abrir a câmera" else "Aponte para o QR Code",
                        color = Color.White, fontSize = 15.sp,
                    )
                    Text(
                        if (erroCamera) "Feche e use o botão de foto." else "A leitura é automática",
                        color = Color.White.copy(alpha = 0.7f), fontSize = 12.5.sp,
                    )
                    aviso?.let {
                        Spacer(Modifier.size(10.dp))
                        Surface(shape = RoundedCornerShape(999.dp), color = corAtencao()) {
                            Text(it, color = Color.Black, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp))
                        }
                    }
                }
                if (temLanterna) {
                    TextButton(
                        onClick = { lanterna = !lanterna },
                        modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 28.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (lanterna) Color.White else Color.White.copy(alpha = 0.14f)),
                    ) {
                        val cor = if (lanterna) Color.Black else Color.White
                        Icon(if (lanterna) Icons.Outlined.FlashlightOff else Icons.Outlined.FlashlightOn, contentDescription = null, tint = cor)
                        Spacer(Modifier.width(8.dp))
                        Text("Lanterna", color = cor, fontSize = 14.sp)
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color.White,
                    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 28.dp).fillMaxWidth(),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Outlined.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                        Column {
                            Text(lido ?: "", color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text("Frota lida — voltando ao abastecimento…", color = Color(0xFF555555), fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

/** Escurece em volta, cantos verdes e a linha que passa enquanto lê. */
@Composable
private fun MolduraQr(lendo: Boolean) {
    val verde = if (lendo) Color(0xFF3FA66E) else MaterialTheme.colorScheme.primary
    val anim = rememberInfiniteTransition(label = "linhaQr")
    val pos by anim.animateFloat(0.08f, 0.92f, infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Reverse), label = "pos")
    Canvas(Modifier.fillMaxSize().graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) {
        val lado = size.minDimension * 0.66f
        val topo = Offset((size.width - lado) / 2f, size.height * 0.44f - lado / 2f)
        drawRect(Color.Black.copy(alpha = 0.55f))
        drawRoundRect(Color.Transparent, topo, Size(lado, lado), CornerRadius(14.dp.toPx()), blendMode = BlendMode.Clear)
        val c = 38.dp.toPx()
        val w = 4.dp.toPx()
        val r = 14.dp.toPx()
        val cantos = listOf(
            topo to (1f to 1f),
            Offset(topo.x + lado, topo.y) to (-1f to 1f),
            Offset(topo.x, topo.y + lado) to (1f to -1f),
            Offset(topo.x + lado, topo.y + lado) to (-1f to -1f),
        )
        for ((p, d) in cantos) {
            val (dx, dy) = d
            drawLine(verde, Offset(p.x + dx * r, p.y), Offset(p.x + dx * c, p.y), w, StrokeCap.Round)
            drawLine(verde, Offset(p.x, p.y + dy * r), Offset(p.x, p.y + dy * c), w, StrokeCap.Round)
            val arcoTopo = Offset(if (dx > 0) p.x else p.x - 2 * r, if (dy > 0) p.y else p.y - 2 * r)
            val inicio = when {
                dx > 0 && dy > 0 -> 180f
                dx < 0 && dy > 0 -> 270f
                dx > 0 && dy < 0 -> 90f
                else -> 0f
            }
            drawArc(verde, inicio, 90f, false, arcoTopo, Size(2 * r, 2 * r), style = Stroke(w))
        }
        if (lendo) {
            val y = topo.y + lado * pos
            drawLine(verde, Offset(topo.x + 14.dp.toPx(), y), Offset(topo.x + lado - 14.dp.toPx(), y), 2.dp.toPx())
        }
    }
}

/**
 * Câmera traseira + leitura de cada quadro numa thread própria. Lê no
 * máximo um quadro por vez (os outros são descartados) para não travar
 * celular mais simples.
 */
private class CameraQr(private val appContext: Context) : TextureView.SurfaceTextureListener {
    var onTexto: (String) -> Unit = {}
    var onAberta: (Boolean) -> Unit = {}
    var onErro: () -> Unit = {}

    private val principal = Handler(Looper.getMainLooper())
    private val thread = HandlerThread("leitor-qr").apply { start() }
    private val fundo = Handler(thread.looper)
    private var camera: Camera? = null
    private var largura = 0
    private var altura = 0
    @Volatile private var ocupado = false
    @Volatile private var pausado = false
    private var textura: TextureView? = null

    fun criarVisao(ctx: Context): FrameLayout {
        val tv = TextureView(ctx).also { it.surfaceTextureListener = this; textura = it }
        return object : FrameLayout(ctx) {
            // Preenche a tela inteira sem esticar a imagem (corta as sobras).
            override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
                val w = r - l
                val h = b - t
                // Em pé, a imagem da câmera chega deitada: troca largura/altura.
                val (pw, ph) = if (largura > 0) altura to largura else w to h
                val escala = maxOf(w.toFloat() / pw, h.toFloat() / ph)
                val cw = (pw * escala).toInt()
                val ch = (ph * escala).toInt()
                val x = (w - cw) / 2
                val y = (h - ch) / 2
                getChildAt(0)?.layout(x, y, x + cw, y + ch)
            }
        }.apply {
            addView(tv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        fundo.post { abrir(surface) }
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        fundo.post { soltar() }
        return true
    }
    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}

    private fun abrir(surface: SurfaceTexture) {
        try {
            val info = Camera.CameraInfo()
            val id = (0 until Camera.getNumberOfCameras()).firstOrNull {
                Camera.getCameraInfo(it, info); info.facing == Camera.CameraInfo.CAMERA_FACING_BACK
            } ?: 0
            val cam = Camera.open(id)
            val p = cam.parameters
            val tamanho = p.supportedPreviewSizes
                .filter { it.width <= 1920 }
                .minByOrNull { kotlin.math.abs(it.width * it.height - 1280 * 720) }
            if (tamanho != null) p.setPreviewSize(tamanho.width, tamanho.height)
            val focos = p.supportedFocusModes ?: emptyList()
            when {
                Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE in focos -> p.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE
                Camera.Parameters.FOCUS_MODE_AUTO in focos -> p.focusMode = Camera.Parameters.FOCUS_MODE_AUTO
            }
            cam.parameters = p
            Camera.getCameraInfo(id, info)
            cam.setDisplayOrientation(info.orientation % 360)
            cam.setPreviewTexture(surface)
            largura = cam.parameters.previewSize.width
            altura = cam.parameters.previewSize.height
            cam.setPreviewCallback { dados, _ -> lerQuadro(dados) }
            cam.startPreview()
            if (p.focusMode == Camera.Parameters.FOCUS_MODE_AUTO) {
                try { cam.autoFocus { _, _ -> } } catch (_: Exception) {}
            }
            camera = cam
            val flash = p.supportedFlashModes?.contains(Camera.Parameters.FLASH_MODE_TORCH) == true
            principal.post {
                textura?.parent?.requestLayout()
                onAberta(flash)
            }
        } catch (e: Exception) {
            AppLog.e("LeitorQrCamera", "Falha ao abrir a câmera do leitor de QR", e)
            principal.post { onErro() }
        }
    }

    private fun lerQuadro(dados: ByteArray?) {
        if (dados == null || ocupado || pausado || largura == 0) return
        ocupado = true
        val w = largura
        val h = altura
        fundo.post {
            try {
                val fonte = PlanarYUVLuminanceSource(dados, w, h, 0, 0, w, h, false)
                val texto = LeitorQr.ler(fonte)
                if (texto != null) principal.post { onTexto(texto) }
            } catch (_: Exception) {
            } finally {
                ocupado = false
            }
        }
    }

    fun lanterna(ligar: Boolean) {
        fundo.post {
            val cam = camera ?: return@post
            try {
                val p = cam.parameters
                p.flashMode = if (ligar) Camera.Parameters.FLASH_MODE_TORCH else Camera.Parameters.FLASH_MODE_OFF
                cam.parameters = p
            } catch (_: Exception) {}
        }
    }

    fun pausar() { pausado = true }

    private fun soltar() {
        val cam = camera ?: return
        camera = null
        try {
            cam.setPreviewCallback(null)
            cam.stopPreview()
        } catch (_: Exception) {}
        cam.release()
    }

    fun fechar() {
        fundo.post {
            soltar()
            thread.quitSafely()
        }
    }
}
