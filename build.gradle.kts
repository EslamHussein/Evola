plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.compose.compiler) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
}

// :shared and :composeApp declare kotlin("multiplatform") themselves (mutually exclusive with
// kotlin("jvm")); :androidApp declares the Android application + kotlin("android") plugins.
// Every other module is a plain JVM module and gets the common JVM setup below.
val nonJvmModules = setOf(":shared", ":composeApp", ":androidApp")

subprojects {
    repositories {
        google()
        mavenCentral()
    }

    if (path !in nonJvmModules) {
        apply(plugin = "org.jetbrains.kotlin.jvm")

        extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
            jvmToolchain(21)
        }

        dependencies {
            "testImplementation"("org.junit.jupiter:junit-jupiter:5.11.3")
            "testImplementation"("org.jetbrains.kotlin:kotlin-test-junit5:2.2.20")
            "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
        }

        tasks.withType<Test> {
            useJUnitPlatform()
        }
    }

    // Several bounded-context modules share a leaf name (e.g. "infrastructure"); disambiguate
    // jar file names by full module path so packaging doesn't collide.
    tasks.withType<Jar> {
        archiveBaseName.set(project.path.removePrefix(":").replace(":", "-"))
    }
}
