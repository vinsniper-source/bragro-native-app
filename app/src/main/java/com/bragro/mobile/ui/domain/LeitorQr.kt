package com.bragro.mobile.ui.domain

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader

/**
 * v78 -- leitura de QR Code PRÓPRIA do app, com o ZXing (biblioteca aberta,
 * Apache 2.0, que o app já usava para GERAR o QR de cada frota). Antes a
 * leitura dependia do ML Kit do Google Play Services, que baixa o leitor à
 * parte e não existe em celular sem Google Play. Roda 100% no aparelho,
 * sem internet e sem aumentar o APK.
 */
object LeitorQr {
    private val dicas = mapOf(
        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
        DecodeHintType.TRY_HARDER to true,
        DecodeHintType.CHARACTER_SET to "UTF-8",
    )

    /** Lê o QR de uma foto. Reduz fotos grandes (a câmera tira 12+ MP) para
     * o leitor ser rápido e tenta dois métodos de contraste. */
    fun lerDaFoto(original: Bitmap): String? {
        val maxLado = 1600
        val escala = minOf(1f, maxLado.toFloat() / maxOf(original.width, original.height))
        val bmp = if (escala < 1f) {
            Bitmap.createScaledBitmap(original, (original.width * escala).toInt().coerceAtLeast(1), (original.height * escala).toInt().coerceAtLeast(1), true)
        } else original
        val pixels = IntArray(bmp.width * bmp.height)
        bmp.getPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        return ler(RGBLuminanceSource(bmp.width, bmp.height, pixels))
    }

    /** Lê o QR de uma imagem já em tons de cinza (ex.: quadro da câmera ao vivo). */
    fun ler(fonte: LuminanceSource): String? {
        for (bin in listOf(HybridBinarizer(fonte), GlobalHistogramBinarizer(fonte))) {
            try {
                val texto = QRCodeReader().decode(BinaryBitmap(bin), dicas).text
                if (!texto.isNullOrBlank()) return texto
            } catch (_: NotFoundException) {
            } catch (_: Exception) {
            }
        }
        return null
    }
}
