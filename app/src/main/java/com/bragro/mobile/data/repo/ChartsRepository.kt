package com.bragro.mobile.data.repo

import android.content.Context
import com.bragro.mobile.data.AppLog
import com.bragro.mobile.data.TokenStore
import com.bragro.mobile.data.model.ModuleActionRequest
import com.bragro.mobile.data.model.ModuleChartsRequest
import com.bragro.mobile.data.model.ModuleChartsResponse
import com.bragro.mobile.data.remote.NetworkModule
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.booleanOrNull

/** Bloco "Gráficos" (Task de réplica completa dos módulos) -- busca em
 * /api/mobile/module-charts, que reaproveita getModuleChartData()/
 * getExtraModuleChartsAction() do site. De propósito SEM cache no Room
 * (mesmo critério de WeatherRepository/HomeRepository): um gráfico
 * desatualizado seria mais confuso que útil, e esses dados só importam
 * enquanto a tela do módulo está aberta -- se não tiver conexão, o bloco
 * simplesmente fica vazio/oculto em vez de mostrar um retrato velho. */
class ChartsRepository(context: Context) {
    private val tokenStore = TokenStore(context)

    suspend fun fetch(domainId: String, safra: String? = null): ModuleChartsResponse? {
        val tokens = tokenStore.current() ?: return null
        var (accessToken, refreshToken) = tokens
        return try {
            var response = NetworkModule.mobileApi.moduleCharts(ModuleChartsRequest(accessToken, refreshToken, domainId, safra))
            if (response.code() == 401) {
                val newAccess = TokenRefresher.refreshAccessToken(tokenStore, refreshToken)
                if (newAccess != null) {
                    accessToken = newAccess
                    response = NetworkModule.mobileApi.moduleCharts(ModuleChartsRequest(accessToken, refreshToken, domainId, safra))
                }
            }
            val body = response.body()
            if (!response.isSuccessful || body?.ok != true) null else body
        } catch (e: Exception) {
            AppLog.e("ChartsRepository", "Falha ao buscar gráficos do módulo domainId=$domainId (safra=$safra)", e)
            null
        }
    }
}

/** Botões sem formulário (Recalcular Vencimentos/Área, Eficiência de Frota)
 * -- busca/dispara em /api/mobile/module-actions. */
class ModuleActionsRepository(context: Context) {
    private val tokenStore = TokenStore(context)

    // Campos extras opcionais -- só usados pelo Controle de Estoque por
    // Fazenda ("estoque-transferir"/"estoque-devolver"/"estoque-saida"/
    // "estoque-ajuste"); as demais actions (recalcular-*, fleet-efficiency)
    // chamam run(action) sem eles.
    suspend fun run(
        action: String,
        item: String? = null,
        unidade: String? = null,
        quantidade: Double? = null,
        fazendaOrigemId: String? = null,
        fazendaDestinoId: String? = null,
        transferenciaEntradaId: String? = null,
        motivo: String? = null,
        tipo: String? = null,
        // Só usado por "preview-next-os" -- ver ModuleActionRequest.domainId.
        domainId: String? = null,
        // Também só usado por "preview-next-os" -- valor atual do campo
        // Local/Fazenda no formulário, pra calcular o próximo número de O.S.
        // NA SEQUÊNCIA DAQUELA FAZENDA (pedido do usuário: "uma sequência
        // por fazenda"), mesmo parâmetro que o site já manda (ver
        // record-form.tsx -> previewNextOsAction).
        local: String? = null,
    ): JsonObject? {
        val tokens = tokenStore.current() ?: return null
        var (accessToken, refreshToken) = tokens
        fun buildRequest(token: String) = ModuleActionRequest(
            token, refreshToken, action,
            item = item, unidade = unidade, quantidade = quantidade,
            fazendaOrigemId = fazendaOrigemId, fazendaDestinoId = fazendaDestinoId,
            transferenciaEntradaId = transferenciaEntradaId,
            motivo = motivo, tipo = tipo, domainId = domainId, local = local,
        )
        return try {
            var response = NetworkModule.mobileApi.moduleActions(buildRequest(accessToken))
            if (response.code() == 401) {
                val newAccess = TokenRefresher.refreshAccessToken(tokenStore, refreshToken)
                if (newAccess != null) {
                    accessToken = newAccess
                    response = NetworkModule.mobileApi.moduleActions(buildRequest(accessToken))
                }
            }
            val body = response.body()
            if (!response.isSuccessful || body?.ok != true) null else body.result
        } catch (e: Exception) {
            AppLog.e("ModuleActionsRepository", "Falha ao executar ação de módulo '$action'", e)
            null
        }
    }

    /** "Emitir NFS-e" (varredura de auditoria, pedido do usuário "implemente
     * tudo") -- réplica mobile de onEmitirNfse() em data-table.tsx (site).
     * Função própria (em vez de reaproveitar run()) porque aqui a MENSAGEM
     * de erro/sucesso do resultado importa pro usuário (run() descarta o
     * campo "error"/"mensagem" no caminho de falha, ok pras demais actions
     * que só mostram "sucesso ou não", mas não pra esta). Duas camadas de
     * "falha" possíveis, mesmo comportamento do servidor
     * (module-actions/route.ts -> emitirNfseAction):
     * 1) erro de validação (ex.: "Informe o Valor antes de emitir") -> HTTP
     *    não-2xx, corpo com "error" (ModuleActionResponse.error).
     * 2) chamada OK, mas a prefeitura/SEFAZ recusou ou ainda está
     *    processando -> HTTP 2xx com result.ok=false e result.mensagem
     *    explicando o motivo. */
    suspend fun emitirNfse(id: String): Pair<Boolean, String> {
        val tokens = tokenStore.current() ?: return false to "Sem sessão -- faça login novamente."
        var (accessToken, refreshToken) = tokens
        fun buildRequest(token: String) = ModuleActionRequest(token, refreshToken, "emitir-nfse", id = id)
        return try {
            var response = NetworkModule.mobileApi.moduleActions(buildRequest(accessToken))
            if (response.code() == 401) {
                val newAccess = TokenRefresher.refreshAccessToken(tokenStore, refreshToken)
                if (newAccess != null) {
                    accessToken = newAccess
                    response = NetworkModule.mobileApi.moduleActions(buildRequest(accessToken))
                }
            }
            val body = response.body()
            if (!response.isSuccessful || body == null) {
                return false to (body?.error ?: "Erro ao emitir NFS-e (código ${response.code()}).")
            }
            if (!body.ok) return false to (body.error ?: "Erro ao emitir NFS-e.")
            val result = body.result
            val ok = (result?.get("ok") as? JsonPrimitive)?.booleanOrNull ?: true
            val mensagem = (result?.get("mensagem") as? JsonPrimitive)?.contentOrNull ?: "NFS-e processada."
            ok to mensagem
        } catch (e: Exception) {
            AppLog.e("ModuleActionsRepository", "Falha ao emitir NFS-e (id=$id)", e)
            false to "Falha de conexão ao emitir NFS-e."
        }
    }
}
