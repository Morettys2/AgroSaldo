plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ktlint)
}

android {
    namespace = "br.joao.agrosaldo"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "br.joao.agrosaldo"
        minSdk = 26
        targetSdk = 37
        versionCode = 6
        versionName = "1.5"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = true
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    // src/testFixtures: fakes em memória e dados de exemplo, compartilhados pelos testes
    // unitários (src/test) e instrumentados (src/androidTest), mas fora do APK.
    testFixtures {
        enable = true
    }
    lint {
        // Erro do Android Lint quebra o build (e o CI); avisos aparecem no relatório.
        abortOnError = true
        checkDependencies = false
    }
    testOptions {
        // Robolectric precisa dos recursos (fontes, temas, strings) para desenhar as telas.
        unitTests.isIncludeAndroidResources = true
    }
}

// Formatação: `./gradlew ktlintCheck` confere, `./gradlew ktlintFormat` arruma sozinho.
// As regras (estilo do Android Studio + regras de Compose) ficam no .editorconfig da raiz.
ktlint {
    android.set(true)
    baseline.set(file("ktlint-baseline.xml"))
}

// Análise estática: `./gradlew detekt`. O baseline guarda os achados antigos, para o CI
// cobrar só código novo; ele encolhe conforme os arquivos são mexidos (`./gradlew detektBaseline`).
//
// Roda pela linha de comando do detekt numa JVM 21 separada, e não pelo plugin: o detekt
// 1.23 não funciona no JDK 25 em que o Gradle do Android Studio roda.
val detektCli: Configuration by configurations.creating {
    // O detekt 1.23 traz o compilador Kotlin 2.0; o Gradle tentaria alinhar com o 2.2 do app.
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlin") useVersion("2.0.21")
    }
}
dependencies { detektCli(libs.detekt.cli) }

fun JavaExec.configurarDetekt(vararg extras: String) {
    group = "verification"
    classpath = detektCli
    mainClass.set("io.gitlab.arturbosch.detekt.cli.Main")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
    val fontes = listOf(
        "src/main/java",
        "src/test/java",
        "src/testFixtures/java",
        "src/androidTest/java",
        "../domain/src/main/java",
        "../domain/src/test/java",
        "../domain/src/testFixtures/java"
    )
    inputs.files(fontes.map { fileTree(it) })
    args(
        "--input", fontes.joinToString(",") { file(it).path },
        "--config", rootProject.file("config/detekt/detekt.yml").path,
        "--build-upon-default-config",
        "--jvm-target", "21",
        "--report", "html:${layout.buildDirectory.get()}/reports/detekt/detekt.html",
        *extras
    )
}

val baselineDoDetekt = file("detekt-baseline.xml")

tasks.register<JavaExec>("detekt") {
    description = "Análise estática com o detekt."
    configurarDetekt(*(if (baselineDoDetekt.exists()) arrayOf("--baseline", baselineDoDetekt.path) else emptyArray()))
}

tasks.register<JavaExec>("detektBaseline") {
    description = "Grava os achados atuais do detekt no baseline."
    configurarDetekt("--baseline", baselineDoDetekt.path, "--create-baseline")
}

// Os testes de captura (CapturasDeTelaTest) gravam as imagens em app/build/capturas.
tasks.withType<Test>().configureEach {
    systemProperty("roborazzi.test.record", "true")
    // O Robolectric do Android 16 mexe em internals do Java (FileDescriptor) que o JDK 17+
    // esconde por padrão.
    jvmArgs(
        "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
        "--add-opens=java.base/java.io=ALL-UNNAMED"
    )
}

// O lint lê os arquivos que o KSP e o Hilt geram para os testes. Se roda ao mesmo tempo que a geração,
// o analisador do Kotlin às vezes cai ("Unexpected failure during lint analysis"): então ele espera.
tasks.configureEach {
    if (name.startsWith("lintAnalyze")) {
        mustRunAfter(tasks.matching { it.name.startsWith("hiltJavaCompile") || it.name.startsWith("ksp") || it.name.startsWith("compile") })
    }
}

// Schemas exportados pelo Room (um JSON por versão). Versionar app/schemas:
// é a referência para escrever e testar migrações.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // O domínio é um módulo Kotlin puro: o compilador impede que ele importe Android.
    implementation(project(":domain"))

    // BOM em variável para reutilizar em implementation e androidTestImplementation
    // sem o aviso "declared multiple times".
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)

    // Room
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)

    // Hilt: injeção de dependências (ViewModels, Worker e Application). O processador roda via KSP.
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // WorkManager: verificação periódica de chuva em segundo plano.
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // Bloqueio por biometria/credencial do aparelho. appcompat e fragment são
    // declarados para sobrepor as versões antigas trazidas pelo biometric 1.1.0.
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.fragment.ktx)

    // SplashScreen API compatível com API < 31.
    implementation(libs.androidx.core.splashscreen)

    ktlintRuleset(libs.compose.rules.ktlint)

    testFixturesImplementation(libs.kotlinx.coroutines.core)
    // Fakes em memória e dados de exemplo moram no :domain; o Semeador (que gera SQL) fica aqui.
    testFixturesApi(testFixtures(project(":domain")))
    // O plugin do Compose também processa src/testFixtures e exige o runtime no classpath,
    // mesmo sem nenhum @Composable lá.
    testFixturesImplementation(composeBom)
    testFixturesImplementation(libs.androidx.compose.runtime)

    testImplementation(libs.junit)
    // Teste do grafo de dependências: o Hilt monta o app de verdade dentro do Robolectric.
    testImplementation(libs.hilt.android.testing)
    kspTest(libs.hilt.compiler)
    testImplementation(libs.kotlinx.coroutines.test)
    // Implementação real do org.json para testes JVM (o android.jar só tem stubs).
    testImplementation(libs.org.json)
    // Telas Compose testadas no JVM: cliques, textos e capturas de tela, sem emulador.
    testImplementation(composeBom)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
