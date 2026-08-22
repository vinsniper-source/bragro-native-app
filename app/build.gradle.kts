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
        // Bump -- pedido do usuario ("a versao do app continua 1.16 na
        // informaçoes do app no android" apos instalar um apk que deveria
        // ter as correçoes de cor da barra inferior/blocos individuais):
        // versionName/versionCode sao campos fixos no codigo, NAO mudam
        // sozinhos a cada build -- sem bump, a tela "Informaçoes do app" do
        // Android sempre mostra o numero antigo, mesmo com o codigo novo
        // rodando por dentro. Bump serve como confirmaçao confiavel de que
        // a instalaçao pegou o apk certo (se apos instalar ainda aparecer
        // 1.1.6, a instalaçao nao pegou o apk novo -- se aparecer 1.1.7,
        // pegou, e as cores tem que estar corrigidas tambem).
        versionCode = 24
        versionName = "1.2.14"

        // URLs do backend (o MESMO backend do site publicado -- ver
        // native-app/README.md). Trocaveis por variante/ambiente sem
        // mudar codigo.
        buildConfigField("String", "API_BASE_URL", "\"https://sistema-agro-bra.vercel.app\"")
        buildConfigField("String", "SUPABASE_URL", "\"https://njmycnbahhvodlhwqnfw.supabase.co\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"sb_publishable_DTtsVk2VEVJjOzSFV8iANg_mE_7JBGt\"")

        // Restringe as bibliotecas nativas (.so) do ML Kit (OCR do Romaneio
        // Rapido) so pra arm64-v8a -- sem isso, o APK carrega 4 copias
        // (arm64-v8a, armeabi-v7a, x86, x86_64) dessas bibliotecas, quase 40MB
        // so nisso, mesmo praticamente nenhum aparelho real usar armeabi-v7a/
        // x86/x86_64 hoje em dia (minSdk 24 = Android 7+, ja majoritariamente
        // 64-bit; x86/x86_64 servem so pra emulador). Necessario pra caber no
        // limite de 50MB do bucket do Supabase Storage (plano Free) usado
        // pelo Painel do Dono pra distribuir o app -- ver app-release-card.tsx.
        ndk {
            abiFilters += "arm64-v8a"
        }
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
            // R8/ProGuard ligado (Fase 3) -- proguard-rules.pro agora tem
            // regras de "keep" explicitas pra kotlinx.serialization (data/
            // model/**), Retrofit (regras oficiais do projeto) e WorkManager
            // (SyncWorker/TokenRefreshWorker, instanciados por reflexao) --
            // Room/OkHttp/Coil/ML Kit/coroutines-android/Crashlytics/AndroidX
            // em geral ja embutem seu proprio consumer-rules.pro (nao
            // precisam de regra manual, ver comentarios em proguard-
            // rules.pro pra detalhe de cada dependencia).
            //
            // ATENCAO -- MAIOR RISCO desta mudanca: erro de regra de keep NAO
            // aparece como erro de compilacao, aparece como CRASH ou
            // comportamento silenciosamente errado em runtime, e SO num
            // aparelho real. A primeira build de release com minify ligado
            // PRECISA ser testada manualmente cobrindo pelo menos: login,
            // listar/criar/editar um lancamento em pelo menos 2 modulos
            // diferentes, upload de foto/arquivo (Coil/OCR) e a tela do mapa
            // (osmdroid, aba Fazendas/KML do FieldView). Se algo quebrar SO
            // no release (nao no debug), a causa quase sempre e uma regra de
            // keep faltando -- a solucao mais rapida enquanto investiga e
            // voltar isMinifyEnabled pra false.
            isMinifyEnabled = true
            // shrinkResources fica de fora por enquanto: nao foi possivel
            // confirmar, so por leitura de codigo (sem compilador/build real
            // disponivel neste ambiente), que nenhum recurso e referenciado
            // dinamicamente por nome/string em runtime (ex.: Resources.
            // getIdentifier, comum em apps com icone dinamico por
            // categoria/status) -- risco desnecessario de ligar as cegas
            // junto com o minify. Ativar isso pode ser um proximo passo,
            // depois que a build com minify sozinho for validada no aparelho.
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
    // Versoes do Compose fixadas explicitamente (em vez de depender so do
    // compose-bom) -- o build ja tentou resolver via BOM 2024.06.00 duas
    // vezes (com Gradle 9.3.0 e depois 8.9) e em ambas o classpath resolvido
    // acabou com uma versao de material3/foundation incompativel com o
    // codigo (ExposedDropdownMenu "nao encontrado", weight "internal"),
    // sintoma de a resolucao NAO estar respeitando a versao do BOM. Fixar a
    // versao de cada artefato Compose aqui elimina essa ambiguidade: 1.6.8
    // (ui/foundation) e 1.2.1 (material3) sao as versoes reais por tras do
    // BOM 2024.06.00, compativeis com o Compose Compiler 1.5.14/Kotlin
    // 1.9.24 usados neste projeto.
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

    implementation("androidx.compose.ui:ui:1.6.8")
    implementation("androidx.compose.ui:ui-graphics:1.6.8")
    implementation("androidx.compose.ui:ui-tooling-preview:1.6.8")
    implementation("androidx.compose.foundation:foundation:1.6.8")
    implementation("androidx.compose.material3:material3:1.2.1")
    implementation("androidx.compose.material:material-icons-extended:1.6.8")
    debugImplementation("androidx.compose.ui:ui-tooling:1.6.8")

    // Fonte Geist (pedido do usuario: "implemente a fonte Geist no app") --
    // baixada em tempo de execucao via Google Fonts (Downloadable Fonts API,
    // ver ui/theme/Type.kt), a mesma fonte ja usada no site (Geist Sans via
    // next/font). Evita empacotar arquivos .ttf binarios no APK -- o Android
    // baixa e faz cache da fonte pelo provedor do Google Play Services,
    // precisando so das credenciais em res/values/font_certs.xml.
    implementation("androidx.compose.ui:ui-text-google-fonts:1.6.8")

    // Carregamento de imagem por URL (logo da organização/avatar do usuário
    // no cabeçalho do Início, ver ui/home/HomeScreen.kt) -- única biblioteca
    // nova deste pacote de mudanças, precisa por não existir nenhum jeito
    // built-in do Compose de carregar bitmap de rede direto num Image().
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Custom Tabs (Chrome) pro botão Módulos abrir Configurações/Base de
    // Dados/Acessos -- pedido do usuário ("habilite de verdade direto...
    // não quero que seja redirecionado para o app anterior"). Um
    // Intent.ACTION_VIEW comum passa pela resolução de "App Links" do
    // Android, que pode entregar a URL pra QUALQUER app instalado que tenha
    // esse domínio verificado (inclusive um app antigo/PWA do próprio site,
    // se ainda estiver instalado no aparelho) -- exatamente o "app anterior"
    // relatado. CustomTabsIntent abre direto no Chrome (ou outro navegador
    // com suporte), sem passar por essa resolução.
    implementation("androidx.browser:browser:1.8.0")

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

    // Mapa nativo do FieldView (Task #110) -- osmdroid renderiza tiles do
    // OpenStreetMap (MAPNIK, via HTTPS, compativel com usesCleartextTraffic
    // "false" acima) e poligonos de contorno de talhao, SEM chave de API
    // nenhuma (ao contrario de Google Maps/Mapbox, descartados de proposito
    // porque exigiriam cadastro/cobranca). Puramente Java/Kotlin, sem
    // dependencia nativa (.so) alem do proprio Android SDK.
    implementation("org.osmdroid:osmdroid-android:6.1.18")

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

    // Fase 3: relato de erros (Crashlytics) -- so funciona de verdade
    // depois que existir um app/google-services.json (ver bloco no fim
    // deste arquivo e README). A dependencia em si e so uma biblioteca
    // normal, nao exige o arquivo pra COMPILAR -- sem ele, o Firebase
    // simplesmente nao inicializa em tempo de execucao (nenhum crash
    // reportado, nenhum erro visivel, comportamento identico a hoje).
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    implementation("com.google.firebase:firebase-crashlytics")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}

// Fase 3: so aplica o plugin do google-services (que GERA os recursos que o
// Firebase precisa pra inicializar sozinho, a partir do json) quando o
// arquivo de verdade existir -- aplicar esse plugin sem o json quebraria a
// configuracao do Gradle pra QUALQUER build (ate "assembleDebug"), entao
// nao pode ser incondicional enquanto nem todo mundo (CI incluso) tem esse
// arquivo. Ver keystore.properties acima pro mesmo padrao aplicado a
// assinatura de release.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}
