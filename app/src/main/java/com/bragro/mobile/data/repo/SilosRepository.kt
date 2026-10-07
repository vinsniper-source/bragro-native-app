package com.bragro.mobile.data.repo

import android.content.Context
import com.bragro.mobile.data.AppLog
import com.bragro.mobile.data.TokenStore
import com.bragro.mobile.data.model.SiloRequest
import com.bragro.mobile.data.model.SiloResponse
import com.bragro.mobile.data.remote.NetworkModule

/** Silos cilíndricos -- cadastro e leituras (nível/temperatura/umidade) via
 * /api/mobile/silos (dispatcher por "action"), que chama DIRETO os mesmos
 * services do site (lib/services/silos.ts) -- sem lógica de negócio
 * duplicada em Kotlin. */
class SilosRepository(context: Context) {
    private val tokenStore = TokenStore(context)

    /** Executa uma action com refresh automático de token em caso de 401. */
    private suspend fun call(build: (accessToken: String, refreshToken: String) -> SiloRequest, falha: String): SiloResponse {
        val tokens = tokenStore.current() ?: return SiloResponse(ok = false, error = "Sessão expirada.")
        var (accessToken, refreshToken) = tokens
        return try {
            var response = NetworkModule.mobileApi.siloAction(build(accessToken, refreshToken))
            if (response.code() == 401) {
                val newAccess = TokenRefresher.refreshAccessToken(tokenStore, refreshToken)
                if (newAccess != null) {
                    accessToken = newAccess
                    response = NetworkModule.mobileApi.siloAction(build(accessToken, refreshToken))
                }
            }
            response.body() ?: SiloResponse(ok = false, error = falha)
        } catch (e: Exception) {
            AppLog.e("SilosRepository", falha, e)
            SiloResponse(ok = false, error = "Sem conexão. Tente novamente.")
        }
    }

    suspend fun listar(): SiloResponse =
        call({ a, r -> SiloRequest(a, r, action = "list") }, "Falha ao listar silos.")

    suspend fun listarFazendas(): SiloResponse =
        call({ a, r -> SiloRequest(a, r, action = "list_farms") }, "Falha ao listar fazendas.")

    suspend fun salvar(
        id: String?,
        nome: String,
        produto: String?,
        fabricante: String?,
        farmId: String?,
        capacidadeT: Double?,
        diametroM: Double?,
        alturaM: Double?,
        externalId: String?,
    ): SiloResponse = call(
        { a, r ->
            SiloRequest(
                a, r, action = if (id != null) "update" else "create", id = id,
                nome = nome, produto = produto, fabricante = fabricante, farmId = farmId,
                capacidadeT = capacidadeT, diametroM = diametroM, alturaM = alturaM, externalId = externalId,
            )
        },
        "Falha ao salvar o silo.",
    )

    suspend fun arquivar(id: String): SiloResponse =
        call({ a, r -> SiloRequest(a, r, action = "archive", id = id) }, "Falha ao arquivar o silo.")

    suspend fun lancarLeitura(
        siloId: String,
        nivelPct: Double,
        temperaturaC: Double?,
        umidadePct: Double?,
        recordedAt: String,
    ): SiloResponse = call(
        { a, r ->
            SiloRequest(
                a, r, action = "create_leitura", siloId = siloId,
                nivelPct = nivelPct, temperaturaC = temperaturaC, umidadePct = umidadePct, recordedAt = recordedAt,
            )
        },
        "Falha ao lançar leitura.",
    )

    suspend fun estoque(siloId: String? = null, dias: Int = 30): SiloResponse =
        call({ a, r -> SiloRequest(a, r, action = "estoque", siloId = siloId, dias = dias) }, "Falha ao calcular estoque.")
}
