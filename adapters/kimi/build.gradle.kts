plugins {
    id("octo.kmp")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":adapters:base"))
        }
        commonTest.dependencies {
            implementation(project(":testing"))
        }
    }
}
