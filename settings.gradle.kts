pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "evola"

// Serverless (local-first) architecture: the Ktor+Postgres backend (`:server`) and its
// persistence module were retired — everything runs and is stored on-device now, the only network
// dependency being Anthropic (called directly from the app with the user's locally-stored key).
include(
    ":shared",
    ":composeApp",
    ":androidApp",
)
