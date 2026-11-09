import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest

plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

val catalog = versionCatalogs.named("libs")

// Native targets keep commonMain honest for the future headless daemon. Only the host target is
// built by default because every extra Kotlin/Native target costs minutes; CI turns all of them on.
val allNativeTargets = providers.gradleProperty("octo.allNativeTargets").map { it.toBoolean() }.getOrElse(false)
val os = System.getProperty("os.name").lowercase()

// A module whose libraries have no mingwX64 build sets `octo.windowsNative=false` in its own
// gradle.properties; Windows still runs it on the JVM.
val windowsNative = (findProperty("octo.windowsNative") as String?)?.toBoolean() ?: true

kotlin {
    jvmToolchain(25)
    jvm()
    if (allNativeTargets || os.contains("mac")) macosArm64()
    if (allNativeTargets || os.contains("linux")) linuxX64()
    if (windowsNative && (allNativeTargets || os.contains("windows"))) mingwX64()

    compilerOptions {
        allWarningsAsErrors.set(true)
    }

    sourceSets {
        commonMain.dependencies {
            implementation(catalog.findLibrary("kotlinx-coroutines-core").get())
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(catalog.findLibrary("kotlinx-coroutines-test").get())
        }
    }
}

// Tests read fixtures by path relative to the module, so every test runs from the module directory.
tasks.withType<Test>().configureEach { workingDir = projectDir }
tasks.withType<KotlinNativeTest>().configureEach { workingDir = projectDir.absolutePath }
