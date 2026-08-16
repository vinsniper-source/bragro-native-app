package com.bragro.mobile.data.repo

import android.content.Context
import com.bragro.mobile.data.AppLog
import com.bragro.mobile.data.TokenStore
import com.bragro.mobile.data.local.AppDatabase
import com.bragro.mobile.data.local.ControleInsumosEntity
import com.bragro.mobile.data.model.ControleInsumosRequest
import com.bragro.mobile.data.model.ControleInsumosResponse
import com.bragro.mobile.data.remote.NetworkModule
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Painel "Controle de Insumos" (gap encontrado na auditoria módulo-a-módulo
 * contra o site, pedido do usuário "implemente tudo que falta ainda para o
 * app native da plataforma") -- busca em /api/mobile/controle-insumos
 * (reaproveita as MESMAS funções de lib/services/insumos-arvore.ts que a
 * página /controle-de-insumos do site usa) e grava no Room
 * (ControleInsumosEntity, um único "retrato" da última consulta) pra a tela
 * conseguir abrir offline com o último resultado conhecido. Mesmo padrão de
 * DreRepository. Guarda o payload inteiro (situação consolidada + itens
 * críticos + árvore Controle Interno + saldo top-10) num único blob JSON. */
class ControleInsumosRepository(context: Context) {
    private val db = AppDatabase.get(context)
    private val tokenStore = TokenStore(context)
    private val json = Json { ignoreUnknownKeys = true }

    fun observeCached(): Flow<ControleInsumosEntity?> = db.controleInsumosDao().observe()

    fun parse(entity: ControleInsumosEntity): ControleInsumosResponse =
        json.decodeFromString(ControleInsumosResponse.serializer(), entity.dataJson)

    /** Retorna true se conseguiu atualizar do servidor; false se
     * offline/sem sessao (quem chamou continua podendo mostrar o cache
     * antigo via observeCached()). */
    suspend fun refresh(safra: String?): Boolean {
        val tokens = tokenStore.current() ?: return false
        var (accessToken, refreshToken) = tokens
        return try {
            var response = NetworkModule.mobileApi.controleInsumos(ControleInsumosRequest(accessToken, refreshToken, safra))
            if (response.code() == 401) {
                val newAccess = TokenRefresher.refreshAccessToken(tokenStore, refreshToken)
                if (newAccess != null) {
                    accessToken = newAccess
                    response = NetworkModule.mobileApi.controleInsumos(ControleInsumosRequest(accessToken, refreshToken, safra))
                }
            }
            val body = response.body()
            if (!response.isSuccessful || body?.ok != true) return false

            db.controleInsumosDao().upsert(
                ControleInsumosEntity(
                    safra = safra,
                    dataJson = json.encodeToString(ControleInsumosResponse.serializer(), body),
                    atualizadoEmMillis = System.currentTimeMillis(),
                )
            )
            true
        } catch (e: Exception) {
            AppLog.e("ControleInsumosRepository", "Falha ao atualizar Controle de Insumos (safra=$safra)", e)
            false
        }
    }
}
