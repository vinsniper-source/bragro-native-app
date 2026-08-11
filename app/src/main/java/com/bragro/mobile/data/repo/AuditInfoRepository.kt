package com.bragro.mobile.data.repo

import android.content.Context
import com.bragro.mobile.data.AppLog
import com.bragro.mobile.data.TokenStore
import com.bragro.mobile.data.model.AuditEntry
import com.bragro.mobile.data.model.AuditInfoRequest
import com.bragro.mobile.data.remote.NetworkModule

/** "Editado por" + data/hora dentro de cada card (pedido do usuário:
 * "coloque a condição que quando alguém editar um lançamento apareça
 * dentro do bloco o usuário, dia e hora... via histórico de alterações"),
 * lendo /api/mobile/audit-info -- que devolve só a ÚLTIMA edição de cada
 * recordId pedido (não o histórico completo). Sem cache no Room de
 * propósito, mesmo critério de ChartsRepository/WeatherRepository: essa
 * informação só importa enquanto a tela de lista está aberta e com
 * conexão; sem rede, os cards simplesmente não mostram o rótulo (em vez de
 * arriscar mostrar um "editado por" desatualizado). */
class AuditInfoRepository(context: Context) {
    private val tokenStore = TokenStore(context)

    suspend fun fetch(domainId: String, recordIds: List<String>): Map<String, AuditEntry> {
        if (recordIds.isEmpty()) return emptyMap()
        val tokens = tokenStore.current() ?: return emptyMap()
        var (accessToken, refreshToken) = tokens
        return try {
            var response = NetworkModule.mobileApi.auditInfo(AuditInfoRequest(accessToken, refreshToken, domainId, recordIds))
            if (response.code() == 401) {
                val newAccess = TokenRefresher.refreshAccessToken(tokenStore, refreshToken)
                if (newAccess != null) {
                    accessToken = newAccess
                    response = NetworkModule.mobileApi.auditInfo(AuditInfoRequest(accessToken, refreshToken, domainId, recordIds))
                }
            }
            val body = response.body()
            if (!response.isSuccessful || body?.ok != true) emptyMap() else body.info
        } catch (e: Exception) {
            AppLog.e("AuditInfoRepository", "Falha ao buscar 'editado por' para domainId=$domainId (${recordIds.size} registros)", e)
            emptyMap()
        }
    }
}
