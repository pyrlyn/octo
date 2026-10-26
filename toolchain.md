# Toolchain

| Program | How to install | Why here | Source |
| --- | --- | --- | --- |
| JDK 21 (Temurin) | mise | Runs Gradle and the desktop app | https://adoptium.net |
| Gradle 9.7.1 | wrapper (`./gradlew`) | Build | https://github.com/gradle/gradle |
| Kotlin 2.4.21 | Gradle plugin | Language, Kotlin Multiplatform | https://github.com/JetBrains/kotlin |
| git | system | Version control, worktrees per task | https://git-scm.com |
| gh | brew | Repository and PRs | https://github.com/cli/cli |

## Gradle

| Package | Where | Source | Why here |
| --- | --- | --- | --- |
| org.jetbrains.kotlinx:kotlinx-coroutines-core 1.11.0 | local | https://github.com/Kotlin/kotlinx.coroutines | Concurrency, flows |
| org.jetbrains.kotlinx:kotlinx-coroutines-swing 1.11.0 | local | https://github.com/Kotlin/kotlinx.coroutines | Main dispatcher for the desktop app |
| org.jetbrains.kotlinx:kotlinx-coroutines-test 1.11.0 | local | https://github.com/Kotlin/kotlinx.coroutines | Tests |
| org.jetbrains.kotlinx:kotlinx-serialization-json 1.12.0 | local | https://github.com/Kotlin/kotlinx.serialization | Agent protocols, config, state |
| org.jetbrains.kotlinx:kotlinx-io-core 0.9.1 | local | https://github.com/Kotlin/kotlinx-io | Files and paths in common code |
| org.jetbrains.kotlin:kotlin-test | local | https://github.com/JetBrains/kotlin | Tests |
| Compose Multiplatform 1.12.2 (`org.jetbrains.compose`) | local | https://github.com/JetBrains/compose-multiplatform | Desktop UI |
| org.jlleitschuh.gradle.ktlint 14.2.0 (ktlint 1.8.0) | local | https://github.com/JLLeitschuh/ktlint-gradle | Formatting lint |
| dev.detekt 2.0.0-alpha.6 | local | https://github.com/detekt/detekt | Static analysis |
