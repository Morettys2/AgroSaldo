import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Módulo do domínio: Kotlin puro (JVM), sem Android, Room nem Compose. O compilador garante
// que nada de Android entra aqui: se alguém importar `android.*`, o build quebra.
plugins {
    alias(libs.plugins.kotlin.jvm)
    // src/testFixtures: fakes em memória das interfaces do domínio e os dados de exemplo.
    // O app e os testes dele reaproveitam; o APK não leva nada disso.
    `java-test-fixtures`
    alias(libs.plugins.ktlint)
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

// Mesmas regras de formatação do app (.editorconfig da raiz).
ktlint {
    android.set(true)
}

dependencies {
    // Flow aparece nas interfaces dos repositórios; quem usa o domínio precisa da biblioteca.
    api(libs.kotlinx.coroutines.core)
    testFixturesApi(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
