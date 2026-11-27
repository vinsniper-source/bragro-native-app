package com.bragro.mobile.data.repo

import android.content.Context
import com.bragro.mobile.data.AppLog
import com.bragro.mobile.data.TokenStore
import com.bragro.mobile.data.remote.NetworkModule
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** v423 (mockup aprovado) -- layout do Início do app salvo na CONTA (antes só no
 * celular): instalar em outro celular ou sair e entrar traz o mesmo layout.
 * As preferências "bragro_inicio" viram um mapa chave -> "tipo:valor". */
class LayoutContaRepository(private val context: Context) {
    private val tokenStore = TokenStore(context)
    private val prefs get() = context.getSharedPreferences("bragro_inicio", Context.MODE_PRIVATE)

    fun local(): Map<String, String> = prefs.all.mapNotNull { (k, v) ->
        when (v) {
            is String -> k to "s:$v"
            is Int -> k to "i:$v"
            is Float -> k to "f:$v"
            is Boolean -> k to "b:$v"
            is Long -> k to "l:$v"
            else -> null
        }
    }.toMap()

    fun aplicarLocal(dados: Map<String, String>) {
        val ed = prefs.edit().clear()
        dados.forEach { (k, v) ->
            val tipo = v.substringBefore(':', "")
            val valor = v.substringAfter(':', "")
            when (tipo) {
                "s" -> ed.putString(k, valor)
                "i" -> valor.toIntOrNull()?.let { ed.putInt(k, it) }
                "f" -> valor.toFloatOrNull()?.let { ed.putFloat(k, it) }
                "b" -> ed.putBoolean(k, valor == "true")
                "l" -> valor.toLongOrNull()?.let { ed.putLong(k, it) }
            }
        }
        ed.apply()
    }

    private suspend fun chamar(action: String, dados: JsonElement? = null): JsonObject? {
        val tokens = tokenStore.current() ?: return null
        var (accessToken, refreshToken) = tokens
        return try {
            fun corpo() = JsonObject(
                buildMap {
                    put("accessToken", JsonPrimitive(accessToken))
                    put("refreshToken", JsonPrimitive(refreshToken))
                    put("action", JsonPrimitive(action))
                    if (action == "save") put("dados", dados ?: JsonNull)
                },
            )
            var r = NetworkModule.mobileApi.layoutConta(corpo())
            if (r.code() == 401) {
                TokenRefresher.refreshAccessToken(tokenStore, refreshToken)?.let { accessToken = it; r = NetworkModule.mobileApi.layoutConta(corpo()) }
            }
            r.body()?.takeIf { (it["ok"] as? JsonPrimitive)?.content == "true" }
        } catch (e: Exception) {
            AppLog.e("LayoutConta", "Falha em $action", e)
            null
        }
    }

    /** Layout salvo na conta (null = sem conexão ou ainda não salvo). */
    suspend fun baixar(): Map<String, String>? {
        val r = chamar("get") ?: return null
        val d = r["dados"] as? JsonObject ?: return emptyMap()
        return d.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.takeIf { it.isString }?.content?.let { k to it } }.toMap()
    }

    /** Salva na conta o layout deste celular (dados = null volta ao padrão). true = salvou. */
    suspend fun enviar(dados: Map<String, String>?): Boolean =
        chamar("save", dados?.let { m -> JsonObject(m.mapValues { JsonPrimitive(it.value) }) }) != null
}
