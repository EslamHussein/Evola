dependencies {
    implementation(project(":contexts:exercise-generation:application"))
    implementation(project(":integrations:persistence-shared"))
    implementation(libs.exposed.core)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.jdbc)
    implementation(libs.kotlinx.coroutines.core)
}
