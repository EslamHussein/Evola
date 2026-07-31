dependencies {
    api(project(":contexts:tutoring:domain"))
    api(project(":core:application-kernel"))
    implementation(project(":contexts:vocabulary:application"))
    implementation(project(":contexts:vocabulary:domain"))
    api(project(":integrations:ai-gateway"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
}
