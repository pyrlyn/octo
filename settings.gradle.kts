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
    ":ui",
    ":app",
)
