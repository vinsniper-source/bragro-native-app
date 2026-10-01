package com.bragro.mobile.data.repo

import android.content.Context
import com.bragro.mobile.data.AppLog
import com.bragro.mobile.data.TokenStore
import com.bragro.mobile.data.model.ColheitaLotesQrRequest
import com.bragro.mobile.data.model.ColheitaLotesQrResponse
import com.bragro.mobile.data.remote.NetworkModule

// QR Code de Rastreabilidade de Colheita (Task #773, paridade com
// listLotesParaQr/RastreabilidadeQrButton do site) -- mesmo padrão do
// ReconciliacaoEstoqueRepository: busca os dados prontos do servidor
// (lotes já colhidos), o payload do QR em si é montado localmente
// (ColheitaQr.kt).
class ColheitaQrRepository(context: Context) {
    private val tokenStore = TokenStore(context)

    suspend fun fetch(): ColheitaLotesQrResponse? {
        val tokens = tokenStore.current() ?: return null
        var (accessToken, refreshToken) = tokens
        return try {
            var response = NetworkModule.mobileApi.colheitaLotesQr(ColheitaLotesQrRequest(accessToken, refreshToken))
            if (response.code() == 401) {
                val newAccess = TokenRefresher.refreshAccessToken(tokenStore, refreshToken)
                if (newAccess != null) {
                    accessToken = newAccess
                    response = NetworkModule.mobileApi.colheitaLotesQr(ColheitaLotesQrRequest(accessToken, refreshToken))
                }
            }
            val body = response.body()
            if (!response.isSuccessful || body?.ok != true) null else body
        } catch (e: Exception) {
            AppLog.e("ColheitaQrRepository", "Falha ao buscar lotes de Colheita para QR Code de Rastreabilidade", e)
            null
        }
    }
}
