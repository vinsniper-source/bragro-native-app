package com.bragro.mobile.data.repo

import android.content.Context
import com.bragro.mobile.data.AppLog
import com.bragro.mobile.data.TokenStore
import com.bragro.mobile.data.local.AppDatabase
import com.bragro.mobile.data.local.OperacoesEntity
import com.bragro.mobile.data.model.OperacoesRequest
import com.bragro.mobile.data.model.OperacoesResponse
import com.bragro.mobile.data.remote.NetworkModule
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Visão "Operação" agrupada (gap encontrado na auditoria módulo-a-módulo
 * contra o site, pedido do usuário "implemente tudo que falta ainda para o
 * app native da plataforma") -- busca em /api/mobile/operacoes (reaproveita
 * getOperacoes() do site, ver route.ts) e grava no Room (OperacoesEntity, um
 * único "retrato" da última consulta) pra a tela conseguir abrir offline com
 * o último resultado conhecido. Mesmo padrão de DreRepository. */
class OperacoesRepository(context: Context) {
    private val db = AppDatabase.get(context)
    private val tokenStore = TokenStore(context)
    private val json = Json { ignoreUnknownKeys = true }

    fun observeCached(): Flow<OperacoesEntity?> = db.operacoesDao().observe()

    fun parse(entity: OperacoesEntity): List<com.bragro.mobile.data.model.OperacaoAgrupadaData> =
        json.decodeFromString(OperacoesResponse.serializer(), entity.dataJson).operacoes

    /** Retorna true se conseguiu atualizar do servidor; false se
     * offline/sem sessao (quem chamou continua podendo mostrar o cache
     * antigo via observeCached()). */
    suspend fun refresh(janela: Int): Boolean {
        val tokens = tokenStore.current() ?: return false
        var (accessToken, refreshToken) = tokens
        return try {
            var response = NetworkModule.mobileApi.operacoes(OperacoesRequest(accessToken, refreshToken, janela))
            if (response.code() == 401) {
                val newAccess = TokenRefresher.refreshAccessToken(tokenStore, refreshToken)
                if (newAccess != null) {
                    accessToken = newAccess
                    response = NetworkModule.mobileApi.operacoes(OperacoesRequest(accessToken, refreshToken, janela))
                }
            }
            val body = response.body()
            if (!response.isSuccessful || body?.ok != true) return false

            db.operacoesDao().upsert(
                OperacoesEntity(
                    janela = janela,
                    dataJson = json.encodeToString(OperacoesResponse.serializer(), body),
                    atualizadoEmMillis = System.currentTimeMillis(),
                )
            )
            true
        } catch (e: Exception) {
            AppLog.e("OperacoesRepository", "Falha ao atualizar Operações (janela=$janela)", e)
            false
        }
    }
}
