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

include(
    ":shared",
    ":server",
    ":composeApp",
    ":androidApp",
    ":integrations:persistence-shared",
)
