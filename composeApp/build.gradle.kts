plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.android.library)
}

kotlin {
    jvmToolchain(21)

    // expect/actual classes (SpeechService, ReminderScheduler, etc.) are a stable, intentional
    // part of this project's platform-bridge pattern - silences the per-declaration "in Beta"
    // warning rather than suppressing it per-file.
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    androidTarget()

    // iosX64 (Intel Mac simulator target) intentionally excluded - navigation3-ui doesn't publish
    // for it yet. Real-device builds are unaffected (those always target iosArm64); this only
    // matters for running the iOS Simulator on an Intel Mac dev machine, which Apple Silicon has
    // made increasingly rare since 2020.
    listOf(
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
            implementation("org.jetbrains.compose.ui:ui-tooling-preview:${libs.versions.composeMultiplatform.get()}")
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.haze)
            implementation(libs.orbit.core)
            implementation(libs.orbit.viewmodel)
            implementation(libs.orbit.compose)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.koin.compose.navigation3)
            implementation(libs.navigation3.ui)
            implementation(libs.kotlinx.serialization.core)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.sqldelight.android.driver)
            implementation(libs.pdfbox.android)
            implementation(libs.kermit.io)
            implementation(libs.androidx.work.runtime.ktx)
            // Not debug-scoped: the KMP androidMain DSL doesn't expose build-type-specific
            // configurations. Small tooling dependency, acceptable to ship in release too.
            implementation("org.jetbrains.compose.ui:ui-tooling:${libs.versions.composeMultiplatform.get()}")
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
            implementation(libs.sqldelight.native.driver)
            implementation(libs.kermit.io)
        }
        // JVM-backed unit tests for the Orbit ViewModels - no Android framework/emulator needed
        // since ViewModels are plain Kotlin depending only on :shared. Mirrors :shared's own
        // jvmTest setup (JdbcSqliteDriver backing a real EvolaDatabase + real Local*Repository
        // implementations, not mocks) so a ViewModel test exercises the same code path production
        // does.
        androidUnitTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.sqldelight.sqlite.driver)
            implementation(libs.orbit.test)
            implementation(libs.ktor.client.mock)
        }
    }
}

android {
    namespace = "evola.composeapp"
    compileSdk = 36
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
