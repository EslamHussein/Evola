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
            api(libs.compose.runtime)
            api(libs.compose.foundation)
            api(libs.compose.material3)
            api(libs.compose.material.icons.extended)
            api(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.ui.tooling.preview)
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
            implementation(libs.compose.ui.tooling)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
            implementation(libs.sqldelight.native.driver)
            implementation(libs.kermit.io)
        }
        // JVM-backed unit tests for the Orbit ViewModels - no emulator needed since ViewModels are
        // plain Kotlin depending only on :shared. Mirrors :shared's own jvmTest setup (a real
        // EvolaDatabase + real Local*Repository implementations, not mocks) so a ViewModel test
        // exercises the same code path production does. Robolectric is the one Android-framework
        // dependency here, needed only because Room's Android database builder requires a real
        // Context (unlike SQLDelight's JDBC-driver path, which didn't) - it still runs on the host
        // JVM, no emulator.
        androidUnitTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.sqldelight.sqlite.driver)
            implementation(libs.orbit.test)
            implementation(libs.ktor.client.mock)
            implementation(libs.robolectric)
            // BundledSQLiteDriver's native library doesn't load under Robolectric's host-JVM
            // sandbox (production code never sees this - Robolectric only runs these tests) - the
            // classic SQLiteOpenHelper-based framework driver is what Robolectric's own SQLite
            // shadow layer is built around, so tests use that instead.
            implementation(libs.androidx.sqlite.framework)
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

// Robolectric (added for the Room-on-Android tests - see androidUnitTest.dependencies above)
// sandboxes each @RunWith(RobolectricTestRunner) test class in its own ClassLoader, which
// corrupts java.sql.DriverManager's driver registry for other, plain-JDBC tests (like
// ProcessingStatusViewModelTest's SQLDelight-backed ones) if they share the same forked test JVM.
// Fresh JVM per test class avoids that cross-contamination.
tasks.withType<Test>().configureEach {
    forkEvery = 1
}

// The Kotlin Gradle plugin computes this task's `enabled` flag by matching Xcode's env vars
// (CONFIGURATION/SDK_NAME/ARCHS) against the registered framework build types/targets. Some
// headless build runners don't reproduce that match exactly, so force it on - the task's own
// actions still no-op safely if the framework truly isn't buildable for the requested config.
tasks.matching { it.name == "embedAndSignAppleFrameworkForXcode" }.configureEach {
    enabled = true
}
