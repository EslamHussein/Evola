plugins {
    application
}

dependencies {
    implementation(project(":core:domain-kernel"))
    implementation(project(":core:application-kernel"))

    implementation(project(":contexts:learner-identity:domain"))
    implementation(project(":contexts:learner-identity:application"))
    implementation(project(":contexts:learner-identity:infrastructure"))

    implementation(project(":contexts:vocabulary:domain"))
    implementation(project(":contexts:vocabulary:application"))
    implementation(project(":contexts:vocabulary:infrastructure"))

    implementation(project(":contexts:exercise-generation:application"))
    implementation(project(":contexts:exercise-generation:infrastructure"))

    implementation(project(":integrations:ai-gateway"))
    implementation(project(":integrations:persistence-shared"))

    implementation(project(":presentation:telegram-bot"))

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)
    runtimeOnly(libs.slf4j.simple)
}

application {
    mainClass.set("evola.compositionroot.MainKt")
}
