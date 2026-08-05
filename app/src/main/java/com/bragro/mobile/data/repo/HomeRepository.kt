package com.bragro.mobile.data.repo

import android.content.Context
import com.bragro.mobile.data.TokenStore
import com.bragro.mobile.data.local.AppDatabase
import com.bragro.mobile.data.local.HomeEntity
import com.bragro.mobile.data.model.HomeData
import com.bragro.mobile.data.model.HomeRequest
import com.bragro.mobile.data.remote.NetworkModule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

/** Réplica mobile do Início (Mural de Avisos + Central de Alertas + Monitor
 * de atividade recente + KPIs) -- busca em /api/mobile/home. Antes era DE
 * PROPÓSITO sem cache no Room (avisos/alertas desatualizados pareciam mais
 * confusos que úteis) -- pedido do usuário mudou isso ("quanto ao dashboard
 * é possível colocá-lo para aparecer offline?"): agora grava o último
 * retrato bem-sucedido no Room (HomeEntity, blob JSON, mesmo padrão do
 * DreEntity/AnalisesEntity) pra a tela abrir com o último resultado
 * conhecido quando offline, em vez de "Sem dados ainda". */
class HomeRepository(context: Context) {
    private val tokenStore = TokenStore(context)
    private val db = AppDatabase.get(context)
    private val json = Json { ignoreUnknownKeys = true }

    /** Último retrato salvo, se houver -- usado pra mostrar algo na tela
     * assim que ela abre, antes mesmo da primeira resposta de rede chegar
     * (ou pra sempre, se estiver offline). */
    fun observeCached(): Flow<HomeData?> =
        db.homeDao().observe().map { entity ->
            entity?.let { runCatching { json.decodeFromString(HomeData.serializer(), it.homeJson) }.getOrNull() }
        }

    suspend fun fetch(): HomeData? {
        val tokens = tokenStore.current() ?: return null
        var (accessToken, refreshToken) = tokens
        return try {
            var response = NetworkModule.mobileApi.home(HomeRequest(accessToken, refreshToken))
            if (response.code() == 401) {
                val newAccess = TokenRefresher.refreshAccessToken(tokenStore, refreshToken)
                if (newAccess != null) {
                    accessToken = newAccess
                    response = NetworkModule.mobileApi.home(HomeRequest(accessToken, refreshToken))
                }
            }
            val body = response.body()
            val home = if (!response.isSuccessful || body?.ok != true) null else body.home
            if (home != null) {
                db.homeDao().upsert(
                    HomeEntity(homeJson = json.encodeToString(HomeData.serializer(), home), atualizadoEmMillis = System.currentTimeMillis())
                )
            }
            home
        } catch (e: Exception) {
            null
        }
    }
}
