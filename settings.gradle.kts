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
    ":core:domain-kernel",
    ":core:application-kernel",
    ":contexts:learner-identity:domain",
    ":contexts:learner-identity:application",
    ":contexts:learner-identity:infrastructure",
    ":contexts:vocabulary:domain",
    ":contexts:vocabulary:application",
    ":contexts:vocabulary:infrastructure",
    ":contexts:tutoring:domain",
    ":contexts:tutoring:application",
    ":contexts:tutoring:infrastructure",
    ":contexts:learning-resources:domain",
    ":contexts:learning-resources:application",
    ":contexts:learning-resources:infrastructure",
    ":integrations:ai-gateway",
    ":integrations:persistence-shared",
)
