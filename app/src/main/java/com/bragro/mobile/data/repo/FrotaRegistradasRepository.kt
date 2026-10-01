package com.bragro.mobile.data.repo

import android.content.Context
import com.bragro.mobile.data.AppLog
import com.bragro.mobile.data.TokenStore
import com.bragro.mobile.data.model.FrotasRegistradasRequest
import com.bragro.mobile.data.model.FrotasRegistradasResponse
import com.bragro.mobile.data.remote.NetworkModule

// Nomes de frota com pelo menos um lançamento real (FrotaRegistro) -- usado
// pra limitar "QR Codes das máquinas" (FrotaQrScreen.kt) ao que a fazenda de
// fato usa, em vez do catálogo genérico inteiro de Base de Dados > Frotas
// (pedido do usuário: "esses QRCode foram criados a partir da base de dados
// ou são ficticios... use como padrao os que tiverem cadastrado na base de
// dados"). Mesmo padrão de ColheitaQrRepository. Qualquer falha (rede,
// sessão) retorna null -- o caller (FrotaQrViewModel) cai de volta pro
// catálogo completo já cacheado localmente, nunca mostra "Sem conexão" no
// lugar de uma tela que já funcionava offline antes.
class FrotaRegistradasRepository(context: Context) {
    private val tokenStore = TokenStore(context)

    suspend fun fetch(): List<String>? {
        val tokens = tokenStore.current() ?: return null
        var (accessToken, refreshToken) = tokens
        return try {
            var response = NetworkModule.mobileApi.frotasRegistradas(FrotasRegistradasRequest(accessToken, refreshToken))
            if (response.code() == 401) {
                val newAccess = TokenRefresher.refreshAccessToken(tokenStore, refreshToken)
                if (newAccess != null) {
                    accessToken = newAccess
                    response = NetworkModule.mobileApi.frotasRegistradas(FrotasRegistradasRequest(accessToken, refreshToken))
                }
            }
            val body: FrotasRegistradasResponse? = response.body()
            if (!response.isSuccessful || body?.ok != true) null else body.frotas
        } catch (e: Exception) {
            AppLog.e("FrotaRegistradasRepository", "Falha ao buscar frotas registradas (QR Codes das máquinas)", e)
            null
        }
    }
}
