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
    ":adapters:antigravity",
    ":adapters:base",
    ":adapters:claude",
    ":adapters:codex",
    ":adapters:cursor",
    ":adapters:grok",
    ":ui",
    ":app",
)
