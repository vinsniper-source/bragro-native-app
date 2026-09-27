// Projeto raiz -- app nativo Android do BRAgro (Task #376), independente da
// plataforma web (Next.js) e do wrapper Capacitor antigo em mobile-app/.
// Kotlin puro + Jetpack Compose, sem nenhum arquivo compartilhado com o
// site: fala com o MESMO backend (Supabase + rotas /api/mobile/* do site,
// ja publicado em https://sistema-agro-bra.vercel.app) via HTTP comum, mas
// o codigo em si e 100% proprio.
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    id("com.google.devtools.ksp") version "1.9.24-1.0.20" apply false
    // Fase 3: relato de erros (Crashlytics). Declarado aqui (apply false) so
    // pra disponibilizar a versao do plugin -- so e de fato aplicado em
    // app/build.gradle.kts, e so QUANDO existir um app/google-services.json
    // de verdade (arquivo que so existe depois que voce criar um projeto no
    // Firebase, ver README). Sem esse arquivo, nada aqui e ativado -- o
    // build continua normal, so sem relato de erros.
    id("com.google.gms.google-services") version "4.4.2" apply false
    // Plugin do Crashlytics em si (Task #749 -- CAUSA RAIZ do crash-ao-abrir
    // reportado pelo usuario na v1.2.97/v1.2.98). A dependencia
    // "firebase-crashlytics" (app/build.gradle.kts) SO funciona em runtime
    // se este plugin Gradle tiver rodado durante o build: e ele quem gera o
    // "Crashlytics build ID" (um recurso/arquivo de mapeamento) que o SDK
    // exige na inicializacao. A v1.2.97 adicionou a dependencia mas
    // esqueceu de aplicar este plugin -- resultado: FirebaseCrashlytics.init()
    // lanca IllegalStateException("The Crashlytics build ID is missing...")
    // dentro do FirebaseInitProvider, que roda ANTES do Application.onCreate,
    // derrubando o app com RuntimeException antes de qualquer tela (login
    // incluso). Confirmado via adb logcat: FATAL EXCEPTION citando
    // exatamente essa mensagem. NAO tinha nenhuma relacao com R8/minify
    // (por isso desliga-los em v1.2.98 nao resolveu).
    id("com.google.firebase.crashlytics") version "3.0.2" apply false
}
