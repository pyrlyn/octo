plugins {
    id("octo.compose")
}

dependencies {
    api(project(":feature:common"))
    // Onboarding ends by handing a draft to the new-task dialog.
    api(project(":feature:newtask"))
    // Core's config edits come as JSON trees.
    implementation(libs.kotlinx.serialization.json)
    testImplementation(project(":testing"))
}
