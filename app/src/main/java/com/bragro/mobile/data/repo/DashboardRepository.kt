package com.bragro.mobile.data.repo

import android.content.Context
import com.bragro.mobile.data.TokenStore
import com.bragro.mobile.data.local.AppDatabase
import com.bragro.mobile.data.local.DashboardEntity
import com.bragro.mobile.data.model.DashboardRequest
import com.bragro.mobile.data.remote.NetworkModule
import kotlinx.coroutines.flow.Flow

/** KPIs do Início (Fase 2, Task #31) -- busca em /api/mobile/dashboard
 * (reaproveita getDashboardStats() do site, ver route.ts) e grava no Room
 * (DashboardEntity) pra a tela conseguir abrir offline com o ultimo retrato
 * conhecido. Mesmo padrao de autenticacao via token de ConfigRepository. */
class DashboardRepository(context: Context) {
    private val db = AppDatabase.get(context)
    private val tokenStore = TokenStore(context)

    fun observeCached(): Flow<DashboardEntity?> = db.dashboardDao().observe()

    /** Retorna true se conseguiu atualizar do servidor (e ja deixou o Room
     * atualizado); false se estava offline/sem sessao -- quem chamou continua
     * podendo mostrar o cache antigo via observeCached() nesse caso. */
    suspend fun refresh(): Boolean {
        val tokens = tokenStore.current() ?: return false
        var (accessToken, refreshToken) = tokens
        return try {
            var response = NetworkModule.mobileApi.dashboard(DashboardRequest(accessToken, refreshToken))
            // Access token de vida curta (~1h) pode ter expirado -- antes,
            // essa tela so caia pro estado "offline" nesse caso mesmo com o
            // aparelho online (Task #37); agora tenta renovar (refresh_token,
            // vida bem mais longa) e refazer a chamada UMA vez, mesma logica
            // ja usada na fila de sincronizacao (ver TokenRefresher).
            if (response.code() == 401) {
                val newAccess = TokenRefresher.refreshAccessToken(tokenStore, refreshToken)
                if (newAccess != null) {
                    accessToken = newAccess
                    response = NetworkModule.mobileApi.dashboard(DashboardRequest(accessToken, refreshToken))
                }
            }
            val body = response.body()
            if (!response.isSuccessful || body?.ok != true || body.dashboard == null) return false

            val d = body.dashboard
            db.dashboardDao().upsert(
                DashboardEntity(
                    orgName = d.orgName,
                    saldoFinanceiroAberto = d.saldoFinanceiroAberto,
                    itensEstoque = d.itensEstoque,
                    safrasAtivas = d.safrasAtivas,
                    colaboradoresAtivos = d.colaboradoresAtivos,
                    culturaLider = d.culturaLider,
                    pedidosAtrasados = d.pedidosAtrasados,
                    alertsCount = d.alertsCount,
                    atualizadoEmMillis = System.currentTimeMillis(),
                )
            )
            true
        } catch (e: Exception) {
            false
        }
    }
}
