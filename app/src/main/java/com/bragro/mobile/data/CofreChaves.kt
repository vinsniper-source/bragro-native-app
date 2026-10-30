package com.bragro.mobile.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * v72b -- criptografa a sessão do app (access/refresh token) com uma chave
 * AES-256 guardada no Android Keystore: a chave nunca sai do aparelho e não
 * vai para backup, então o arquivo copiado de um celular não serve em outro.
 * Valores antigos (salvos sem criptografia) continuam sendo lidos e passam a
 * ser gravados criptografados no próximo save.
 */
object CofreChaves {
    private const val ALIAS = "bragro_sessao_v1"
    private const val PREFIXO = "e1:"

    private fun chave(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    fun cifrar(texto: String): String = try {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, chave())
        val dados = c.doFinal(texto.toByteArray(Charsets.UTF_8))
        PREFIXO + Base64.encodeToString(c.iv + dados, Base64.NO_WRAP)
    } catch (e: Exception) {
        AppLog.e("CofreChaves", "Falha ao criptografar; gravando sem criptografia", e)
        texto
    }

    fun decifrar(valor: String?): String? {
        if (valor == null || !valor.startsWith(PREFIXO)) return valor
        return try {
            val bytes = Base64.decode(valor.removePrefix(PREFIXO), Base64.NO_WRAP)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, chave(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            String(c.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
        } catch (e: Exception) {
            AppLog.e("CofreChaves", "Falha ao abrir a sessão salva", e)
            null
        }
    }
}
