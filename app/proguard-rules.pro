# =============================================================================
# Regras de R8/ProGuard para o build type "release" (isMinifyEnabled = true,
# ver app/build.gradle.kts). Antes disso o minify ficava desligado de
# proposito -- essas regras sao o motivo de agora poder ligar com seguranca
# (dentro do que e possivel confirmar so por leitura de codigo/documentacao
# oficial, sem compilador disponivel neste ambiente).
#
# IMPORTANTE -- LEIA ANTES DE PUBLICAR: erro de regra de "keep" nao aparece
# como erro de compilacao, aparece como CRASH ou comportamento silenciosamente
# errado em runtime, e SO no aparelho real (o R8 remove/renomeia codigo que
# "parece" nao usado, mas pode ser usado via reflexao/proxy/ServiceLoader).
# A primeira build de release com minify ligado precisa ser testada manualmente
# num aparelho real cobrindo pelo menos: login, listar/criar/editar um
# lancamento em pelo menos 2 modulos diferentes, upload de foto/arquivo
# (Coil/OCR) e a tela do mapa (osmdroid). Se alguma coisa quebrar so no
# release (e nao no debug), a causa quase sempre e uma regra de keep faltando
# aqui -- a solucao mais rapida enquanto investiga e voltar isMinifyEnabled
# pra false.
# =============================================================================

# Atributos exigidos pra reflexao/generics/anotacoes funcionarem certo depois
# de obfuscado (kotlinx.serialization, Retrofit e Room dependem disso) --
# sem isso, tipos genericos (Call<List<Foo>>, List<ColumnConfig> etc.) e
# anotacoes lidas em runtime podem virar erro silencioso.
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*, AnnotationDefault
-keepattributes SourceFile, LineNumberTable

# -----------------------------------------------------------------------------
# kotlinx.serialization (kotlinx-serialization-json 1.6.3)
# -----------------------------------------------------------------------------
# A biblioteca ja embute suas proprias regras de consumer-rules (arquivo
# rules/r8.pro dentro do artefato, aplicado automaticamente em qualquer app
# que dependa dela) cobrindo o caso comum de "@Serializable sem companion
# object nomeado" -- e exatamente o caso de todas as 89 classes @Serializable
# em data/model/Models.kt (nenhuma usa "companion object NomeCustomizado",
# confirmado por leitura). Ainda assim, as regras abaixo (recomendadas
# historicamente pela documentacao/README do kotlinx.serialization pra R8)
# ficam como camada extra de seguranca, restritas ao nosso pacote de modelos
# pra nao desligar a obfuscacao do resto do app: mantem a classe gerada
# "$serializer" (usada pelo Json.encode/decode em runtime) e a funcao
# serializer() de cada Companion.
-keep,includedescriptorclasses class com.bragro.mobile.data.model.**$$serializer { *; }
-keepclassmembers class com.bragro.mobile.data.model.** {
    *** Companion;
}
-keepclasseswithmembers class com.bragro.mobile.data.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# Nomes de campo Kotlin (ex.: "areaHaCalc") NAO precisam ficar sem obfuscar
# pra o JSON continuar correto: o plugin do compilador gera o serializer com
# os nomes de chave (ou @SerialName) EMBUTIDOS como constantes de String antes
# do R8 rodar -- so o nome da propriedade Kotlin muda, a chave JSON nao.

# -----------------------------------------------------------------------------
# Retrofit 2.11.0 + converter-kotlinx-serialization
# -----------------------------------------------------------------------------
# Regras oficiais do proprio projeto Retrofit para R8 (retrofit2.pro, bundled
# no artefato desde a 2.6.0 -- replicadas aqui de forma explicita por
# clareza/seguranca, nao tem custo repetir uma regra ja aplicada
# automaticamente via consumer-rules).
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface * extends <1>
-keep,allowoptimization,allowshrinking,allowobfuscation class kotlin.coroutines.Continuation
-if interface * { @retrofit2.http.* public *** *(...); }
-keep,allowoptimization,allowshrinking,allowobfuscation class <3>
-keep,allowoptimization,allowshrinking,allowobfuscation class retrofit2.Response

# -----------------------------------------------------------------------------
# OkHttp 4.12.0 (logging-interceptor + a versao transitiva usada pelo Retrofit)
# -----------------------------------------------------------------------------
# OkHttp ja embute seu proprio consumer-rules.pro (dontwarn de
# okhttp3.internal.platform.**/org.conscrypt.**/org.bouncycastle.**/
# org.openjsse.** -- provedores de TLS opcionais que so existem em JVM
# desktop, nao no Android, e sao referenciados dentro de try/catch
# NoClassDefFoundError) -- nenhuma regra manual necessaria.

# -----------------------------------------------------------------------------
# Room 2.6.1
# -----------------------------------------------------------------------------
# Bibliotecas modernas do AndroidX (Room incluso desde a 2.1) embutem seu
# proprio consumer-rules.pro cobrindo os *_Impl gerados pelo KSP (DAOs,
# Database) e a infraestrutura de reflexao interna do Room -- nenhuma regra
# manual necessaria pras nossas entidades/DAOs (ver Entities.kt/AppDatabase.kt).

# -----------------------------------------------------------------------------
# WorkManager 2.9.1 (androidx.work) -- SyncWorker e TokenRefreshWorker
# -----------------------------------------------------------------------------
# Diferente de Room/Coil/OkHttp, este PRECISA de regra manual: o WorkManager
# instancia CoroutineWorker/Worker por REFLEXAO (Class.forName do nome
# completo da classe, gravado no WorkSpec) quando o WorkerFactory padrao
# processa um job agendado -- as regras da propria biblioteca so cobrem a
# infraestrutura dela, nao conhecem (nem podem conhecer) as subclasses
# concretas deste app. Sem isso, o R8 pode remover/renomear SyncWorker ou
# TokenRefreshWorker (nada os chama diretamente por nome de classe no nosso
# codigo Kotlin -- só a WorkRequest que referencia a KClass, o que nao e
# suficiente pra manter o construtor) e a sincronizacao em segundo plano falha
# silenciosamente so no release (ver sync/SyncWorker.kt, sync/TokenRefreshWorker.kt).
-keep class com.bragro.mobile.sync.SyncWorker {
    <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class com.bragro.mobile.sync.TokenRefreshWorker {
    <init>(android.content.Context, androidx.work.WorkerParameters);
}

# -----------------------------------------------------------------------------
# kotlinx-coroutines-android 1.8.1 / kotlinx-coroutines-play-services 1.8.1
# -----------------------------------------------------------------------------
# O artefato -android ja embute consumer-rules.pro mantendo (via -keepnames)
# as classes carregadas por ServiceLoader (AndroidDispatcherFactory,
# AndroidExceptionPreHandler, MainDispatcherFactory) -- nenhuma regra manual
# necessaria.

# -----------------------------------------------------------------------------
# Coil 2.6.0 (io.coil-kt:coil-compose)
# -----------------------------------------------------------------------------
# Nao usa reflexao em runtime pra decodificar/mostrar imagem (carregamento por
# URL do logo/avatar, ver ui/home/HomeScreen.kt) e ja embute seu proprio
# consumer-rules.pro -- nenhuma regra manual necessaria.

# -----------------------------------------------------------------------------
# ML Kit Text Recognition 16.0.1 (OCR do Romaneio Rapido)
# -----------------------------------------------------------------------------
# Artefato do Google (Play Services) -- essas bibliotecas embutem seu proprio
# consumer-rules.pro cobrindo a infraestrutura de carregamento de modelo/
# comunicacao com o Play Services -- nenhuma regra manual necessaria pro uso
# feito aqui (TextRecognition.getClient(...).process(image), sem subclasses
# custom nossas envolvidas).

# -----------------------------------------------------------------------------
# Firebase Crashlytics (condicional -- so ativo com google-services.json real)
# -----------------------------------------------------------------------------
# O SDK do Crashlytics embute seu proprio consumer-rules.pro -- nenhuma regra
# manual necessaria pro SDK inicializar/reportar crash. ATENCAO (fora do
# escopo de proguard-rules.pro, so um aviso): o projeto NAO aplica o plugin
# Gradle "com.google.firebase.crashlytics" (so o "com.google.gms.google-
# services", ver build.gradle.kts raiz) -- sem aquele plugin, o mapping.txt
# gerado pelo R8 neste build NAO e enviado automaticamente pro Firebase, e os
# stack traces que chegarem ao console do Crashlytics ficarao com nomes
# obfuscados (ex. "a.b.c"), dificultando debug. Nao afeta a estabilidade do
# app (Crashlytics continua funcionando), so a legibilidade dos relatorios --
# considerar aplicar o plugin Crashlytics num passo futuro, quando o Firebase
# estiver configurado de fato.

# -----------------------------------------------------------------------------
# osmdroid 6.1.18 (mapa nativo do FieldView/Drone)
# -----------------------------------------------------------------------------
# Biblioteca puramente Java/Kotlin (sem .so nativo alem do proprio Android
# SDK), sem uso conhecido de reflexao pesada (Class.forName/ServiceLoader)
# no caminho usado por este app (MapView, TileSourceFactory.MAPNIK, overlays
# de Polygon -- unico uso no projeto e em ui/fieldview/FieldviewScreen.kt,
# aba "Fazendas/KML"). Nenhuma regra manual adicionada por falta de evidencia
# concreta da necessidade (nao ha wiki/consumer-rules oficiais publicados
# pelo projeto, que foi arquivado). Exatamente por essa incerteza, a tela do
# mapa esta na lista de testes manuais obrigatorios antes de publicar.

# -----------------------------------------------------------------------------
# Compose (androidx.compose.ui/foundation/material3) e demais AndroidX
# (lifecycle, activity-compose, navigation-compose, datastore-preferences,
# core-splashscreen, exifinterface, browser)
# -----------------------------------------------------------------------------
# Todos artefatos AndroidX modernos, cada um com seu proprio consumer-rules.pro
# cobrindo a respectiva infraestrutura interna -- nenhuma regra manual
# necessaria pro uso feito neste app (Composables, ViewModel, NavHost, etc.).
