plugins {
    id("octo.compose")
}

dependencies {
    api(project(":core"))
    api(compose.runtime)
    api(compose.foundation)
    api(compose.material3)
    api(compose.ui)
}
