package com.bragro.mobile.data.repo

import android.content.Context
import com.bragro.mobile.data.TokenStore
import com.bragro.mobile.data.model.NfeImportRequest
import com.bragro.mobile.data.model.NfeImportedInvoiceData
import com.bragro.mobile.data.model.NfePreviewData
import com.bragro.mobile.data.model.NfePreviewRequest
import com.bragro.mobile.data.remote.NetworkModule

/** Importacao de XML de NF-e (Fase 2, Task #40) -- preview em
 * /api/mobile/nfe-preview e confirmacao em /api/mobile/nfe-import (ambas
 * reaproveitam previewXmlAction()/confirmXmlImportAction() do site, mesmo
 * parser e mesmo motor de rateio -- nada reescrito em Kotlin). Sem cache no
 * Room: e uma acao pontual (ler um arquivo e confirmar), nao uma leitura
 * recorrente como Dashboard/DRE/Analises. */
class NfeImportRepository(context: Context) {
    private val tokenStore = TokenStore(context)

    sealed class Result<out T> {
        data class Success<T>(val data: T) : Result<T>()
        data class Error(val message: String) : Result<Nothing>()
    }

    suspend fun preview(xmlRaw: String): Result<NfePreviewData> {
        val tokens = tokenStore.current() ?: return Result.Error("Sessão expirada. Faça login novamente.")
        var (accessToken, refreshToken) = tokens
        return try {
            var response = NetworkModule.mobileApi.nfePreview(NfePreviewRequest(accessToken, refreshToken, xmlRaw))
            if (response.code() == 401) {
                val newAccess = TokenRefresher.refreshAccessToken(tokenStore, refreshToken)
                if (newAccess != null) {
                    accessToken = newAccess
                    response = NetworkModule.mobileApi.nfePreview(NfePreviewRequest(accessToken, refreshToken, xmlRaw))
                }
            }
            val body = response.body()
            if (response.isSuccessful && body?.ok == true && body.preview != null) {
                Result.Success(body.preview)
            } else {
                Result.Error(body?.error ?: "Não foi possível ler o XML.")
            }
        } catch (e: Exception) {
            Result.Error("Sem conexão com o servidor.")
        }
    }

    suspend fun confirmImport(xmlRaw: String, fazendaDestino: String): Result<NfeImportedInvoiceData> {
        val tokens = tokenStore.current() ?: return Result.Error("Sessão expirada. Faça login novamente.")
        var (accessToken, refreshToken) = tokens
        return try {
            var response = NetworkModule.mobileApi.nfeImport(NfeImportRequest(accessToken, refreshToken, xmlRaw, fazendaDestino))
            if (response.code() == 401) {
                val newAccess = TokenRefresher.refreshAccessToken(tokenStore, refreshToken)
                if (newAccess != null) {
                    accessToken = newAccess
                    response = NetworkModule.mobileApi.nfeImport(NfeImportRequest(accessToken, refreshToken, xmlRaw, fazendaDestino))
                }
            }
            val body = response.body()
            if (response.isSuccessful && body?.ok == true && body.invoice != null) {
                Result.Success(body.invoice)
            } else {
                Result.Error(body?.error ?: "Não foi possível importar a nota.")
            }
        } catch (e: Exception) {
            Result.Error("Sem conexão com o servidor.")
        }
    }
}
