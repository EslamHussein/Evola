dependencies {
    implementation(project(":contexts:tutoring:application"))
    implementation(project(":contexts:tutoring:domain"))
    implementation(project(":contexts:vocabulary:domain"))
    implementation(project(":integrations:persistence-shared"))
    implementation(libs.exposed.core)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.java.time)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
}
