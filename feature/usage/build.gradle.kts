plugins {
    id("octo.compose")
}

dependencies {
    api(project(":feature:common"))
    testImplementation(project(":testing"))
}
