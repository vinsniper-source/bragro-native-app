package com.bragro.mobile.data.repo

import android.content.Context
import com.bragro.mobile.data.AppLog
import com.bragro.mobile.data.TokenStore
import com.bragro.mobile.data.model.PragaIaRequest
import com.bragro.mobile.data.model.PragaIaResponse
import com.bragro.mobile.data.remote.NetworkModule

/** IA de diagnóstico de pragas por foto -- paridade com o site (Task #601).
 * Chama /api/mobile/pragas-ia, que chama DIRETO diagnosticarPragaFotoAction()
 * no servidor -- mesmo motor de IA (Claude vision) que o site usa, nenhuma
 * lógica de IA duplicada aqui. Mesmo padrão de retry em 401 já usado em
 * OrcamentoRepository/RomaneioQuickScreen. */
class PragaIaRepository(context: Context) {
    private val tokenStore = TokenStore(context)

    suspend fun diagnosticar(fotoUrl: String): PragaIaResponse? {
        val tokens = tokenStore.current() ?: return null
        var (accessToken, refreshToken) = tokens
        return try {
            fun buildRequest() = PragaIaRequest(accessToken = accessToken, refreshToken = refreshToken, fotoUrl = fotoUrl)
            var response = NetworkModule.mobileApi.pragaIa(buildRequest())
            if (response.code() == 401) {
                val newAccess = TokenRefresher.refreshAccessToken(tokenStore, refreshToken)
                if (newAccess != null) {
                    accessToken = newAccess
                    response = NetworkModule.mobileApi.pragaIa(buildRequest())
                }
            }
            response.body() ?: PragaIaResponse(ok = false, error = "Falha no diagnóstico automático.")
        } catch (e: Exception) {
            AppLog.e("PragaIaRepository", "Falha ao chamar diagnóstico de praga por foto", e)
            PragaIaResponse(ok = false, error = "Sem conexão. Tente novamente.")
        }
    }
}
