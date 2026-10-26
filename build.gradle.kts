// ktlint and detekt come from ci-diffuser, so every Kotlin project lints the same way. Octo's own
// detekt overrides stay in config/detekt/detekt.yml, which ci-diffuser merges after its shared file.
plugins {
    id("ci.diffuser.ktlint") apply false
    id("ci.diffuser.detekt") apply false
}

subprojects {
    listOf("org.jetbrains.kotlin.multiplatform", "org.jetbrains.kotlin.jvm").forEach { kotlin ->
        pluginManager.withPlugin(kotlin) {
            pluginManager.apply("ci.diffuser.ktlint")
            pluginManager.apply("ci.diffuser.detekt")
        }
    }
}
