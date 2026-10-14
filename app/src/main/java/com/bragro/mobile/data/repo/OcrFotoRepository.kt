package com.bragro.mobile.data.repo

import android.content.Context
import com.bragro.mobile.data.AppLog
import com.bragro.mobile.data.TokenStore
import com.bragro.mobile.data.model.OcrFotoNota
import com.bragro.mobile.data.model.OcrFotoRequest
import com.bragro.mobile.data.model.OcrFotoResponse
import com.bragro.mobile.data.remote.NetworkModule

/** "Preencher com foto (IA)" -- paridade com o site (Task #889-895). Chama
 * /api/mobile/ocr-foto, que chama DIRETO as mesmas Server Actions do site
 * (nenhum prompt/IA duplicado aqui). Mesmo padrão de retry em 401 do
 * PragaIaRepository. */
class OcrFotoRepository(context: Context) {
    private val tokenStore = TokenStore(context)

    private suspend fun chamar(build: (String, String) -> OcrFotoRequest): OcrFotoResponse {
        val tokens = tokenStore.current() ?: return OcrFotoResponse(ok = false, error = "Sessão expirada. Faça login novamente.")
        var (accessToken, refreshToken) = tokens
        return try {
            var response = NetworkModule.mobileApi.ocrFoto(build(accessToken, refreshToken))
            if (response.code() == 401) {
                val newAccess = TokenRefresher.refreshAccessToken(tokenStore, refreshToken)
                if (newAccess != null) {
                    accessToken = newAccess
                    response = NetworkModule.mobileApi.ocrFoto(build(accessToken, refreshToken))
                }
            }
            val body = response.body()
            if (body != null) return body
            // Erros 4xx da rota vêm como {"ok":false,"error":"..."} -- extrai a mensagem.
            val raw = response.errorBody()?.string().orEmpty()
            val msg = Regex("\"error\"\\s*:\\s*\"([^\"]*)\"").find(raw)?.groupValues?.get(1)
            OcrFotoResponse(ok = false, error = msg ?: "Falha na leitura automática da foto.")
        } catch (e: Exception) {
            AppLog.e("OcrFotoRepository", "Falha ao chamar leitura automática por foto", e)
            OcrFotoResponse(ok = false, error = "Sem conexão. Tente novamente.")
        }
    }

    suspend fun lerGenerico(domainId: String, fotoUrl: String): OcrFotoResponse =
        chamar { a, r -> OcrFotoRequest(a, r, tipo = "generico", domainId = domainId, fotoUrl = fotoUrl) }

    suspend fun lerFazenda(fotoUrl: String): OcrFotoResponse =
        chamar { a, r -> OcrFotoRequest(a, r, tipo = "fazenda", fotoUrl = fotoUrl) }

    suspend fun lerNotaPapel(fotoUrl: String): OcrFotoResponse =
        chamar { a, r -> OcrFotoRequest(a, r, tipo = "nota_papel", fotoUrl = fotoUrl) }

    suspend fun confirmarNotaPapel(nota: OcrFotoNota, fazendaDestino: String): OcrFotoResponse =
        chamar { a, r -> OcrFotoRequest(a, r, tipo = "nota_papel_confirmar", nota = nota, fazendaDestino = fazendaDestino) }
}
