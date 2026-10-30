package com.bragro.mobile.ui.domain

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bragro.mobile.data.AppLog
import com.bragro.mobile.data.TokenStore
import com.bragro.mobile.data.remote.NetworkModule
import com.bragro.mobile.data.repo.TokenRefresher
import com.bragro.mobile.ui.i18n.Text
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.serialization.Serializable
import java.text.NumberFormat
import java.util.Locale

/*
 * v56 -- Pix da cobrança no app (mockup v56, item 4): mesma janela da
 * plataforma -- QR, valor, vencimento e o Pix copia e cola, com Copiar e
 * Compartilhar (manda o código pelo WhatsApp/e-mail). O código vem pronto do
 * servidor (/api/mobile/pix), gerado pela mesma regra da plataforma.
 */

@Serializable
data class PixRequest(val accessToken: String, val refreshToken: String, val cobrancaId: String)

@Serializable
data class PixDados(
    val ok: Boolean,
    val codigo: String? = null,
    val valor: Double? = null,
    val vencimento: String? = null,
    val descricao: String? = null,
    val cliente: String? = null,
    val recebedor: String? = null,
    val txid: String? = null,
    val motivo: String? = null,
)

@Serializable
data class PixResponse(val ok: Boolean, val pix: PixDados? = null, val error: String? = null)

class PixRepository(context: Context) {
    private val tokenStore = TokenStore(context)

    suspend fun buscar(cobrancaId: String): PixDados? {
        val tokens = tokenStore.current() ?: return null
        var (accessToken, refreshToken) = tokens
        return try {
            var response = NetworkModule.mobileApi.pix(PixRequest(accessToken, refreshToken, cobrancaId))
            if (response.code() == 401) {
                val novo = TokenRefresher.refreshAccessToken(tokenStore, refreshToken)
                if (novo != null) {
                    accessToken = novo
                    response = NetworkModule.mobileApi.pix(PixRequest(accessToken, refreshToken, cobrancaId))
                }
            }
            val body = response.body()
            if (!response.isSuccessful || body?.ok != true) null else body.pix
        } catch (e: Exception) {
            AppLog.e("PixRepository", "Falha ao gerar o Pix da cobrança", e)
            null
        }
    }
}

private fun qrBitmap(payload: String, tamanho: Int = 512): Bitmap {
    val matriz = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, tamanho, tamanho, mapOf(EncodeHintType.MARGIN to 1))
    val bmp = Bitmap.createBitmap(tamanho, tamanho, Bitmap.Config.RGB_565)
    for (x in 0 until tamanho) for (y in 0 until tamanho) {
        bmp.setPixel(x, y, if (matriz[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
    }
    return bmp
}

private fun brl(v: Double) = NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(v)
private fun dataBr(iso: String?) = iso?.split("-")?.reversed()?.joinToString("/")

private val MOTIVOS = mapOf(
    "SEM_CHAVE" to "Cadastre a chave Pix na plataforma (Cobranças e NFS-e → Ferramentas → Pix) para gerar o código das cobranças.",
    "NAO_ENCONTRADA" to "Cobrança não encontrada. Se acabou de lançar sem internet, sincronize e tente de novo.",
    "CHAVE_INVALIDA" to "A chave Pix cadastrada está inválida. Confira na plataforma.",
)

@Composable
fun PixCobrancaDialog(cobrancaId: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    com.bragro.mobile.ui.seguranca.ProtegerDePrints()
    var carregando by remember(cobrancaId) { mutableStateOf(true) }
    var pix by remember(cobrancaId) { mutableStateOf<PixDados?>(null) }
    var qr by remember(cobrancaId) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(cobrancaId) {
        val r = PixRepository(context).buscar(cobrancaId)
        pix = r
        if (r?.ok == true && r.codigo != null) qr = runCatching { qrBitmap(r.codigo) }.getOrNull()
        carregando = false
    }
    val p = pix
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (p?.ok == true && p.txid != null) "Pix da cobrança ${p.txid}" else "Pix da cobrança") },
        text = {
            when {
                carregando -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                p == null -> Text("Não foi possível gerar o Pix agora. Confira a internet e tente de novo.")
                !p.ok -> Text(MOTIVOS[p.motivo] ?: "Não foi possível gerar o Pix.")
                else -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    qr?.let {
                        Box(Modifier.clip(RoundedCornerShape(10.dp)).background(Color.White).padding(6.dp)) {
                            Image(bitmap = it.asImageBitmap(), contentDescription = "QR Code Pix", modifier = Modifier.size(200.dp))
                        }
                    }
                    Text(brl(p.valor ?: 0.0), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        listOfNotNull(p.recebedor, dataBr(p.vencimento)?.let { "vence $it" }).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SelectionContainer {
                        androidx.compose.material3.Text(p.codigo.orEmpty(), fontFamily = FontFamily.Monospace, fontSize = 10.sp, modifier = Modifier.fillMaxWidth())
                    }
                    Text(
                        "O sistema não vê o pagamento. Quando cair, marque como PAGO.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            if (p?.ok == true && p.codigo != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        val cm = context.getSystemService(ClipboardManager::class.java)
                        cm?.setPrimaryClip(ClipData.newPlainText("Pix", p.codigo))
                        Toast.makeText(context, "Código Pix copiado", Toast.LENGTH_SHORT).show()
                    }) { Text("Copiar código") }
                    OutlinedButton(onClick = {
                        val texto = "Pix ${brl(p.valor ?: 0.0)} — ${p.descricao.orEmpty()}" +
                            (dataBr(p.vencimento)?.let { " (vence $it)" } ?: "") +
                            "\n\nPix copia e cola:\n${p.codigo}"
                        val envio = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, texto) }
                        context.startActivity(Intent.createChooser(envio, "Compartilhar Pix"))
                    }) { Text("Compartilhar") }
                }
            } else {
                TextButton(onClick = onDismiss) { Text("Fechar") }
            }
        },
        dismissButton = if (p?.ok == true) ({ TextButton(onClick = onDismiss) { Text("Fechar") } }) else null,
    )
}
