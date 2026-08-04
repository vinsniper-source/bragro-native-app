package com.bragro.mobile.data.repo

import android.content.Context
import com.bragro.mobile.data.TokenStore
import com.bragro.mobile.data.model.BankImportConfirmRequest
import com.bragro.mobile.data.model.BankImportRowDto
import com.bragro.mobile.data.model.BankImportSignaturesRequest
import com.bragro.mobile.data.remote.NetworkModule

/** Extrato bancário (aba "Extrato" dentro de Financeiro) -- o parsing do CSV
 * roda no aparelho (BankImportParser.kt); aqui só a dedup (signatures) e a
 * gravação (confirm), únicas partes que precisam do servidor. Live-only
 * (sem cache no Room), mesmo critério de ChartsRepository/ModuleActionsRepository:
 * sem conexão, a tela simplesmente avisa e não deixa importar. */
class BankImportRepository(context: Context) {
    private val tokenStore = TokenStore(context)

    suspend fun signatures(banco: String): List<String>? {
        val tokens = tokenStore.current() ?: return null
        var (accessToken, refreshToken) = tokens
        return try {
            var response = NetworkModule.mobileApi.bankImportSignatures(BankImportSignaturesRequest(accessToken, refreshToken, banco = banco))
            if (response.code() == 401) {
                val newAccess = TokenRefresher.refreshAccessToken(tokenStore, refreshToken)
                if (newAccess != null) {
                    accessToken = newAccess
                    response = NetworkModule.mobileApi.bankImportSignatures(BankImportSignaturesRequest(accessToken, refreshToken, banco = banco))
                }
            }
            val body = response.body()
            if (!response.isSuccessful || body?.ok != true) null else body.signatures
        } catch (e: Exception) {
            null
        }
    }

    /** Retorna quantos lançamentos foram gravados, ou null em caso de falha
     * (sem conexão/sessão/erro do servidor). */
    suspend fun confirm(banco: String, rows: List<BankImportRowDto>): Int? {
        val tokens = tokenStore.current() ?: return null
        var (accessToken, refreshToken) = tokens
        return try {
            var response = NetworkModule.mobileApi.bankImportConfirm(BankImportConfirmRequest(accessToken, refreshToken, banco = banco, rows = rows))
            if (response.code() == 401) {
                val newAccess = TokenRefresher.refreshAccessToken(tokenStore, refreshToken)
                if (newAccess != null) {
                    accessToken = newAccess
                    response = NetworkModule.mobileApi.bankImportConfirm(BankImportConfirmRequest(accessToken, refreshToken, banco = banco, rows = rows))
                }
            }
            val body = response.body()
            if (!response.isSuccessful || body?.ok != true) null else body.imported
        } catch (e: Exception) {
            null
        }
    }
}
