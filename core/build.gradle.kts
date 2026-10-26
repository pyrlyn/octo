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
        commonTest.dependencies {
            implementation(libs.wasm.chasm)
        }
    }
}
