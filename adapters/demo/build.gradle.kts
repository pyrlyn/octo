plugins {
    id("octo.kmp")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core"))
        }
        commonTest.dependencies {
            implementation(project(":testing"))
        }
    }
}
