plugins {
    id("octo.compose")
}

dependencies {
    api(project(":core"))
    api(compose.runtime)
    api(compose.foundation)
    api(compose.material3)
    api(compose.ui)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(project(":testing"))
    // Skia's native library, so the tests can decode pictures.
    testImplementation(compose.desktop.currentOs)
    testImplementation(libs.wasm.chasm)
}
