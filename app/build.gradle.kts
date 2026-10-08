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
}

// O Room salva aqui um JSON com o formato de cada versão do banco.
// Versione a pasta app/schemas no Git: é ela que permite escrever migrações.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // O BOM ("bill of materials") escolhe a versão de todas as bibliotecas do
    // Compose de uma vez. Guardado numa variável e usado nos dois lugares que
    // precisam dele (app e testes do emulador) — é o padrão da documentação
    // do Google e acaba com o aviso "declared multiple times".
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

    // Banco de dados (Room sobre SQLite). O "ksp" gera o código dos DAOs.
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)

    // Fase B: roda a verificação de chuva com o app fechado, 2x por dia.
    implementation(libs.androidx.work.runtime.ktx)

    // Refinamento: bloqueio com digital ou PIN do celular.
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.fragment.ktx)

    // R4: tela de abertura com o broto, igual em todo Android a partir do 8.0.
    implementation(libs.androidx.core.splashscreen)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // O org.json que vem no Android é só "casca" nos testes do computador.
    // Este é o de verdade, para testar a leitura da resposta da Open-Meteo.
    testImplementation(libs.org.json)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
