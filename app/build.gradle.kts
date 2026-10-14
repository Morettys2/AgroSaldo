plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
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
}

// Schemas exportados pelo Room (um JSON por versão). Versionar app/schemas:
// é a referência para escrever e testar migrações.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
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

    // WorkManager: verificação periódica de chuva em segundo plano.
    implementation(libs.androidx.work.runtime.ktx)

    // Bloqueio por biometria/credencial do aparelho. appcompat e fragment são
    // declarados para sobrepor as versões antigas trazidas pelo biometric 1.1.0.
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.fragment.ktx)

    // SplashScreen API compatível com API < 31.
    implementation(libs.androidx.core.splashscreen)

    testFixturesImplementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // Implementação real do org.json para testes JVM (o android.jar só tem stubs).
    testImplementation(libs.org.json)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
