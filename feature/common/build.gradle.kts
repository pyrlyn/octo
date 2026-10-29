// App-level pieces several features share: they know Octo's types, unlike the design system.
plugins {
    id("octo.compose")
}

dependencies {
    api(project(":core"))
    api(project(":design"))
}
