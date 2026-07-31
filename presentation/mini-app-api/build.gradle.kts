plugins {
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":core:application-kernel"))
    implementation(project(":core:domain-kernel"))
    implementation(project(":contexts:learner-identity:application"))
    implementation(project(":contexts:learner-identity:domain"))
    implementation(project(":contexts:vocabulary:application"))
    implementation(project(":contexts:vocabulary:domain"))
    implementation(project(":contexts:tutoring:application"))
    implementation(project(":contexts:tutoring:domain"))
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test.junit5)
}
