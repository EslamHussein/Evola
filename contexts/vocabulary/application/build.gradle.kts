dependencies {
    api(project(":contexts:vocabulary:domain"))
    api(project(":core:application-kernel"))
    implementation(libs.kotlinx.coroutines.core)
}
