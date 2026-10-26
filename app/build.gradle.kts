import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("octo.compose")
}

dependencies {
    implementation(project(":ui"))
    implementation(project(":adapters:claude"))
    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutines.swing)
}

compose.desktop {
    application {
        mainClass = "octo.app.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Octo"
            packageVersion = "1.0.0"
        }
    }
}
