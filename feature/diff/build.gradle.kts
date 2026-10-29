plugins {
    id("octo.compose")
}

dependencies {
    api(project(":feature:common"))
    implementation(libs.kotlinx.serialization.json)
    testImplementation(project(":testing"))
    // Skia's native library, so the tests can decode pictures.
    testImplementation(compose.desktop.currentOs)
}
