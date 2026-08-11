package com.bragro.mobile.data.repo

import android.content.Context
import com.bragro.mobile.data.AppLog
import com.bragro.mobile.data.local.AppDatabase
import com.bragro.mobile.data.local.DomainConfigEntity
import com.bragro.mobile.data.local.FarmEntity
import com.bragro.mobile.data.local.LookupEntity
import com.bragro.mobile.data.local.SessionEntity
import com.bragro.mobile.data.model.BootstrapRequest
import com.bragro.mobile.data.model.DomainConfig
import com.bragro.mobile.data.remote.NetworkModule
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Configuracao dos 16 modulos (campos/tipos, ver /api/mobile/config) +
 * dados da organizacao (sessao, listas suspensas, fazendas, ver
 * /api/mobile/bootstrap) -- tudo cacheado no Room pra funcionar offline
 * depois da primeira sincronizacao online. */
class ConfigRepository(context: Context) {
    private val db = AppDatabase.get(context)
    private val json = Json { ignoreUnknownKeys = true }

    fun observeDomains(): Flow<List<DomainConfigEntity>> = db.domainConfigDao().observeAll()

    suspend fun domainConfig(domainId: String): DomainConfig? {
        val entity = db.domainConfigDao().byId(domainId) ?: return null
        return json.decodeFromString(DomainConfig.serializer(), entity.configJson)
    }

    suspend fun lookupsByCategory(category: String) = db.lookupDao().byCategory(category)

    suspend fun farms() = db.farmDao().all()

    /** Chamada apos login (ou manualmente em "Sincronizar agora") -- busca a
     * configuracao dos modulos + dados da organizacao e grava tudo no Room.
     * Retorna false se alguma das duas chamadas falhar (sem internet, token
     * expirado etc.) -- quem chamou decide o que fazer (ex.: no login,
     * bloquear; numa atualizacao manual, so avisar e manter o cache antigo).
     *
     * [onOrgResolved] (Task #124, isolamento de cache por organizacao) --
     * chamado logo que o orgId da organizacao recem-autenticada e conhecido,
     * ANTES de continuar o resto do bootstrap (session/lookups/farms). E o
     * unico ponto do fluxo em que quem chamou (AuthRepository.login()) fica
     * sabendo o orgId a tempo de decidir se limpa a fila de sincronizacao
     * pendente (organizacao diferente da ultima logada neste aparelho) antes
     * dela ser potencialmente usada. No-op por padrao pra nao quebrar
     * nenhuma outra chamada existente (ex.: "Sincronizar agora" manual, que
     * nunca precisou dessa decisao por so rodar com o usuario ja logado na
     * MESMA organizacao). */
    suspend fun bootstrapAndCacheConfig(
        accessToken: String,
        refreshToken: String,
        onOrgResolved: suspend (orgId: String) -> Unit = {},
    ): Boolean {
        return try {
            val configResponse = NetworkModule.mobileApi.config()
            val configBody = configResponse.body()
            if (!configResponse.isSuccessful || configBody?.ok != true) return false

            val bootstrapResponse = NetworkModule.mobileApi.bootstrap(BootstrapRequest(accessToken, refreshToken))
            val bootstrapBody = bootstrapResponse.body()
            if (!bootstrapResponse.isSuccessful || bootstrapBody?.ok != true || bootstrapBody.session == null) return false

            val session = bootstrapBody.session
            onOrgResolved(session.orgId)
            db.domainConfigDao().clearAll()
            db.domainConfigDao().insertAll(
                configBody.domains.map { d ->
                    DomainConfigEntity(domainId = d.id, label = d.label, configJson = json.encodeToString(DomainConfig.serializer(), d))
                }
            )

            db.sessionDao().upsert(
                SessionEntity(
                    userId = session.userId,
                    email = session.email,
                    orgId = session.orgId,
                    orgName = session.orgName,
                    orgLogoUrl = session.orgLogoUrl,
                    avatarUrl = session.avatarUrl,
                    role = session.role,
                    allowedModulesCsv = session.allowedModules.joinToString(","),
                    planTier = session.planTier,
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    atualizadoEm = System.currentTimeMillis(),
                )
            )

            db.lookupDao().clearAll()
            db.lookupDao().insertAll(bootstrapBody.lookups.map { LookupEntity(it.category, it.value, it.label, it.order) })

            db.farmDao().clearAll()
            db.farmDao().insertAll(bootstrapBody.farms.map { FarmEntity(name = it.name, areaHa = it.areaHa, id = it.id) })

            true
        } catch (e: Exception) {
            AppLog.e("ConfigRepository", "Falha ao executar bootstrap/config (login ou sincronizar agora)", e)
            false
        }
    }
}
