// ktlint and detekt come from shared-settings, so every Kotlin project lints the same way. Octo's own
// detekt overrides stay in config/detekt/detekt.yml, which shared-settings merges after its shared file.
plugins {
    id("shared.settings.ktlint") apply false
    id("shared.settings.detekt") apply false
}

subprojects {
    listOf("org.jetbrains.kotlin.multiplatform", "org.jetbrains.kotlin.jvm").forEach { kotlin ->
        pluginManager.withPlugin(kotlin) {
            pluginManager.apply("shared.settings.ktlint")
            pluginManager.apply("shared.settings.detekt")
        }
    }
}
