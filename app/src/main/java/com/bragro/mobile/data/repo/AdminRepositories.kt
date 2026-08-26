package com.bragro.mobile.data.repo

import android.content.Context
import com.bragro.mobile.data.AppLog
import com.bragro.mobile.data.TokenStore
import com.bragro.mobile.data.model.BaseDeDadosRequest
import com.bragro.mobile.data.model.SecurityRequest
import com.bragro.mobile.data.model.SettingsRequest
import com.bragro.mobile.data.remote.NetworkModule
import kotlinx.serialization.json.JsonElement

/** As 3 telas administrativas nativas (Configurações/Base de Dados/Acessos,
 * Task #148) -- pedido explícito e repetido do usuário ("não use nada para
 * redirecionar, quero ele fixo nesse app"), substituindo o antigo
 * BridgeRepository (Custom Tabs abrindo o site). MESMO padrão genérico de
 * "action" + retry em 401 já usado por NotificationsRepository/
 * NoticesRepository -- resultado cru (JsonElement), cada tela re-parseia
 * como JsonObject/JsonArray. Sem cache no Room de propósito: são telas de
 * administração usadas raramente, cujo valor real está sempre em mostrar o
 * estado mais recente do servidor (mesmo critério do Mural/Alertas/Monitor
 * no Início). */
class SettingsRepository(context: Context) {
    private val tokenStore = TokenStore(context)

    suspend fun run(
        action: String,
        name: String? = null,
        toleranciaPct: Double? = null,
        notifTelegramBotToken: String? = null,
        notifTelegramChatId: String? = null,
        notifWhatsappPhoneId: String? = null,
        notifWhatsappToken: String? = null,
        notifWhatsappTo: String? = null,
        notifChannelPush: Boolean? = null,
        notifChannelTelegram: Boolean? = null,
        notifChannelWhatsapp: Boolean? = null,
        notifFrotaManutencao: Boolean? = null,
        notifRomaneioDiario: Boolean? = null,
        notifEstoqueMinimo: Boolean? = null,
        channel: String? = null,
        plano: String? = null,
    ): Result<JsonElement?> {
        val tokens = tokenStore.current() ?: return Result.failure(IllegalStateException("Sessão expirada. Entre novamente."))
        var (accessToken, refreshToken) = tokens
        fun body() = SettingsRequest(
            accessToken, refreshToken, action, name, toleranciaPct,
            notifTelegramBotToken, notifTelegramChatId, notifWhatsappPhoneId, notifWhatsappToken, notifWhatsappTo,
            notifChannelPush, notifChannelTelegram, notifChannelWhatsapp,
            notifFrotaManutencao, notifRomaneioDiario, notifEstoqueMinimo,
            channel, plano,
        )
        return try {
            var response = NetworkModule.mobileApi.settings(body())
            if (response.code() == 401) {
                val newAccess = TokenRefresher.refreshAccessToken(tokenStore, refreshToken)
                if (newAccess != null) {
                    accessToken = newAccess
                    response = NetworkModule.mobileApi.settings(body())
                }
            }
            val resp = response.body()
            if (!response.isSuccessful || resp?.ok != true) Result.failure(IllegalStateException(resp?.error ?: "Falha na operação."))
            else Result.success(resp.result)
        } catch (e: Exception) {
            AppLog.e("SettingsRepository", "Falha ao executar ação '$action' em Configurações", e)
            Result.failure(e)
        }
    }
}

class BaseDeDadosRepository(context: Context) {
    private val tokenStore = TokenStore(context)

    suspend fun run(
        action: String,
        category: String? = null,
        value: String? = null,
        id: String? = null,
        ativo: Boolean? = null,
        name: String? = null,
        areaHa: Double? = null,
        areaSafrinhaHa: Double? = null,
        areaSafrinhaMilhoHa: Double? = null,
        areaSafrinhaSorgoHa: Double? = null,
    ): Result<JsonElement?> {
        val tokens = tokenStore.current() ?: return Result.failure(IllegalStateException("Sessão expirada. Entre novamente."))
        var (accessToken, refreshToken) = tokens
        fun body() = BaseDeDadosRequest(accessToken, refreshToken, action, category, value, id, ativo, name, areaHa, areaSafrinhaHa, areaSafrinhaMilhoHa, areaSafrinhaSorgoHa)
        return try {
            var response = NetworkModule.mobileApi.baseDeDados(body())
            if (response.code() == 401) {
                val newAccess = TokenRefresher.refreshAccessToken(tokenStore, refreshToken)
                if (newAccess != null) {
                    accessToken = newAccess
                    response = NetworkModule.mobileApi.baseDeDados(body())
                }
            }
            val resp = response.body()
            if (!response.isSuccessful || resp?.ok != true) Result.failure(IllegalStateException(resp?.error ?: "Falha na operação."))
            else Result.success(resp.result)
        } catch (e: Exception) {
            AppLog.e("BaseDeDadosRepository", "Falha ao executar ação '$action' em Base de Dados", e)
            Result.failure(e)
        }
    }
}

class SecurityRepository(context: Context) {
    private val tokenStore = TokenStore(context)

    suspend fun run(
        action: String,
        email: String? = null,
        role: String? = null,
        modulosPermitidos: List<String>? = null,
        membershipId: String? = null,
        ativo: Boolean? = null,
    ): Result<JsonElement?> {
        val tokens = tokenStore.current() ?: return Result.failure(IllegalStateException("Sessão expirada. Entre novamente."))
        var (accessToken, refreshToken) = tokens
        fun body() = SecurityRequest(accessToken, refreshToken, action, email, role, modulosPermitidos, membershipId, ativo)
        return try {
            var response = NetworkModule.mobileApi.security(body())
            if (response.code() == 401) {
                val newAccess = TokenRefresher.refreshAccessToken(tokenStore, refreshToken)
                if (newAccess != null) {
                    accessToken = newAccess
                    response = NetworkModule.mobileApi.security(body())
                }
            }
            val resp = response.body()
            if (!response.isSuccessful || resp?.ok != true) Result.failure(IllegalStateException(resp?.error ?: "Falha na operação."))
            else Result.success(resp.result)
        } catch (e: Exception) {
            AppLog.e("SecurityRepository", "Falha ao executar ação '$action' em Acessos/Segurança", e)
            Result.failure(e)
        }
    }
}
