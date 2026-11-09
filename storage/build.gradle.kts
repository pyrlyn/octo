plugins {
    id("octo.kmp")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("androidx.room")
}

kotlin {
    // Room generates the actual of an expect object for every target.
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":core"))
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.room.runtime)
            implementation(libs.sqlite.bundled)
        }
        commonTest.dependencies {
            implementation(project(":testing"))
        }
    }
}

// Room's processor runs once per target, so each declared target gets it.
dependencies {
    kotlin.targets.names.filter { it != "metadata" }.forEach { target ->
        add("ksp${target.replaceFirstChar(Char::uppercase)}", libs.room.compiler)
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}
