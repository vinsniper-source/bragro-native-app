package com.bragro.mobile.data.repo

import android.content.Context
import com.bragro.mobile.data.AppLog
import com.bragro.mobile.data.TokenStore
import com.bragro.mobile.data.model.CertificadoDigitalData
import com.bragro.mobile.data.model.CertificadoDigitalRequest
import com.bragro.mobile.data.remote.NetworkModule

/** Certificado digital (tipo/emissor/validade) da organização -- paridade com
 * o card de NF-e/NFS-e do site (Task #405). Só metadado, sem cache offline
 * (mesmo critério do ProdutorRuralRepository). */
class CertificadoDigitalRepository(context: Context) {
    private val tokenStore = TokenStore(context)

    suspend fun fetch(): CertificadoDigitalData? = call(null)

    suspend fun save(config: CertificadoDigitalData): CertificadoDigitalData? = call(config)

    private suspend fun call(save: CertificadoDigitalData?): CertificadoDigitalData? {
        val tokens = tokenStore.current() ?: return null
        var (accessToken, refreshToken) = tokens
        return try {
            var response = NetworkModule.mobileApi.certificadoDigital(CertificadoDigitalRequest(accessToken, refreshToken, save))
            if (response.code() == 401) {
                val newAccess = TokenRefresher.refreshAccessToken(tokenStore, refreshToken)
                if (newAccess != null) {
                    accessToken = newAccess
                    response = NetworkModule.mobileApi.certificadoDigital(CertificadoDigitalRequest(accessToken, refreshToken, save))
                }
            }
            val body = response.body()
            if (!response.isSuccessful || body?.ok != true) return null
            body.config
        } catch (e: Exception) {
            AppLog.e("CertificadoDigitalRepository", "Falha ao buscar/salvar certificado digital", e)
            null
        }
    }
}
