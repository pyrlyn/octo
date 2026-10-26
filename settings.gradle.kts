pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

rootProject.name = "octo"

include(
    ":core",
    ":testing",
    ":adapters:base",
    ":adapters:claude",
    ":ui",
    ":app",
)
