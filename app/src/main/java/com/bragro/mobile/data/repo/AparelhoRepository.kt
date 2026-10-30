package com.bragro.mobile.data.repo

import android.content.Context
import com.bragro.mobile.data.AppLog
import com.bragro.mobile.data.TokenStore
import com.bragro.mobile.data.remote.NetworkModule
import com.bragro.mobile.ui.seguranca.SegurancaPrefs
import kotlinx.serialization.Serializable

/** v72b -- avisa o servidor que este aparelho está em uso (/api/mobile/
 * aparelho). sair=true: foi desconectado em "Meus dados e aparelhos" ou
 * ficou 30 dias sem uso. */
@Serializable
data class AparelhoRequest(val accessToken: String, val refreshToken: String, val deviceId: String, val aposLogin: Boolean = false)

@Serializable
data class AparelhoResponse(val ok: Boolean, val sair: Boolean = false, val error: String? = null)

class AparelhoRepository(private val context: Context) {
    private val tokenStore = TokenStore(context)

    /** null = sem resposta (offline etc.), não faz nada. */
    suspend fun avisarUso(): Boolean? {
        val agora = System.currentTimeMillis()
        if (agora - SegurancaPrefs.ultimoHeartbeat(context) < 30 * 60_000L) return null
        val (access, refresh) = tokenStore.current() ?: return null
        return try {
            val aposLogin = SegurancaPrefs.aposLogin(context)
            val r = NetworkModule.mobileApi.aparelho(AparelhoRequest(access, refresh, SegurancaPrefs.idAparelho(context), aposLogin))
            val b = r.body()
            if (!r.isSuccessful || b?.ok != true) return null
            SegurancaPrefs.setUltimoHeartbeat(context, agora)
            if (aposLogin) SegurancaPrefs.setAposLogin(context, false)
            b.sair
        } catch (e: Exception) {
            AppLog.e("AparelhoRepository", "Falha ao avisar uso do aparelho", e)
            null
        }
    }
}
