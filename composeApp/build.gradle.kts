plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.android.library)
}

kotlin {
    jvmToolchain(21)
    androidTarget()

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { target ->
        target.binaries.framework {
            baseName = "ComposeApp"
            export(project(":shared"))
            // SQLDelight's native driver (touchlab sqliter) calls into the system SQLite; the
            // iOS framework must link libsqlite3 or the link fails with undefined _sqlite3_* symbols.
            linkerOpts("-lsqlite3")
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":shared"))
            api(compose.runtime)
            api(compose.foundation)
            api(compose.material3)
            api(compose.materialIconsExtended)
            api(compose.ui)
            implementation(compose.components.resources)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.haze)
        }
        androidMain.dependencies {
            implementation(libs.androidx.security.crypto)
            implementation(libs.androidx.activity.compose)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.sqldelight.android.driver)
            implementation(libs.pdfbox.android)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
            implementation(libs.sqldelight.native.driver)
        }
    }
}

android {
    namespace = "evola.composeapp"
    compileSdk = 35
    defaultConfig {
        minSdk = 24
    }
}

// The Kotlin Gradle plugin computes this task's `enabled` flag by matching Xcode's env vars
// (CONFIGURATION/SDK_NAME/ARCHS) against the registered framework build types/targets. Some
// headless build runners don't reproduce that match exactly, so force it on - the task's own
// actions still no-op safely if the framework truly isn't buildable for the requested config.
tasks.matching { it.name == "embedAndSignAppleFrameworkForXcode" }.configureEach {
    enabled = true
}
