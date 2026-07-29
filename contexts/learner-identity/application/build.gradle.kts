dependencies {
    api(project(":contexts:learner-identity:domain"))
    api(project(":core:application-kernel"))
    implementation(libs.kotlinx.coroutines.core)
}
