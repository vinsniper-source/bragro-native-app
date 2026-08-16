package com.bragro.mobile.data.repo

import android.content.Context
import com.bragro.mobile.data.AppLog
import com.bragro.mobile.data.TokenStore
import com.bragro.mobile.data.local.AppDatabase
import com.bragro.mobile.data.local.LivroCaixaEntity
import com.bragro.mobile.data.model.LivroCaixaData
import com.bragro.mobile.data.model.LivroCaixaRequest
import com.bragro.mobile.data.remote.NetworkModule
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Livro Caixa do Produtor Rural (Task #58) -- busca em
 * /api/mobile/livro-caixa (reaproveita getLivroCaixaData() do site, ver
 * route.ts) e grava no Room (LivroCaixaEntity, um unico "retrato" da ultima
 * consulta) pra a tela conseguir abrir offline com o ultimo resultado
 * conhecido. Mesmo padrao de DreRepository. */
class LivroCaixaRepository(context: Context) {
    private val db = AppDatabase.get(context)
    private val tokenStore = TokenStore(context)
    private val json = Json { ignoreUnknownKeys = true }

    fun observeCached(): Flow<LivroCaixaEntity?> = db.livroCaixaDao().observe()

    fun parse(entity: LivroCaixaEntity): LivroCaixaData = json.decodeFromString(LivroCaixaData.serializer(), entity.dataJson)

    /** Retorna true se conseguiu atualizar do servidor; false se
     * offline/sem sessao (quem chamou continua podendo mostrar o cache
     * antigo via observeCached()). */
    suspend fun refresh(ano: Int, saldoInicial: Double, banco: String?, imovel: String? = null): Boolean {
        val tokens = tokenStore.current() ?: return false
        var (accessToken, refreshToken) = tokens
        return try {
            var response = NetworkModule.mobileApi.livroCaixa(LivroCaixaRequest(accessToken, refreshToken, ano, saldoInicial, banco, imovel))
            // Ver comentario equivalente em DashboardRepository (Task #37).
            if (response.code() == 401) {
                val newAccess = TokenRefresher.refreshAccessToken(tokenStore, refreshToken)
                if (newAccess != null) {
                    accessToken = newAccess
                    response = NetworkModule.mobileApi.livroCaixa(LivroCaixaRequest(accessToken, refreshToken, ano, saldoInicial, banco, imovel))
                }
            }
            val body = response.body()
            if (!response.isSuccessful || body?.ok != true || body.resultado == null) return false

            db.livroCaixaDao().upsert(
                LivroCaixaEntity(
                    ano = ano,
                    banco = banco,
                    dataJson = json.encodeToString(LivroCaixaData.serializer(), body.resultado),
                    atualizadoEmMillis = System.currentTimeMillis(),
                )
            )
            true
        } catch (e: Exception) {
            AppLog.e("LivroCaixaRepository", "Falha ao atualizar Livro Caixa (ano=$ano, banco=$banco)", e)
            false
        }
    }
}
