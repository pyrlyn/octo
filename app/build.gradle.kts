import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("octo.compose")
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
    implementation(project(":adapters:plugin"))
    implementation(libs.wasm.chasm)
    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutines.swing)
}

compose.desktop {
    application {
        mainClass = "octo.app.MainKt"
        // Skiko loads its native renderer with System.load; JDK 24+ warns about that and will block it.
        jvmArgs += "--enable-native-access=ALL-UNNAMED"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Octo"
            packageVersion = "1.0.0"
            // The agent-messaging MCP server runs on the JDK's built-in HTTP server.
            modules("jdk.httpserver")
        }
    }
}
