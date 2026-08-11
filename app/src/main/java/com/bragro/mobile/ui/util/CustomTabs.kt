package com.bragro.mobile.ui.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import com.bragro.mobile.data.AppLog

/** Abre uma URL numa Custom Tab (Chrome) em vez de um Intent.ACTION_VIEW
 * genérico -- usado tanto pelo botão Módulos (BottomNavBar.kt) quanto pelo
 * link "Esqueci minha senha" do login. Um Intent.ACTION_VIEW comum passa
 * pela resolução de "App Links" do Android, que pode entregar a URL pra
 * QUALQUER app instalado com esse domínio verificado (inclusive um app
 * antigo/PWA do próprio site, se ainda estiver instalado -- pedido do
 * usuário: "não quero que seja redirecionado para o app anterior").
 * CustomTabsIntent abre direto no navegador, sem passar por essa resolução.
 * Se nenhum navegador com suporte a Custom Tabs estiver disponível
 * (raríssimo -- todo Android tem Chrome), cai pro Intent.ACTION_VIEW comum. */
fun openInCustomTab(context: Context, url: String) {
    try {
        CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url))
    } catch (e: Exception) {
        AppLog.e("CustomTabs", "Falha ao abrir Custom Tab pra url=$url -- usando Intent.ACTION_VIEW de fallback", e)
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
