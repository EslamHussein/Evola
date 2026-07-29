dependencies {
    implementation(project(":contexts:learner-identity:application"))
    implementation(project(":contexts:learner-identity:domain"))
    implementation(project(":integrations:persistence-shared"))
    implementation(libs.exposed.core)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.jdbc)
    implementation(libs.kotlinx.coroutines.core)
}
