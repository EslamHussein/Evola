dependencies {
    implementation(project(":contexts:learning-resources:application"))
    implementation(project(":contexts:learning-resources:domain"))
    implementation(project(":integrations:ai-gateway"))
    implementation(project(":integrations:persistence-shared"))
    implementation(libs.exposed.core)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.java.time)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.pdfbox)
}
