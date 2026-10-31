package com.bragro.mobile.data

/**
 * Ponto único de logging de exceções capturadas no app.
 *
 * `AppLog.e` sempre manda pro Logcat (`android.util.Log.e`, visível em
 * `adb logcat` / Android Studio) e, quando existe um `Throwable`, manda pro
 * registro de erros PRÓPRIO do BRAgro (v74, [ErroReporter] -- no lugar do
 * Firebase Crashlytics e do Sentry), que aparece no Painel do dono.
 */
object AppLog {
    fun e(tag: String, msg: String, err: Throwable? = null) {
        android.util.Log.e(tag, msg, err)
        if (err != null) runCatching { ErroReporter.registrar(tag, msg, err) }
    }

    fun w(tag: String, msg: String, err: Throwable? = null) {
        android.util.Log.w(tag, msg, err)
    }
}
