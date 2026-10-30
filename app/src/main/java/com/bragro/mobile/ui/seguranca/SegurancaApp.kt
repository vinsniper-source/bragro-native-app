package com.bragro.mobile.ui.seguranca

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.CancellationSignal
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.UUID

/**
 * v72b -- segurança do app neste celular (cada usuário escolhe):
 *  - bloqueio por digital/rosto (ou senha do celular) ao abrir depois de N
 *    minutos parado;
 *  - esconder telas do Financeiro e do Pix em prints e na lista de apps
 *    recentes (FLAG_SECURE);
 *  - id deste aparelho para "Meus dados e aparelhos".
 */
object SegurancaPrefs {
    private const val ARQ = "bragro_seguranca"
    private fun p(c: Context) = c.getSharedPreferences(ARQ, Context.MODE_PRIVATE)

    fun bloqueioAtivo(c: Context) = p(c).getBoolean("bloqueio", false)
    fun setBloqueioAtivo(c: Context, v: Boolean) = p(c).edit().putBoolean("bloqueio", v).apply()
    fun minutos(c: Context) = p(c).getInt("minutos", 5)
    fun setMinutos(c: Context, v: Int) = p(c).edit().putInt("minutos", v).apply()
    fun esconderPrints(c: Context) = p(c).getBoolean("prints", false)
    fun setEsconderPrints(c: Context, v: Boolean) = p(c).edit().putBoolean("prints", v).apply()
    fun ultimoFundo(c: Context) = p(c).getLong("fundo", 0L)
    fun setUltimoFundo(c: Context, v: Long) = p(c).edit().putLong("fundo", v).apply()
    fun ultimoHeartbeat(c: Context) = p(c).getLong("heartbeat", 0L)
    fun setUltimoHeartbeat(c: Context, v: Long) = p(c).edit().putLong("heartbeat", v).apply()
    /** Primeiro aviso depois de um login: libera o aparelho de novo no servidor. */
    fun aposLogin(c: Context) = p(c).getBoolean("aposLogin", false)
    fun setAposLogin(c: Context, v: Boolean) = p(c).edit().putBoolean("aposLogin", v).apply()
    fun idAparelho(c: Context): String {
        p(c).getString("aparelho", null)?.let { return it }
        val novo = UUID.randomUUID().toString().replace("-", "")
        p(c).edit().putString("aparelho", novo).apply()
        return novo
    }
}

object AppLock {
    val travado = mutableStateOf(false)

    /** Celular tem senha/padrão/digital cadastrado? Sem isso não dá para travar. */
    fun aparelhoProtegido(c: Context): Boolean =
        (c.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager)?.isDeviceSecure == true

    fun aoAbrir(c: Context, sessaoAtiva: Boolean) {
        if (sessaoAtiva && SegurancaPrefs.bloqueioAtivo(c) && aparelhoProtegido(c)) travado.value = true
    }

    fun aoSair(c: Context) = SegurancaPrefs.setUltimoFundo(c, System.currentTimeMillis())

    fun aoVoltar(c: Context, sessaoAtiva: Boolean) {
        if (!sessaoAtiva || !SegurancaPrefs.bloqueioAtivo(c) || !aparelhoProtegido(c)) return
        val parado = System.currentTimeMillis() - SegurancaPrefs.ultimoFundo(c)
        if (SegurancaPrefs.ultimoFundo(c) > 0 && parado >= SegurancaPrefs.minutos(c) * 60_000L) travado.value = true
    }
}

fun Context.atividade(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/** Pede a digital/rosto (ou a senha do celular). API 28+: BiometricPrompt do
 * sistema; abaixo disso, a tela de senha do próprio Android. */
fun pedirDesbloqueio(activity: Activity, pedirSenhaAntiga: () -> Unit, onOk: () -> Unit) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val builder = android.hardware.biometrics.BiometricPrompt.Builder(activity)
            .setTitle("Desbloquear o BRAgro")
            .setSubtitle("Use a digital, o rosto ou a senha do celular")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setAllowedAuthenticators(
                android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_WEAK or
                    android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            )
        } else {
            @Suppress("DEPRECATION")
            builder.setDeviceCredentialAllowed(true)
        }
        builder.build().authenticate(
            CancellationSignal(),
            activity.mainExecutor,
            object : android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: android.hardware.biometrics.BiometricPrompt.AuthenticationResult?) = onOk()
            },
        )
    } else {
        pedirSenhaAntiga()
    }
}

@Composable
fun TelaBloqueio(pedirSenhaAntiga: () -> Unit) {
    val context = LocalContext.current
    val desbloquear = {
        context.atividade()?.let { act -> pedirDesbloqueio(act, pedirSenhaAntiga) { AppLock.travado.value = false } }
        Unit
    }
    LaunchedEffect(Unit) { desbloquear() }
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("BRAgro", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            Box(
                Modifier.size(72.dp).border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Fingerprint, contentDescription = null, modifier = Modifier.size(38.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Text("Toque para desbloquear", style = MaterialTheme.typography.bodyMedium)
            Button(onClick = desbloquear, modifier = Modifier.padding(top = 4.dp)) { Text("Desbloquear") }
        }
    }
}

/** Coloque no começo das telas do Financeiro e do Pix: com a opção ligada,
 * a tela fica preta em prints, gravações e na lista de apps recentes. */
@Composable
fun ProtegerDePrints() {
    val context = LocalContext.current
    if (!SegurancaPrefs.esconderPrints(context)) return
    DisposableEffect(Unit) {
        val janela = context.atividade()?.window
        janela?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { janela?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}
