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
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.haze)
            implementation(libs.flowmvi.core)
            implementation(libs.flowmvi.compose)
            implementation(libs.flowmvi.android)
            // Orbit MVI, added alongside FlowMVI during the incremental Container-by-Container
            // migration (see docs/ROADMAP.md) - flowmvi-* is removed once all 16 features are
            // converted, not before.
            implementation(libs.orbit.core)
            implementation(libs.orbit.viewmodel)
            implementation(libs.orbit.compose)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
        }
        androidMain.dependencies {
            implementation(libs.androidx.security.crypto)
            implementation(libs.androidx.activity.compose)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.sqldelight.android.driver)
            implementation(libs.pdfbox.android)
            implementation(libs.kermit.io)
            implementation(libs.androidx.work.runtime.ktx)
            // Not debug-scoped: the KMP androidMain DSL doesn't expose build-type-specific
            // configurations. Small tooling dependency, acceptable to ship in release too.
            implementation(compose.uiTooling)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
            implementation(libs.sqldelight.native.driver)
            implementation(libs.kermit.io)
        }
        // JVM-backed unit tests for the FlowMVI Containers - no Android framework/emulator
        // needed since Containers are plain Kotlin depending only on :shared. Mirrors :shared's
        // own jvmTest setup (JdbcSqliteDriver backing a real EvolaDatabase + real Local*Repository
        // implementations, not mocks) so a Container test exercises the same code path production
        // does.
        androidUnitTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.sqldelight.sqlite.driver)
            implementation(libs.flowmvi.test)
            implementation(libs.orbit.test)
            implementation(libs.ktor.client.mock)
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
