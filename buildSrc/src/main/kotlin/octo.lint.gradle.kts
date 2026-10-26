plugins {
    id("org.jlleitschuh.gradle.ktlint")
    id("dev.detekt")
}

val catalog = versionCatalogs.named("libs")

ktlint {
    version.set(catalog.findVersion("ktlint").get().requiredVersion)
}

detekt {
    buildUponDefaultConfig.set(true)
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
}

// On multiplatform modules the plain `detekt` task has no sources; the per-source-set tasks do
// the work but are not part of `check` by default.
tasks.named("check") {
    dependsOn(
        tasks.matching {
            it.name.startsWith("detekt") && it.name.endsWith("SourceSet") && !it.name.startsWith("detektBaseline")
        },
    )
}
