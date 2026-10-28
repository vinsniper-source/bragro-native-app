package com.bragro.mobile.data.repo

import android.content.Context
import com.bragro.mobile.data.AppLog
import com.bragro.mobile.data.TokenStore
import com.bragro.mobile.data.remote.NetworkModule
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** v57 -- prévia dos campos automáticos do formulário (/api/mobile/
 * preview-computed): o servidor faz a mesma conta do salvar e devolve o
 * resultado sem gravar. Valores viram texto no mesmo formato que o
 * formulário já usa para exibir os campos calculados. */
@Serializable
data class PreviaCalculadosRequest(
    val accessToken: String,
    val refreshToken: String,
    val domainId: String,
    val recordId: String? = null,
    val fields: Map<String, String>,
)

@Serializable
data class PreviaCalculadosResponse(val ok: Boolean, val previa: JsonElement? = null, val error: String? = null)

data class PreviaCalculados(val ok: Boolean, val valores: Map<String, String>, val dicas: Map<String, String>, val falta: String?)

class PreviaCalculadosRepository(context: Context) {
    private val tokenStore = TokenStore(context)

    suspend fun calcular(domainId: String, recordId: String?, fields: Map<String, String>): PreviaCalculados? {
        val tokens = tokenStore.current() ?: return null
        var (accessToken, refreshToken) = tokens
        return try {
            var response = NetworkModule.mobileApi.previewComputed(PreviaCalculadosRequest(accessToken, refreshToken, domainId, recordId, fields))
            if (response.code() == 401) {
                val novo = TokenRefresher.refreshAccessToken(tokenStore, refreshToken)
                if (novo != null) {
                    accessToken = novo
                    response = NetworkModule.mobileApi.previewComputed(PreviaCalculadosRequest(accessToken, refreshToken, domainId, recordId, fields))
                }
            }
            val body = response.body()
            if (!response.isSuccessful || body?.ok != true) return null
            val p = body.previa?.jsonObject ?: return null
            val ok = p["ok"]?.jsonPrimitive?.booleanOrNull == true
            if (!ok) return PreviaCalculados(false, emptyMap(), emptyMap(), p["falta"]?.jsonPrimitive?.contentOrNull)
            PreviaCalculados(
                ok = true,
                valores = (p["valores"] as? JsonObject)?.mapValues { (_, v) -> texto(v) } ?: emptyMap(),
                dicas = (p["dicas"] as? JsonObject)?.mapValues { (_, v) -> texto(v) } ?: emptyMap(),
                falta = null,
            )
        } catch (e: Exception) {
            AppLog.e("PreviaCalculadosRepository", "Falha ao calcular a prévia", e)
            null
        }
    }

    private fun texto(v: JsonElement): String = when (v) {
        is JsonNull -> ""
        is JsonPrimitive -> v.contentOrNull ?: ""
        else -> v.toString()
    }
}
