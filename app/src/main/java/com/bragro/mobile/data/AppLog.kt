package com.bragro.mobile.data

/**
 * Ponto único de logging de exceções capturadas no app.
 *
 * Antes disso, TODO `catch (e: Exception)` do projeto engolia o erro em
 * silêncio -- nem Logcat, nem Crashlytics (que só registra crash NÃO
 * tratado, nunca uma exceção capturada dentro de um catch). Resultado: um
 * usuário relatando "meu lançamento não aparece" ou "deu erro ao
 * sincronizar" não deixava rastro nenhum pra diagnosticar se foi timeout,
 * erro de parsing, token expirado, etc.
 *
 * `AppLog.e` sempre manda pro Logcat (`android.util.Log.e`, visível em
 * `adb logcat` / Android Studio, útil em debug local) e, quando existir um
 * `Throwable`, tenta também mandar pro Firebase Crashlytics
 * (`recordException`), que agrega e reporta remotamente -- é o que permite
 * diagnosticar um bug relatado por um usuário sem precisar reproduzir
 * localmente.
 *
 * A dependência do Gradle (`com.google.firebase:firebase-crashlytics`, ver
 * app/build.gradle.kts) é INCONDICIONAL -- sempre linkada, em qualquer
 * build -- então a chamada a `FirebaseCrashlytics.getInstance()` sempre
 * COMPILA. Só a INICIALIZAÇÃO em runtime é condicional (depende de existir
 * um `google-services.json` de verdade e o plugin `com.google.gms.
 * google-services` ter sido aplicado). Sem esse arquivo, `getInstance()`
 * pode lançar (`FirebaseApp` não inicializado) -- por isso a chamada fica
 * dentro de um `runCatching`: se o Crashlytics não estiver configurado
 * neste build/aparelho, essa falha é apenas engolida, o log do Logcat já
 * foi emitido antes e o app nunca cai por causa do próprio logging.
 */
object AppLog {
    fun e(tag: String, msg: String, err: Throwable? = null) {
        android.util.Log.e(tag, msg, err)
        if (err != null) {
            runCatching {
                com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().recordException(err)
            }
        }
    }

    fun w(tag: String, msg: String, err: Throwable? = null) {
        android.util.Log.w(tag, msg, err)
    }
}
