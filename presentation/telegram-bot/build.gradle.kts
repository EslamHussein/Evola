plugins {
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":core:application-kernel"))
    implementation(project(":contexts:learner-identity:application"))
    implementation(project(":contexts:learner-identity:domain"))
    implementation(project(":contexts:vocabulary:application"))
    implementation(project(":contexts:vocabulary:domain"))
    implementation(project(":contexts:exercise-generation:application"))
    implementation(project(":contexts:tutoring:application"))
    implementation(project(":contexts:tutoring:domain"))
    implementation(project(":contexts:learning-resources:application"))
    implementation(project(":contexts:learning-resources:domain"))
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
}
