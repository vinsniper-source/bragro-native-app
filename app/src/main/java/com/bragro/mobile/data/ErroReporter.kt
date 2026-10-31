package com.bragro.mobile.data

import android.content.Context
import android.os.Build
import com.bragro.mobile.BuildConfig
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * v74 -- registro de erros PRÓPRIO do app (no lugar do Sentry e do Firebase
 * Crashlytics). Vai para /api/public/erros da plataforma, tabela
 * error_events, e aparece no Painel do dono.
 *
 *  - Travamento (crash): gravado num arquivo NA HORA (o app está morrendo,
 *    não dá para usar a rede) e enviado na próxima abertura.
 *  - Erro tratado (AppLog.e com exceção): enviado em segundo plano, no
 *    máximo 10 por sessão e sem repetir; falhas de rede da fazenda
 *    (sem sinal, tempo esgotado) não são enviadas, para não virar ruído.
 *  - Só em build de release; nada de dados pessoais (o servidor ainda
 *    limpa e-mail, CPF, CNPJ, telefone e valores antes de gravar).
 */
object ErroReporter {
    private const val PASTA = "erros_pendentes"
    private val fila = Executors.newSingleThreadExecutor()
    private val enviadosNaSessao = HashSet<String>()
    @Volatile private var appContext: Context? = null

    fun instalar(context: Context) {
        appContext = context.applicationContext
        if (BuildConfig.DEBUG) return
        val anterior = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, err ->
            runCatching { salvarEmArquivo(montar(err, "Travamento em ${thread.name}", fatal = true)) }
            anterior?.uncaughtException(thread, err)
        }
        fila.execute { enviarPendentes() }
    }

    /** Chamado por AppLog.e quando há uma exceção. */
    fun registrar(tag: String, msg: String, err: Throwable) {
        if (BuildConfig.DEBUG || appContext == null) return
        if (ehFalhaDeRede(err)) return
        val chave = "${err.javaClass.name}:${err.message?.take(120)}"
        synchronized(enviadosNaSessao) {
            if (enviadosNaSessao.size >= 10 || !enviadosNaSessao.add(chave)) return
        }
        val json = montar(err, "$tag: $msg", fatal = false)
        fila.execute { if (!enviar(json)) runCatching { salvarEmArquivo(json) } }
    }

    private fun ehFalhaDeRede(err: Throwable): Boolean {
        var e: Throwable? = err
        var n = 0
        while (e != null && n < 5) {
            if (e is IOException) return true
            e = e.cause
            n++
        }
        return false
    }

    private fun montar(err: Throwable, local: String, fatal: Boolean): JSONObject = JSONObject().apply {
        put("origem", "app")
        put("mensagem", "${err.javaClass.simpleName}: ${err.message ?: "(sem mensagem)"}".take(1000))
        put("pilha", err.stackTraceToString().take(8000))
        put("local", local.take(300))
        put("appVersao", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        put("aparelho", "${Build.MANUFACTURER} ${Build.MODEL}".take(120))
        put("androidVersao", "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        put("fatal", fatal)
    }

    private fun pasta(): File? = appContext?.let { File(it.filesDir, PASTA).apply { mkdirs() } }

    private fun salvarEmArquivo(json: JSONObject) {
        val p = pasta() ?: return
        if ((p.listFiles()?.size ?: 0) >= 20) return
        File(p, "${System.currentTimeMillis()}.json").writeText(json.toString())
    }

    private fun enviarPendentes() {
        val arquivos = pasta()?.listFiles()?.sortedBy { it.name } ?: return
        for (f in arquivos) {
            val ok = runCatching { enviar(JSONObject(f.readText())) }.getOrDefault(false)
            if (ok) f.delete() else break // sem rede: tenta na próxima abertura
        }
    }

    /** true = o servidor recebeu (ou recusou de vez); false = tentar depois. */
    private fun enviar(json: JSONObject): Boolean {
        return try {
            val con = (URL("${BuildConfig.API_BASE_URL}/api/public/erros").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 10_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
            con.outputStream.use { it.write(json.toString().toByteArray(Charsets.UTF_8)) }
            val code = con.responseCode
            con.disconnect()
            code in 200..499 && code != 429
        } catch (_: Exception) {
            false
        }
    }
}
