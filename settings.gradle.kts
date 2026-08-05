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

// Local-first architecture: everything runs and is stored on-device. The only network dependency
// is Anthropic, called directly from the app with the user's locally-stored key.
include(
    ":shared",
    ":composeApp",
    ":androidApp",
)
