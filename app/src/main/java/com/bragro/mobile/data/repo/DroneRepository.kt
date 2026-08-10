package com.bragro.mobile.data.repo

import android.content.Context
import com.bragro.mobile.data.TokenStore
import com.bragro.mobile.data.model.DroneCreateRequest
import com.bragro.mobile.data.model.DroneListRequest
import com.bragro.mobile.data.model.DroneRecordDto
import com.bragro.mobile.data.remote.NetworkModule

/** Módulo Drone (Task #106) -- busca/lança em /api/mobile/drone, mesmos
 * dados de src/app/(app)/drone/actions.ts no site. Sem cache no Room de
 * propósito (mesmo critério de ChartsRepository): a lista de voos é
 * pequena (take=100) e não precisa funcionar offline -- é sempre um
 * registro histórico de algo já ocorrido, nunca um lançamento urgente de
 * campo sem sinal. */
class DroneRepository(context: Context) {
    private val tokenStore = TokenStore(context)

    suspend fun list(): List<DroneRecordDto> {
        val tokens = tokenStore.current() ?: return emptyList()
        var (accessToken, refreshToken) = tokens
        return try {
            var response = NetworkModule.mobileApi.droneList(DroneListRequest(accessToken, refreshToken))
            if (response.code() == 401) {
                val newAccess = TokenRefresher.refreshAccessToken(tokenStore, refreshToken)
                if (newAccess != null) {
                    accessToken = newAccess
                    response = NetworkModule.mobileApi.droneList(DroneListRequest(accessToken, refreshToken))
                }
            }
            val body = response.body()
            if (!response.isSuccessful || body?.ok != true) emptyList() else body.records
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Retorna o registro criado, ou null (e uma mensagem de erro, quando
     * disponível) em caso de falha -- quem chama decide como avisar o
     * usuário. Ao contrário dos módulos genéricos, Drone NÃO tem fila
     * offline própria: sem conexão, o upload falha e o usuário tenta de
     * novo depois (o arquivo já capturado não se perde, fica no picker). */
    suspend fun create(
        data: String, talhao: String?, tipoCaptura: String, piloto: String?,
        altitude: Double?, areaCoberta: Double?, observacoes: String?,
        storagePath: String, publicUrl: String, fileSizeBytes: Long,
    ): Result<DroneRecordDto> {
        val tokens = tokenStore.current() ?: return Result.failure(Exception("Sem sessão."))
        var (accessToken, refreshToken) = tokens
        fun buildRequest(token: String) = DroneCreateRequest(
            token, refreshToken, data = data, talhao = talhao, tipoCaptura = tipoCaptura,
            piloto = piloto, altitude = altitude, areaCoberta = areaCoberta, observacoes = observacoes,
            storagePath = storagePath, publicUrl = publicUrl, fileSizeBytes = fileSizeBytes,
        )
        return try {
            var response = NetworkModule.mobileApi.droneCreate(buildRequest(accessToken))
            if (response.code() == 401) {
                val newAccess = TokenRefresher.refreshAccessToken(tokenStore, refreshToken)
                if (newAccess != null) {
                    accessToken = newAccess
                    response = NetworkModule.mobileApi.droneCreate(buildRequest(accessToken))
                }
            }
            val body = response.body()
            if (response.isSuccessful && body?.ok == true && body.record != null) {
                Result.success(body.record)
            } else {
                Result.failure(Exception(body?.error ?: "Falha ao salvar registro de drone."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
