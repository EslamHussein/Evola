dependencies {
    api(project(":core:application-kernel"))
    api(project(":integrations:ai-gateway"))
    implementation(libs.kotlinx.coroutines.core)
}
