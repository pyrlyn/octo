plugins {
    id("octo.compose")
}

dependencies {
    api(project(":feature:common"))
    // Core's config edits come as JSON trees.
    implementation(libs.kotlinx.serialization.json)
    testImplementation(project(":testing"))
}
