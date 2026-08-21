plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
}

kotlin {
    jvmToolchain(21)

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    jvm()
    androidTarget()

    // iosX64 (Intel Mac simulator) intentionally excluded - androidx.sqlite:sqlite-bundled
    // doesn't publish for it, mirroring composeApp's own iosX64 exclusion for navigation3-ui.
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            // api, not implementation - :shared references AppDatabase (extends RoomDatabase)
            // directly, so RoomDatabase's own type metadata must be on its compile classpath too.
            api(libs.androidx.room.runtime)
            api(libs.androidx.sqlite.bundled)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

dependencies {
    add("kspJvm", libs.androidx.room.compiler)
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspIosArm64", libs.androidx.room.compiler)
    add("kspIosSimulatorArm64", libs.androidx.room.compiler)
}

room {
    schemaDirectory("$projectDir/schemas")
}

android {
    namespace = "evola.database"
    compileSdk = 36
    defaultConfig {
        minSdk = 24
    }
}
