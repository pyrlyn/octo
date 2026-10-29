plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
    application
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.clikt)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(project(":testing"))
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
}

// Clikt's terminal detection loads JNA's native library; without this every run warns on stderr.
val nativeAccess = "--enable-native-access=ALL-UNNAMED"

application {
    mainClass = "octo.cli.MainKt"
    applicationName = "octo"
    applicationDefaultJvmArgs = listOf(nativeAccess)
}

tasks.test {
    jvmArgs(nativeAccess)
}
