plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.library)
    alias(libs.plugins.sqldelight)
}

kotlin {
    jvmToolchain(21)

    // expect/actual classes (DatabaseDriverFactory, LogFileWriterFactory, etc.) are a stable,
    // intentional part of this project's platform-bridge pattern - silences the per-declaration
    // "in Beta" warning rather than suppressing it per-file.
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    jvm()
    androidTarget()

    // iosX64 (Intel Mac simulator) intentionally excluded - :database can't target it
    // (androidx.sqlite:sqlite-bundled doesn't publish for it), mirroring composeApp's existing
    // iosX64 exclusion for navigation3-ui. Confirmed unused: no Xcode scheme builds it.
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            api(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.client.auth)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.serialization.kotlinx.json)
            api(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines.extensions)
            api(libs.kermit)
            api(project(":database"))
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
        jvmTest.dependencies {
            // In-memory JDBC driver so commonTest can exercise the generated queries on the JVM.
            implementation(libs.sqldelight.sqlite.driver)
        }
    }
}

sqldelight {
    databases {
        create("EvolaDatabase") {
            packageName.set("evola.shared.db")
        }
    }
}

android {
    namespace = "evola.shared"
    compileSdk = 36
    defaultConfig {
        minSdk = 24
    }
}
