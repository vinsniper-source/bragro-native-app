import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    kotlin("plugin.serialization") version "1.9.24"
}

// Fase 3: assinatura de release. O keystore em si (arquivo .jks/.keystore) e
// as senhas NUNCA vao pro git (ver .gitignore) -- ficam so num arquivo local
// "keystore.properties" (modelo em keystore.properties.example) que cada
// maquina que for gerar um release assinado precisa criar por conta propria.
// Sem esse arquivo (caso normal: CI, ou qualquer maquina que so vai rodar
// "assembleDebug"/testes), o build type "release" simplesmente fica sem
// signingConfig -- continua compilando normalmente, so gera um APK/AAB nao
// assinado (que nao instala em aparelho nenhum ate ser assinado depois).
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
val hasKeystoreConfig = keystorePropertiesFile.exists()
if (hasKeystoreConfig) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

android {
    namespace = "com.bragro.mobile"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.bragro.mobile"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        // URLs do backend (o MESMO backend do site publicado -- ver
        // native-app/README.md). Trocaveis por variante/ambiente sem
        // mudar codigo.
        buildConfigField("String", "API_BASE_URL", "\"https://sistema-agro-bra.vercel.app\"")
        buildConfigField("String", "SUPABASE_URL", "\"https://njmycnbahhvodlhwqnfw.supabase.co\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"sb_publishable_DTtsVk2VEVJjOzSFV8iANg_mE_7JBGt\"")
    }

    signingConfigs {
        if (hasKeystoreConfig) {
            create("release") {
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            // isMinifyEnabled continua false de proposito por enquanto:
            // ativar R8/ProGuard exigiria testar manualmente num aparelho
            // real (Room/kotlinx.serialization/Retrofit usam reflexao/
            // proxies que costumam precisar de regras de "keep" especificas,
            // e nao ha como validar isso sem rodar o app de verdade -- risco
            // que nao vale a pena correr as cegas). Ativar isso e um bom
            // proximo passo, mas so com um aparelho/emulador em maos pra
            // testar o instalado antes de publicar.
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasKeystoreConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    // Tela de splash oficial (API do AndroidX, Task #38) -- funciona em
    // qualquer versao (Android 12+ usa o SplashScreen nativo do sistema;
    // versoes anteriores ganham um comportamento equivalente via compat,
    // sem precisar de duas implementacoes separadas).
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Persistencia local (Room) -- e o que faz o app funcionar OFFLINE de
    // verdade: tudo que o usuario ve/preenche fica gravado aqui primeiro,
    // sincronizado com o servidor depois (ver data/repo/*.kt).
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Preferencias simples (tokens de sessao) -- substitui SharedPreferences
    // cru, API moderna baseada em coroutines/Flow.
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Rede: fala com o Supabase Auth REST (login) e com as rotas
    // /api/mobile/* do site publicado (bootstrap, registros, sync) -- ver
    // data/remote/*.kt.
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // Sincronizacao em segundo plano quando a conexao voltar.
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // OCR embarcado no aparelho (Fase 2, Task #42: Romaneio rapido) --
    // Google ML Kit Text Recognition (modelo latino), 100% no dispositivo,
    // sem round-trip pro servidor. O site NAO tem OCR real hoje (ver
    // lib/services/romaneio-ocr.ts, sempre "NAO_CONFIGURADO" -- so estrutura
    // pronta pra um provedor futuro); aqui o app nativo faz a leitura de
    // verdade, so como recurso de PREENCHIMENTO ASSISTIDO (o usuario sempre
    // confere/edita antes de lancar) -- nao e regra de negocio, entao nao
    // fere o principio de "nao duplicar logica do backend em Kotlin".
    implementation("com.google.mlkit:text-recognition:16.0.1")
    // Ponte suspend/await para as Task<> do Google Play Services (ML Kit
    // devolve Task<Text>, nao uma suspend fun) -- sem isso, `.await()" nao
    // compila.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
    // So pra ler a orientacao EXIF da foto da Camera e desrotacionar antes
    // de comprimir/subir -- sem isso, fotos tiradas em pe saem deitadas no
    // arquivo final (BitmapFactory ignora o EXIF; ML Kit ja corrige
    // sozinho pra OCR, mas a compressao manual abaixo nao).
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
