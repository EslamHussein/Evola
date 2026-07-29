dependencies {
    api(libs.exposed.core)
    api(libs.exposed.dao)
    api(libs.exposed.jdbc)
    api(libs.exposed.java.time)
    implementation(libs.hikaricp)
    implementation(libs.postgresql.driver)
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgresql)
    implementation(libs.kotlinx.coroutines.core)
}
