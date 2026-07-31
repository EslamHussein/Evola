dependencies {
    api(project(":contexts:learning-resources:domain"))
    api(project(":core:application-kernel"))
    api(project(":integrations:ai-gateway"))
    api(project(":contexts:vocabulary:application"))
    api(project(":contexts:vocabulary:domain"))
    implementation(libs.kotlinx.coroutines.core)
}
