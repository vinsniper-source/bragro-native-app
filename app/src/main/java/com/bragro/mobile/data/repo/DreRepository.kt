package com.bragro.mobile.data.repo

import android.content.Context
import com.bragro.mobile.data.AppLog
import com.bragro.mobile.data.TokenStore
import com.bragro.mobile.data.local.AppDatabase
import com.bragro.mobile.data.local.DreEntity
import com.bragro.mobile.data.model.DreData
import com.bragro.mobile.data.model.DreRequest
import com.bragro.mobile.data.remote.NetworkModule
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** DRE consolidado (Fase 2, Task #32) -- busca em /api/mobile/dre
 * (reaproveita getDreConsolidado() do site, ver route.ts) e grava no Room
 * (DreEntity, um unico "retrato" da ultima consulta) pra a tela conseguir
 * abrir offline com o ultimo resultado conhecido. Mesmo padrao de
 * DashboardRepository. */
class DreRepository(context: Context) {
    private val db = AppDatabase.get(context)
    private val tokenStore = TokenStore(context)
    private val json = Json { ignoreUnknownKeys = true }

    fun observeCached(): Flow<DreEntity?> = db.dreDao().observe()

    fun parse(entity: DreEntity): DreData = json.decodeFromString(DreData.serializer(), entity.dataJson)

    /** Retorna true se conseguiu atualizar do servidor; false se
     * offline/sem sessao (quem chamou continua podendo mostrar o cache
     * antigo via observeCached()). */
    suspend fun refresh(safra: String?, cultura: String?): Boolean {
        val tokens = tokenStore.current() ?: return false
        var (accessToken, refreshToken) = tokens
        return try {
            var response = NetworkModule.mobileApi.dre(DreRequest(accessToken, refreshToken, safra, cultura))
            // Ver comentario equivalente em DashboardRepository (Task #37).
            if (response.code() == 401) {
                val newAccess = TokenRefresher.refreshAccessToken(tokenStore, refreshToken)
                if (newAccess != null) {
                    accessToken = newAccess
                    response = NetworkModule.mobileApi.dre(DreRequest(accessToken, refreshToken, safra, cultura))
                }
            }
            val body = response.body()
            if (!response.isSuccessful || body?.ok != true || body.dre == null) return false

            db.dreDao().upsert(
                DreEntity(
                    safra = safra,
                    cultura = cultura,
                    dataJson = json.encodeToString(DreData.serializer(), body.dre),
                    atualizadoEmMillis = System.currentTimeMillis(),
                )
            )
            true
        } catch (e: Exception) {
            AppLog.e("DreRepository", "Falha ao atualizar DRE consolidado (safra=$safra, cultura=$cultura)", e)
            false
        }
    }
}
