package com.bragro.mobile.data.repo

import android.content.Context
import com.bragro.mobile.BuildConfig
import com.bragro.mobile.data.AppLog
import com.bragro.mobile.data.TokenStore
import com.bragro.mobile.data.local.AppDatabase
import com.bragro.mobile.data.model.SupabaseLoginRequest
import com.bragro.mobile.data.remote.NetworkModule

sealed class LoginResult {
    data object Success : LoginResult()
    data class Failure(val message: String) : LoginResult()
}

/** Login/logout -- fala DIRETO com o Supabase Auth (REST), sem passar pelo
 * site. Depois de logar, chama ConfigRepository.bootstrap() pra baixar tudo
 * que o app precisa cachear (org, papel, modulos liberados, listas
 * suspensas, fazendas) antes de mostrar a tela inicial. */
class AuthRepository(private val context: Context) {
    private val tokenStore = TokenStore(context)
    private val configRepository = ConfigRepository(context)
    private val db = AppDatabase.get(context)
    private val vault = com.bragro.mobile.data.OfflineVault(context)

    suspend fun login(email: String, password: String): LoginResult {
        return try {
            val response = NetworkModule.supabaseAuthApi.login(
                grantType = "password",
                apiKey = BuildConfig.SUPABASE_ANON_KEY,
                body = SupabaseLoginRequest(email, password),
            )
            val body = response.body()
            if (!response.isSuccessful || body?.accessToken == null || body.refreshToken == null) {
                val msg = body?.errorDescription ?: body?.msg ?: "E-mail ou senha invalidos."
                return LoginResult.Failure(msg)
            }
            tokenStore.save(body.accessToken, body.refreshToken, email)

            // Task #124 (isolamento de cache por organizacao) -- assim que o
            // bootstrap souber o orgId recem-autenticado (mas ANTES dele
            // continuar e sobrescrever session/lookups/farms), compara com o
            // ultimo orgId conhecido neste aparelho (TokenStore.getLastOrgId,
            // sobrevive a logout de proposito). Organizacao DIFERENTE = outro
            // usuario/org logando no mesmo aparelho (comum em campo, celular
            // compartilhado) -- limpa records/pending_sync ANTES de deixar o
            // bootstrap seguir, senao a fila pendente da organizacao antiga
            // ficaria disponivel pro SyncWorker sincronizar contra a
            // organizacao nova. Organizacao IGUAL (ou nenhuma anterior
            // conhecida, ou seja, primeiro login neste aparelho) = nao faz
            // nada, preserva a fila exatamente como hoje.
            val bootstrapOk = configRepository.bootstrapAndCacheConfig(body.accessToken, body.refreshToken) { newOrgId ->
                val lastOrgId = tokenStore.getLastOrgId()
                if (lastOrgId != null && lastOrgId != newOrgId) {
                    db.recordDao().clearAll()
                    db.pendingSyncDao().clearAll()
                    // Filtro global de fazenda (ver FarmSelection.kt) tambem
                    // e por organizacao -- uma fazenda escolhida na org
                    // anterior nao deve continuar filtrando os modulos da
                    // org nova que acabou de logar neste mesmo aparelho.
                    com.bragro.mobile.ui.domain.FarmSelection.clear(context)
                }
                tokenStore.setLastOrgId(newOrgId)
            }
            if (!bootstrapOk) {
                return LoginResult.Failure("Login feito, mas nao foi possivel carregar os dados da organizacao. Tente novamente.")
            }
            // Pre-carrega os registros de todos os modulos pro uso offline
            // (pedido do usuario: o app nao precisa ter sido aberto em cada
            // modulo antes da conexao cair).
            com.bragro.mobile.sync.PrefetchWorker.enqueue(context)
            // Cofre de login offline (ver OfflineVault): permite entrar de novo
            // sem internet neste aparelho, mesmo depois de sair da conta.
            db.sessionDao().get()?.let { vault.save(email, password, it) }
            LoginResult.Success
        } catch (e: Exception) {
            AppLog.e("AuthRepository", "Falha ao fazer login/bootstrap para email=$email", e)
            // Antes reportava "sem conexão" pra QUALQUER excecao (senha
            // errada tratada em outro lugar, mas timeout/erro de servidor/
            // sessao cairiam aqui) -- agora confere a conectividade real do
            // aparelho (com.bragro.mobile.data.NetworkStatus) antes de
            // afirmar isso, pedido do usuario ("o app esta acusando sem
            // conexao mesmo com wifi e dados ligados").
            if (!com.bragro.mobile.data.NetworkStatus.isOnline(context)) {
                val offline = tryOfflineLogin(email, password)
                if (offline != null) return offline
            }
            val msg = if (com.bragro.mobile.data.NetworkStatus.isOnline(context)) {
                "Não foi possível conectar ao servidor. Tente novamente em alguns instantes."
            } else {
                "Sem conexão com o servidor. Verifique sua internet e tente novamente."
            }
            LoginResult.Failure(msg)
        }
    }

    /** Login SEM internet: confere e-mail+senha contra o cofre (guardado no
     * ultimo login online deste aparelho) e, se bater, restaura a sessao
     * local -- os dados em cache (registros, listas, fazendas) continuam no
     * aparelho. Devolve null se nao ha cofre / senha nao confere (quem chamou
     * mostra o erro normal de "sem conexao"). Os tokens restaurados podem
     * estar vencidos: sao renovados sozinhos quando a internet voltar, e a
     * fila de lancamentos offline sincroniza nessa hora. */
    private suspend fun tryOfflineLogin(email: String, password: String): LoginResult? {
        val session = vault.verify(email, password) ?: return null
        tokenStore.save(session.accessToken, session.refreshToken, session.email)
        tokenStore.setLastOrgId(session.orgId)
        db.sessionDao().upsert(session)
        return LoginResult.Success
    }

    suspend fun isLoggedIn(): Boolean = db.sessionDao().get() != null

    suspend fun logout() {
        tokenStore.clear()
        db.sessionDao().clear()
        // Listas suspensas e fazendas NAO sao apagadas no logout: o login
        // offline (OfflineVault) precisa delas pra reabrir o app sem
        // internet. O proximo login online reescreve ambas (bootstrap) e,
        // se a organizacao mudou, limpa o resto (ver login()).
        // Registros e fila de sincronizacao pendente NAO sao apagados AQUI
        // no logout de proposito -- um lancamento feito offline nao pode se
        // perder so porque o usuario saiu da conta antes de reconectar.
        // Voltam a aparecer normalmente no proximo login NA MESMA
        // organizacao (tokenStore.clear() preserva "last_org_id" de
        // proposito, ver TokenStore.kt).
        //
        // Task #124 -- essa regra passou a ter uma excecao: se o PROXIMO
        // login for de uma organizacao DIFERENTE (outro usuario/org no
        // mesmo aparelho, comum em campo com celular compartilhado),
        // login() (acima) limpa records/pending_sync ali, ANTES do
        // bootstrap dessa organizacao nova continuar -- nao aqui no
        // logout, porque neste momento ainda nao sabemos qual vai ser a
        // PROXIMA organizacao a logar (pode ser a mesma, e nesse caso a
        // fila tem que sobreviver, exatamente como sempre foi).
    }
}
