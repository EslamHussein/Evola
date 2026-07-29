plugins {
    alias(libs.plugins.kotlin.jvm) apply false
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    repositories {
        mavenCentral()
    }

    extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
        jvmToolchain(21)
    }

    dependencies {
        "testImplementation"("org.junit.jupiter:junit-jupiter:5.11.3")
        "testImplementation"("org.jetbrains.kotlin:kotlin-test-junit5:2.1.0")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    // Several bounded-context modules share a leaf name (e.g. "infrastructure"); disambiguate
    // jar file names by full module path so packaging (e.g. composition-root's distribution) doesn't collide.
    tasks.withType<Jar> {
        archiveBaseName.set(project.path.removePrefix(":").replace(":", "-"))
    }
}
