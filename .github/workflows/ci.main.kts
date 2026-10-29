#!/usr/bin/env kotlin
@file:Repository("https://repo.maven.apache.org/maven2/")
@file:DependsOn("io.github.typesafegithub:github-workflows-kt:4.0.0")

@file:Repository("https://bindings.krzeminski.it")
@file:DependsOn("actions:checkout:v7.0.1")
@file:DependsOn("actions:setup-java:v6.1.0")
@file:DependsOn("gradle:actions__setup-gradle:v6.4.0")
@file:DependsOn("actions:upload-artifact:v7")

import io.github.typesafegithub.workflows.actions.actions.Checkout
import io.github.typesafegithub.workflows.actions.actions.SetupJava
import io.github.typesafegithub.workflows.actions.actions.UploadArtifact
import io.github.typesafegithub.workflows.actions.gradle.ActionsSetupGradle
import io.github.typesafegithub.workflows.domain.Mode
import io.github.typesafegithub.workflows.domain.Permission
import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.domain.RunnerType.WindowsLatest
import io.github.typesafegithub.workflows.domain.triggers.PullRequest
import io.github.typesafegithub.workflows.domain.triggers.Push
import io.github.typesafegithub.workflows.domain.Shell
import io.github.typesafegithub.workflows.dsl.JobBuilder
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.CheckoutActionVersionSource
import io.github.typesafegithub.workflows.yaml.DEFAULT_CONSISTENCY_CHECK_JOB_CONFIG

// Shared builds from github.com/pyrlyn/packages, checked out by setUp; absolute, since the
// included kotlin-wasm build reads the same variables relative to its own directory.
val sharedBuilds =
    mapOf(
        "SHARED_SETTINGS_DIR" to "\${{ github.workspace }}/packages/shared-settings",
        "KOTLIN_WASM_DIR" to "\${{ github.workspace }}/packages/kotlin-wasm",
    )

fun JobBuilder<*>.setUp() {
    uses(action = Checkout())
    // Pinned release tag; bump when octo needs newer shared builds.
    uses(action = Checkout(repository = "pyrlyn/packages", ref = "0.0.1", path = "packages"))
    uses(action = SetupJava(distribution = SetupJava.Distribution.Temurin, javaVersion = "25"))
    uses(action = ActionsSetupGradle())
}

workflow(
    name = "CI",
    on = listOf(Push(branches = listOf("main")), PullRequest()),
    permissions = mapOf(Permission.Contents to Mode.Read),
    sourceFile = __FILE__,
    targetFileName = "ci.yml",
    consistencyCheckJobConfig =
        DEFAULT_CONSISTENCY_CHECK_JOB_CONFIG.copy(
            // The library's bundled default is older than the checkout the build pins.
            checkoutActionVersion = CheckoutActionVersionSource.InferFromClasspath(),
        ),
) {
    job(
        id = "check",
        runsOn = UbuntuLatest,
        env = sharedBuilds,
    ) {
        setUp()
        // Builds every native target the host can build, so commonMain stays free of JVM-only code.
        run(command = "./gradlew check -Pocto.allNativeTargets=true --console=plain")
    }
    // Renders the design sample at 100-200% on each desktop OS, to compare text and border sharpness.
    // Informational: nothing depends on it, and the PNGs are kept as an artifact.
    for ((os, runner) in listOf("linux" to UbuntuLatest, "windows" to WindowsLatest)) {
        job(
            id = "screenshots-$os",
            runsOn = runner,
            env = sharedBuilds + ("OCTO_SHOTS" to "\${{ github.workspace }}/shots"),
        ) {
            setUp()
            run(
                command = "./gradlew :design:jvmTest --tests octo.design.CrispShotsTest --console=plain",
                shell = Shell.Bash,
            )
            uses(action = UploadArtifact(name = "screenshots-$os", path = listOf("shots/")))
        }
    }
}
