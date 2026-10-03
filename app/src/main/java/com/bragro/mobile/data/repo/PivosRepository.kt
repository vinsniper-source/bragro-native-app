package com.bragro.mobile.data.repo

import android.content.Context
import com.bragro.mobile.data.AppLog
import com.bragro.mobile.data.TokenStore
import com.bragro.mobile.data.model.FarmLookupData
import com.bragro.mobile.data.model.PivoArchiveRequest
import com.bragro.mobile.data.model.PivoBalancoHidricoRequest
import com.bragro.mobile.data.model.PivoBalancoHidricoResponse
import com.bragro.mobile.data.model.PivoControleRequest
import com.bragro.mobile.data.model.PivoFarmsResponse
import com.bragro.mobile.data.model.PivoGenericResponse
import com.bragro.mobile.data.model.PivoListRequest
import com.bragro.mobile.data.model.PivoListResponse
import com.bragro.mobile.data.model.PivoSaveRequest
import com.bragro.mobile.data.model.PivoSaveResponse
import com.bragro.mobile.data.model.PivoTelemetriaCreateRequest
import com.bragro.mobile.data.model.PivoTelemetriaListRequest
import com.bragro.mobile.data.model.PivoTelemetriaListResponse
import com.bragro.mobile.data.remote.NetworkModule

/** Pivôs de Irrigação (Lindsay FieldNET/Valley 365-AgSense/Reinke
 * ReinCloud) -- mesmo scaffolding dos demais módulos de telemetria
 * (Frota/Romaneio/Pecuária): cadastro, telemetria manual e "controle"
 * (sem comando real pro equipamento, ver aviso na tela) via
 * /api/mobile/pivos (dispatcher por "action"), que chama DIRETO os
 * mesmos services do site (lib/services/pivos.ts) -- sem lógica de
 * negócio duplicada em Kotlin. */
class PivosRepository(context: Context) {
    private val tokenStore = TokenStore(context)

    suspend fun listar(farmId: String? = null): PivoListResponse? {
        val tokens = tokenStore.current() ?: return null
        var (accessToken, refreshToken) = tokens
        return try {
            fun buildRequest() = PivoListRequest(accessToken = accessToken, refreshToken = refreshToken, farmId = farmId)
            var response = NetworkModule.mobileApi.pivoList(buildRequest())
            if (response.code() == 401) {
                val newAccess = TokenRefresher.refreshAccessToken(tokenStore, refreshToken)
                if (newAccess != null) {
                    accessToken = newAccess
                    response = NetworkModule.mobileApi.pivoList(buildRequest())
                }
            }
            response.body() ?: PivoListResponse(ok = false, error = "Falha ao listar pivôs.")
        } catch (e: Exception) {
            AppLog.e("PivosRepository", "Falha ao listar pivôs", e)
            PivoListResponse(ok = false, error = "Sem conexão. Tente novamente.")
        }
    }

    suspend fun listarFazendas(): List<FarmLookupData> {
        val tokens = tokenStore.current() ?: return emptyList()
        var (accessToken, refreshToken) = tokens
        return try {
            fun buildRequest() = PivoListRequest(accessToken = accessToken, refreshToken = refreshToken, action = "list_farms")
            var response = NetworkModule.mobileApi.pivoListFarms(buildRequest())
            if (response.code() == 401) {
                val newAccess = TokenRefresher.refreshAccessToken(tokenStore, refreshToken)
                if (newAccess != null) {
                    accessToken = newAccess
                    response = NetworkModule.mobileApi.pivoListFarms(buildRequest())
                }
            }
            (response.body() as? PivoFarmsResponse)?.farms ?: emptyList()
        } catch (e: Exception) {
            AppLog.e("PivosRepository", "Falha ao listar fazendas", e)
            emptyList()
        }
    }

    suspend fun salvar(
        id: String?,
        nome: String,
        marca: String,
        farmId: String?,
        raioM: Double?,
        latitude: Double?,
        longitude: Double?,
    ): PivoSaveResponse? {
        val tokens = tokenStore.current() ?: return null
        var (accessToken, refreshToken) = tokens
        return try {
            fun buildRequest() = PivoSaveRequest(
                accessToken = accessToken, refreshToken = refreshToken,
                action = if (id != null) "update" else "create", id = id,
                nome = nome, marca = marca, farmId = farmId, raioM = raioM, latitude = latitude, longitude = longitude,
            )
            var response = NetworkModule.mobileApi.pivoSave(buildRequest())
            if (response.code() == 401) {
                val newAccess = TokenRefresher.refreshAccessToken(tokenStore, refreshToken)
                if (newAccess != null) {
                    accessToken = newAccess
                    response = NetworkModule.mobileApi.pivoSave(buildRequest())
                }
            }
            response.body() ?: PivoSaveResponse(ok = false, error = "Falha ao salvar o pivô.")
        } catch (e: Exception) {
            AppLog.e("PivosRepository", "Falha ao salvar pivô", e)
            PivoSaveResponse(ok = false, error = "Sem conexão. Tente novamente.")
        }
    }

    suspend fun arquivar(id: String): PivoGenericResponse? {
        val tokens = tokenStore.current() ?: return null
        var (accessToken, refreshToken) = tokens
        return try {
            fun buildRequest() = PivoArchiveRequest(accessToken = accessToken, refreshToken = refreshToken, id = id)
            var response = NetworkModule.mobileApi.pivoArchive(buildRequest())
            if (response.code() == 401) {
                val newAccess = TokenRefresher.refreshAccessToken(tokenStore, refreshToken)
                if (newAccess != null) {
                    accessToken = newAccess
                    response = NetworkModule.mobileApi.pivoArchive(buildRequest())
                }
            }
            response.body()
        } catch (e: Exception) {
            AppLog.e("PivosRepository", "Falha ao arquivar pivô", e)
            PivoGenericResponse(ok = false, error = "Sem conexão. Tente novamente.")
        }
    }

    /** Painel de "controle" -- SEM comando real pro equipamento (não há
     * parceria com nenhum fabricante ainda). Só registra o estado
     * desejado, igual a um lançamento manual. */
    suspend fun registrarControle(id: String, status: String, laminaAtualMm: Double?, sentido: String?): PivoGenericResponse? {
        val tokens = tokenStore.current() ?: return null
        var (accessToken, refreshToken) = tokens
        return try {
            fun buildRequest() = PivoControleRequest(
                accessToken = accessToken, refreshToken = refreshToken, id = id,
                status = status, laminaAtualMm = laminaAtualMm, sentido = sentido,
            )
            var response = NetworkModule.mobileApi.pivoControle(buildRequest())
            if (response.code() == 401) {
                val newAccess = TokenRefresher.refreshAccessToken(tokenStore, refreshToken)
                if (newAccess != null) {
                    accessToken = newAccess
                    response = NetworkModule.mobileApi.pivoControle(buildRequest())
                }
            }
            response.body() ?: PivoGenericResponse(ok = false, error = "Falha ao registrar o comando.")
        } catch (e: Exception) {
            AppLog.e("PivosRepository", "Falha ao registrar controle", e)
            PivoGenericResponse(ok = false, error = "Sem conexão. Tente novamente.")
        }
    }

    suspend fun listarTelemetria(pivoId: String, dias: Int = 30): PivoTelemetriaListResponse? {
        val tokens = tokenStore.current() ?: return null
        var (accessToken, refreshToken) = tokens
        return try {
            fun buildRequest() = PivoTelemetriaListRequest(accessToken = accessToken, refreshToken = refreshToken, pivoId = pivoId, dias = dias)
            var response = NetworkModule.mobileApi.pivoListTelemetria(buildRequest())
            if (response.code() == 401) {
                val newAccess = TokenRefresher.refreshAccessToken(tokenStore, refreshToken)
                if (newAccess != null) {
                    accessToken = newAccess
                    response = NetworkModule.mobileApi.pivoListTelemetria(buildRequest())
                }
            }
            response.body() ?: PivoTelemetriaListResponse(ok = false, error = "Falha ao listar telemetria.")
        } catch (e: Exception) {
            AppLog.e("PivosRepository", "Falha ao listar telemetria", e)
            PivoTelemetriaListResponse(ok = false, error = "Sem conexão. Tente novamente.")
        }
    }

    suspend fun lancarTelemetria(
        pivoId: String,
        waterAppliedMm: Double,
        rainGaugeMm: Double,
        operatingHours: Double?,
        recordedAt: String,
    ): PivoGenericResponse? {
        val tokens = tokenStore.current() ?: return null
        var (accessToken, refreshToken) = tokens
        return try {
            fun buildRequest() = PivoTelemetriaCreateRequest(
                accessToken = accessToken, refreshToken = refreshToken, pivoId = pivoId,
                waterAppliedMm = waterAppliedMm, rainGaugeMm = rainGaugeMm,
                operatingHours = operatingHours, recordedAt = recordedAt,
            )
            var response = NetworkModule.mobileApi.pivoCreateTelemetria(buildRequest())
            if (response.code() == 401) {
                val newAccess = TokenRefresher.refreshAccessToken(tokenStore, refreshToken)
                if (newAccess != null) {
                    accessToken = newAccess
                    response = NetworkModule.mobileApi.pivoCreateTelemetria(buildRequest())
                }
            }
            response.body() ?: PivoGenericResponse(ok = false, error = "Falha ao lançar telemetria.")
        } catch (e: Exception) {
            AppLog.e("PivosRepository", "Falha ao lançar telemetria", e)
            PivoGenericResponse(ok = false, error = "Sem conexão. Tente novamente.")
        }
    }

    suspend fun balancoHidrico(pivoId: String? = null, dias: Int = 30): PivoBalancoHidricoResponse? {
        val tokens = tokenStore.current() ?: return null
        var (accessToken, refreshToken) = tokens
        return try {
            fun buildRequest() = PivoBalancoHidricoRequest(accessToken = accessToken, refreshToken = refreshToken, pivoId = pivoId, dias = dias)
            var response = NetworkModule.mobileApi.pivoBalancoHidrico(buildRequest())
            if (response.code() == 401) {
                val newAccess = TokenRefresher.refreshAccessToken(tokenStore, refreshToken)
                if (newAccess != null) {
                    accessToken = newAccess
                    response = NetworkModule.mobileApi.pivoBalancoHidrico(buildRequest())
                }
            }
            response.body() ?: PivoBalancoHidricoResponse(ok = false, error = "Falha ao calcular balanço hídrico.")
        } catch (e: Exception) {
            AppLog.e("PivosRepository", "Falha ao calcular balanço hídrico", e)
            PivoBalancoHidricoResponse(ok = false, error = "Sem conexão. Tente novamente.")
        }
    }
}
