package com.bragro.mobile.data.repo

import android.content.Context
import com.bragro.mobile.BuildConfig
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

            val bootstrapOk = configRepository.bootstrapAndCacheConfig(body.accessToken, body.refreshToken)
            if (!bootstrapOk) {
                return LoginResult.Failure("Login feito, mas nao foi possivel carregar os dados da organizacao. Tente novamente.")
            }
            LoginResult.Success
        } catch (e: Exception) {
            // Antes reportava "sem conexão" pra QUALQUER excecao (senha
            // errada tratada em outro lugar, mas timeout/erro de servidor/
            // sessao cairiam aqui) -- agora confere a conectividade real do
            // aparelho (com.bragro.mobile.data.NetworkStatus) antes de
            // afirmar isso, pedido do usuario ("o app esta acusando sem
            // conexao mesmo com wifi e dados ligados").
            val msg = if (com.bragro.mobile.data.NetworkStatus.isOnline(context)) {
                "Não foi possível conectar ao servidor. Tente novamente em alguns instantes."
            } else {
                "Sem conexão com o servidor. Verifique sua internet e tente novamente."
            }
            LoginResult.Failure(msg)
        }
    }

    suspend fun isLoggedIn(): Boolean = db.sessionDao().get() != null

    suspend fun logout() {
        tokenStore.clear()
        db.sessionDao().clear()
        db.lookupDao().clearAll()
        db.farmDao().clearAll()
        // Registros e fila de sincronizacao pendente NAO sao apagados no
        // logout de proposito -- um lancamento feito offline nao pode se
        // perder so porque o usuario saiu da conta antes de reconectar.
        // Voltam a aparecer normalmente no proximo login (mesma conta).
    }
}
