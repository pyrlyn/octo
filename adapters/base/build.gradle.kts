plugins {
    id("octo.kmp")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core"))
            api(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(project(":testing"))
        }
    }
}
