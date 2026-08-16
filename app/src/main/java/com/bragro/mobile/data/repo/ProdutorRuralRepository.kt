package com.bragro.mobile.data.repo

import android.content.Context
import com.bragro.mobile.data.AppLog
import com.bragro.mobile.data.TokenStore
import com.bragro.mobile.data.model.ProdutorRuralConfigData
import com.bragro.mobile.data.model.ProdutorRuralRequest
import com.bragro.mobile.data.remote.NetworkModule

/** Config do Produtor Rural / IRPF (Organization.cnpj/cpfProdutorRural/
 * inscricaoEstadualProdutor/certificadoDigitalRef/contaIrpfPadrao) pro app
 * nativo -- pedido do usuário ("implemente tudo que falta ainda para o app
 * native da plataforma"): esse card já existia no site
 * (produtor-rural-card.tsx) mas nunca tinha rota nem tela mobile. Sem cache
 * offline de propósito (mesmo critério do card Clima/Câmbio no Início): é
 * uma configuração da organização, não um dado transacional -- melhor
 * sempre buscar/gravar direto no servidor do que arriscar mostrar um valor
 * desatualizado ou perder uma edição feita por outro usuário/no site. */
class ProdutorRuralRepository(context: Context) {
    private val tokenStore = TokenStore(context)

    suspend fun fetch(): ProdutorRuralConfigData? = call(save = null)

    suspend fun save(config: ProdutorRuralConfigData): ProdutorRuralConfigData? = call(save = config)

    private suspend fun call(save: ProdutorRuralConfigData?): ProdutorRuralConfigData? {
        val tokens = tokenStore.current() ?: return null
        var (accessToken, refreshToken) = tokens
        return try {
            var response = NetworkModule.mobileApi.produtorRural(ProdutorRuralRequest(accessToken, refreshToken, save))
            if (response.code() == 401) {
                val newAccess = TokenRefresher.refreshAccessToken(tokenStore, refreshToken)
                if (newAccess != null) {
                    accessToken = newAccess
                    response = NetworkModule.mobileApi.produtorRural(ProdutorRuralRequest(accessToken, refreshToken, save))
                }
            }
            val body = response.body()
            if (!response.isSuccessful || body?.ok != true) return null
            body.config
        } catch (e: Exception) {
            AppLog.e("ProdutorRuralRepository", "Falha ao buscar/salvar dados do produtor rural", e)
            null
        }
    }
}
