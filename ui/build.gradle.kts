// The shell: window, sidebar, toolbar and tray, wiring every feature's view model together.
plugins {
    id("octo.compose")
}

dependencies {
    api(project(":feature:agents"))
    api(project(":feature:devices"))
    api(project(":feature:diff"))
    api(project(":feature:newtask"))
    api(project(":feature:onboarding"))
    api(project(":feature:palette"))
    api(project(":feature:settings"))
    api(project(":feature:task"))
    api(project(":feature:usage"))
    testImplementation(project(":testing"))
    // Skia's native library, so the tests can decode pictures.
    testImplementation(compose.desktop.currentOs)
    testImplementation(libs.wasm.chasm)
}

tasks.test {
    // The window code calls AppKit through the Foreign Function API, as the app does.
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}
