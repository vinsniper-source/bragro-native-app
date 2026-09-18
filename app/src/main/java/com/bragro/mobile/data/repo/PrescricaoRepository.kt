package com.bragro.mobile.data.repo

import android.content.Context
import com.bragro.mobile.data.AppLog
import com.bragro.mobile.data.TokenStore
import com.bragro.mobile.data.model.PrescricaoFarmDto
import com.bragro.mobile.data.model.PrescricaoFarmsRequest
import com.bragro.mobile.data.model.PrescricaoFeatureInput
import com.bragro.mobile.data.model.PrescricaoRequest
import com.bragro.mobile.data.model.PrescricaoResponse
import com.bragro.mobile.data.model.PrescricaoSalvarRequest
import com.bragro.mobile.data.remote.NetworkModule

/** Prescrição / Taxa Variável -- busca em /api/mobile/prescricao, que
 * reaproveita 100% os registros já salvos (savePrescricaoAction já resolve
 * taxaMedia/Min/Max e grava o geojson). DE PROPÓSITO SEM CACHE no Room
 * (mesmo critério de ChartsRepository/ReconciliacaoEstoqueRepository).
 *
 * A partir da v1.2.85 (Task #651, pedido do usuário "crie no native como
 * foi criado na plataforma") o repositório também CRIA prescrições
 * (listFarms/salvar) -- só a partir de ISO-XML importado no aparelho (ver
 * IsoXmlVraParser.kt); SHP continua exclusivo do site. */
class PrescricaoRepository(context: Context) {
    private val tokenStore = TokenStore(context)

    suspend fun fetch(): PrescricaoResponse? {
        val tokens = tokenStore.current() ?: return null
        var (accessToken, refreshToken) = tokens
        return try {
            var response = NetworkModule.mobileApi.prescricao(PrescricaoRequest(accessToken, refreshToken))
            if (response.code() == 401) {
                val newAccess = TokenRefresher.refreshAccessToken(tokenStore, refreshToken)
                if (newAccess != null) {
                    accessToken = newAccess
                    response = NetworkModule.mobileApi.prescricao(PrescricaoRequest(accessToken, refreshToken))
                }
            }
            val body = response.body()
            if (!response.isSuccessful || body?.ok != true) null else body
        } catch (e: Exception) {
            AppLog.e("PrescricaoRepository", "Falha ao buscar prescrições de taxa variável", e)
            null
        }
    }

    suspend fun listFarms(): List<PrescricaoFarmDto> {
        val tokens = tokenStore.current() ?: return emptyList()
        var (accessToken, refreshToken) = tokens
        return try {
            var response = NetworkModule.mobileApi.prescricaoFarms(PrescricaoFarmsRequest(accessToken, refreshToken, action = "farms"))
            if (response.code() == 401) {
                val newAccess = TokenRefresher.refreshAccessToken(tokenStore, refreshToken)
                if (newAccess != null) {
                    accessToken = newAccess
                    response = NetworkModule.mobileApi.prescricaoFarms(PrescricaoFarmsRequest(accessToken, refreshToken, action = "farms"))
                }
            }
            val body = response.body()
            if (!response.isSuccessful || body?.ok != true) emptyList() else body.farms
        } catch (e: Exception) {
            AppLog.e("PrescricaoRepository", "Falha ao buscar fazendas para vínculo da prescrição", e)
            emptyList()
        }
    }

    /** Cria uma prescrição -- devolve o id criado, ou null em caso de falha
     * (erro de rede ou de validação, ver "erro" no retorno do servidor). */
    suspend fun salvar(
        nome: String,
        produto: String?,
        unidadeTaxa: String?,
        safra: String?,
        cultura: String?,
        talhao: String?,
        farmId: String?,
        origemTipo: String,
        origemArquivo: String?,
        features: List<PrescricaoFeatureInput>,
    ): Result<String> {
        val tokens = tokenStore.current() ?: return Result.failure(Exception("Sessão expirada."))
        var (accessToken, refreshToken) = tokens
        fun montarBody(token: String) = PrescricaoSalvarRequest(
            accessToken = token,
            refreshToken = refreshToken,
            action = "salvar",
            nome = nome,
            produto = produto,
            unidadeTaxa = unidadeTaxa,
            safra = safra,
            cultura = cultura,
            talhao = talhao,
            farmId = farmId,
            origemTipo = origemTipo,
            origemArquivo = origemArquivo,
            features = features,
        )
        return try {
            var response = NetworkModule.mobileApi.prescricaoSalvar(montarBody(accessToken))
            if (response.code() == 401) {
                val newAccess = TokenRefresher.refreshAccessToken(tokenStore, refreshToken)
                if (newAccess != null) {
                    accessToken = newAccess
                    response = NetworkModule.mobileApi.prescricaoSalvar(montarBody(accessToken))
                }
            }
            val body = response.body()
            if (response.isSuccessful && body?.ok == true && body.id != null) {
                Result.success(body.id)
            } else {
                Result.failure(Exception(body?.error ?: "Erro ao salvar prescrição."))
            }
        } catch (e: Exception) {
            AppLog.e("PrescricaoRepository", "Falha ao salvar prescrição", e)
            Result.failure(e)
        }
    }
}
