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
}
