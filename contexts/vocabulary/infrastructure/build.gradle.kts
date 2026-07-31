dependencies {
    implementation(project(":contexts:vocabulary:application"))
    implementation(project(":contexts:vocabulary:domain"))
    implementation(project(":integrations:persistence-shared"))
    implementation(libs.exposed.core)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.java.time)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
}
