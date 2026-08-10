package com.bragro.mobile.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Checagem REAL de conectividade do aparelho -- pedido do usuário ("o app
 * está acusando sem conexão mesmo com wifi e dados ligados"). Antes disso,
 * NENHUM lugar do app checava o `ConnectivityManager` de verdade (confirmado
 * por varredura): todo "offline"/"Sem conexão" era só uma inferência de "a
 * última chamada de rede falhou", e um catch genérico (`catch (e: Exception)`)
 * tratava IGUAL uma falha de rede real, um timeout, uma sessão expirada
 * (refresh_token inválido) e até um erro de parsing -- todos caíam na mesma
 * mensagem "Sem conexão", mesmo com o aparelho 100% online.
 *
 * `ACCESS_NETWORK_STATE` já estava declarado no AndroidManifest.xml mas
 * nunca tinha sido usado -- esta é a primeira vez que o app de fato pergunta
 * pro sistema "há uma rede com internet validada agora?" antes de decidir
 * qual mensagem mostrar.
 */
object NetworkStatus {
    fun isOnline(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } catch (e: Exception) {
            // Se a própria checagem falhar por algum motivo, não afirma nada
            // que possa estar errado -- assume "online" (deixa a chamada de
            // rede real decidir, em vez de bloquear a UI com um falso "sem
            // conexão" vindo da checagem em si).
            true
        }
    }

    /** Mensagem a mostrar quando uma sincronização/atualização falhou --
     * distingue "sem internet de verdade" de "tem internet, mas não
     * conseguiu falar com o servidor" (sessão expirada, servidor fora do
     * ar, etc.), que antes eram sempre reportados como "Sem conexão",
     * confundindo quem via a mensagem com wifi/dados ligados. */
    fun failureMessage(context: Context): String =
        if (isOnline(context)) {
            "Não foi possível conectar ao servidor -- mostrando o último resultado salvo neste aparelho. Tente novamente em alguns instantes."
        } else {
            "Sem conexão -- mostrando o último resultado salvo neste aparelho."
        }
}
