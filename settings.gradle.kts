pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
    // Shared builds live in the workspace's packages/. Every ancestor is searched, so the build works
    // from the main checkout and from a worktree nested deeper (e.g. .rtok/worktrees/); an env variable
    // points elsewhere. Defined here because pluginManagement cannot see the rest of the script.
    fun sharedBuild(name: String, env: String): String =
        System.getenv(env)?.let { settingsDir.resolve(it).path }
            ?: generateSequence(settingsDir.parentFile) { it.parentFile }
                .map { it.resolve("packages/$name") }
                .firstOrNull { it.isDirectory }
                ?.path
            ?: error("packages/$name not found above $settingsDir; set $env")

    // Shared build settings (ktlint, detekt, repositories, build cache).
    val sharedSettings = sharedBuild("shared-settings", "SHARED_SETTINGS_DIR")
    includeBuild(sharedSettings)
    includeBuild("$sharedSettings/settings-plugin")
    extra["kotlinWasmDir"] = sharedBuild("kotlin-wasm", "KOTLIN_WASM_DIR")
}

// Repositories and the optional S3/R2 remote build cache, which stays off without its keys.
plugins {
    id("com.github.burrunan.s3-build-cache") version "1.9.10"
    id("shared.settings")
}

rootProject.name = "octo"

// The WebAssembly interface and its Chasm adapter, shared with rtok: a checkout of kotlin-wasm
// (github.com/pyrlyn/packages, kotlin-wasm/).
includeBuild(extra["kotlinWasmDir"] as String)

include(
    ":core",
    ":testing",
    ":adapters:antigravity",
    ":adapters:base",
    ":adapters:claude",
    ":adapters:codex",
    ":adapters:cursor",
    ":adapters:demo",
    ":adapters:grok",
    ":adapters:kimi",
    ":adapters:mimo",
    ":adapters:plugin",
    ":adapters:zcode",
    ":design",
    ":feature:common",
    ":feature:agents",
    ":feature:devices",
    ":feature:diff",
    ":feature:newtask",
    ":feature:onboarding",
    ":feature:palette",
    ":feature:settings",
    ":feature:task",
    ":feature:usage",
    ":ui",
    ":app",
    ":cli",
)
