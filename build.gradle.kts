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
}
