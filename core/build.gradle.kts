plugins {
    id("octo.kmp")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.io.core)
            implementation(libs.kotlinx.serialization.json)
            api(libs.wasm)
        }
        jvmMain.dependencies {
            // Cron parsing for triggers; it has no macosArm64 build, so common code sees only Cron.
            implementation(libs.krontab)
        }
        commonTest.dependencies {
            implementation(libs.wasm.chasm)
        }
    }
}
