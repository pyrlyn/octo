import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.reload.gradle.ComposeHotRun

// Skiko loads its native renderer with System.load; JDK 24+ warns about that and will block it.
val nativeAccess = "--enable-native-access=ALL-UNNAMED"

plugins {
    id("octo.compose")
    // hotRun: reloads changed UI code into the running app; needs a JetBrains Runtime.
    id("org.jetbrains.compose.hot-reload")
}

dependencies {
    implementation(project(":ui"))
    implementation(project(":adapters:antigravity"))
    implementation(project(":adapters:claude"))
    implementation(project(":adapters:codex"))
    implementation(project(":adapters:cursor"))
    implementation(project(":adapters:demo"))
    implementation(project(":adapters:grok"))
    implementation(project(":adapters:kimi"))
    implementation(project(":adapters:mimo"))
    implementation(project(":adapters:plugin"))
    implementation(project(":adapters:zcode"))
    implementation(libs.wasm.chasm)
    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutines.swing)
}

compose.desktop {
    application {
        mainClass = "octo.app.MainKt"
        // Run and package on the toolchain's JDK, not whichever JDK runs Gradle: the classes need 25.
        javaHome =
            javaToolchains
                .launcherFor { languageVersion = JavaLanguageVersion.of(25) }
                .get()
                .metadata.installationPath.asFile.absolutePath
        jvmArgs += nativeAccess
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Octo"
            packageVersion = "1.0.0"
            // The agent-messaging MCP server runs on the JDK's built-in HTTP server.
            modules("jdk.httpserver")
        }
    }
}

// hotRun starts its own JVM and ignores compose.desktop's jvmArgs.
tasks.withType<ComposeHotRun>().configureEach {
    jvmArgumentProviders += CommandLineArgumentProvider { listOf(nativeAccess) }
}
