package com.bragro.mobile.data.repo

import android.content.Context
import com.bragro.mobile.data.TokenStore
import com.bragro.mobile.data.local.AnalisesEntity
import com.bragro.mobile.data.local.AppDatabase
import com.bragro.mobile.data.model.AnalisesRequest
import com.bragro.mobile.data.remote.NetworkModule
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject

/** Analises cruzadas entre modulos (Fase 2, Task #36) -- busca em
 * /api/mobile/analises (reaproveita getAnalisesCruzadas() do site) e grava
 * no Room (AnalisesEntity, um unico "retrato" da ultima consulta) pra a
 * tela conseguir abrir offline com o ultimo resultado conhecido. Mesmo
 * padrao de DreRepository/DashboardRepository. */
class AnalisesRepository(context: Context) {
    private val db = AppDatabase.get(context)
    private val tokenStore = TokenStore(context)

    fun observeCached(): Flow<AnalisesEntity?> = db.analisesDao().observe()

    fun parse(entity: AnalisesEntity): JsonObject =
        kotlinx.serialization.json.Json.parseToJsonElement(entity.analisesJson) as JsonObject

    fun safras(entity: AnalisesEntity): List<String> =
        entity.safrasDisponiveisCsv.split(",").filter { it.isNotBlank() }

    fun culturas(entity: AnalisesEntity): List<String> =
        entity.culturasDisponiveisCsv.split(",").filter { it.isNotBlank() }

    suspend fun refresh(safra: String?, cultura: String? = null): Boolean {
        val tokens = tokenStore.current() ?: return false
        var (accessToken, refreshToken) = tokens
        return try {
            var response = NetworkModule.mobileApi.analises(AnalisesRequest(accessToken, refreshToken, safra, cultura))
            // Ver comentario equivalente em DashboardRepository (Task #37).
            if (response.code() == 401) {
                val newAccess = TokenRefresher.refreshAccessToken(tokenStore, refreshToken)
                if (newAccess != null) {
                    accessToken = newAccess
                    response = NetworkModule.mobileApi.analises(AnalisesRequest(accessToken, refreshToken, safra, cultura))
                }
            }
            val body = response.body()
            if (!response.isSuccessful || body?.ok != true || body.analises == null) return false

            db.analisesDao().upsert(
                AnalisesEntity(
                    safra = safra,
                    cultura = cultura,
                    analisesJson = body.analises.toString(),
                    safrasDisponiveisCsv = body.safrasDisponiveis.joinToString(","),
                    culturasDisponiveisCsv = body.culturasDisponiveis.joinToString(","),
                    atualizadoEmMillis = System.currentTimeMillis(),
                )
            )
            true
        } catch (e: Exception) {
            false
        }
    }
}
