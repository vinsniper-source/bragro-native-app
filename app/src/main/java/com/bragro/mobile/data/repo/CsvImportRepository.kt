package com.bragro.mobile.data.repo

import android.content.Context
import com.bragro.mobile.data.AppLog
import com.bragro.mobile.data.TokenStore
import com.bragro.mobile.data.model.CsvImportAnalyzeResponse
import com.bragro.mobile.data.model.CsvImportOptionsResponse
import com.bragro.mobile.data.model.CsvImportRequest
import com.bragro.mobile.data.model.CsvImportRunResponse
import com.bragro.mobile.data.remote.NetworkModule
import org.json.JSONObject
import retrofit2.Response

/** Resultado de uma chamada ao servidor: ou [data], ou [error] com a mensagem
 * (já em português, vinda do servidor quando possível) pra mostrar na tela. */
class CsvImportResult<T>(val data: T? = null, val error: String? = null)

/** Migração de Dados (CSV de outra plataforma) -- o parsing e a gravação
 * rodam no servidor (/api/mobile/csv-import, mesmas Server Actions do site);
 * aqui só o transporte. Live-only (sem cache no Room), igual BankImportRepository.
 * Só OWNER/ADMIN: o servidor devolve 403 com mensagem pra outros papéis. */
class CsvImportRepository(context: Context) {
    private val tokenStore = TokenStore(context)

    suspend fun listOptions(): CsvImportResult<CsvImportOptionsResponse> =
        call("list_options", { req -> NetworkModule.mobileApi.csvImportOptions(req) }) { it.ok }

    suspend fun analyze(domainId: String, csvText: String, platformId: String?): CsvImportResult<CsvImportAnalyzeResponse> =
        call(
            "analyze",
            { req -> NetworkModule.mobileApi.csvImportAnalyze(req) },
            domainId = domainId, csvText = csvText, platformId = platformId,
        ) { it.ok }

    suspend fun run(domainId: String, csvText: String, mapping: Map<String, String>): CsvImportResult<CsvImportRunResponse> =
        call(
            "run",
            { req -> NetworkModule.mobileApi.csvImportRun(req) },
            domainId = domainId, csvText = csvText, mapping = mapping,
        ) { it.ok }

    private suspend fun <T> call(
        action: String,
        exec: suspend (CsvImportRequest) -> Response<T>,
        domainId: String? = null,
        csvText: String? = null,
        platformId: String? = null,
        mapping: Map<String, String>? = null,
        isOk: (T) -> Boolean,
    ): CsvImportResult<T> {
        val tokens = tokenStore.current() ?: return CsvImportResult(error = "Sessão expirada. Entre novamente.")
        var (accessToken, refreshToken) = tokens
        return try {
            fun req() = CsvImportRequest(accessToken, refreshToken, action, domainId, csvText, platformId, mapping)
            var response = exec(req())
            if (response.code() == 401) {
                val newAccess = TokenRefresher.refreshAccessToken(tokenStore, refreshToken)
                if (newAccess != null) {
                    accessToken = newAccess
                    response = exec(req())
                }
            }
            val body = response.body()
            if (response.isSuccessful && body != null && isOk(body)) {
                CsvImportResult(data = body)
            } else {
                CsvImportResult(error = extractError(response) ?: "Não foi possível concluir a operação (código ${response.code()}).")
            }
        } catch (e: Exception) {
            AppLog.e("CsvImportRepository", "Falha na chamada csv-import (action=$action)", e)
            CsvImportResult(error = "Sem conexão com o servidor. Tente novamente.")
        }
    }

    /** O servidor responde `{ok:false, error:"..."}` nos erros -- Retrofit não
     * converte o corpo de respostas não-2xx, então lê o JSON na mão. */
    private fun extractError(response: Response<*>): String? = try {
        val raw = response.errorBody()?.string()
        if (raw.isNullOrBlank()) null else JSONObject(raw).optString("error").takeIf { it.isNotBlank() }
    } catch (e: Exception) {
        null
    }
}
