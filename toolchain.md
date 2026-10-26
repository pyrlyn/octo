# Toolchain

| Program | How to install | Why here | Source |
| --- | --- | --- | --- |
| JDK 21 (Temurin) | mise | Runs Gradle and the desktop app | https://adoptium.net |
| Gradle 9.7.1 | wrapper (`./gradlew`) | Build | https://github.com/gradle/gradle |
| Kotlin 2.4.21 | Gradle plugin | Language, Kotlin Multiplatform | https://github.com/JetBrains/kotlin |
| git | system | Version control, worktrees per task | https://git-scm.com |
| gh | brew | Repository and PRs | https://github.com/cli/cli |
| ci-diffuser | sibling checkout at `../ci-diffuser` (or `CI_DIFFUSER_DIR`), local included build | Shared ktlint, detekt and settings conventions | `apps/ci-diffuser` (no remote) |
| kotlin-wasm | checkout at `../../packages/kotlin-wasm` (or `KOTLIN_WASM_DIR`), local included build | The `wasm` interface and its Chasm adapter, shared with rtok | https://github.com/pyrlyn/kotlin-wasm |
| Rust (cargo, `wasm32-unknown-unknown` target) | rustup | Builds the demo plugin test fixtures (`core/src/commonTest/fixtures/plugins/build.sh`); not needed to build Octo | https://github.com/rust-lang/rust |

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
| org.jlleitschuh.gradle.ktlint 14.2.0 (ktlint 1.8.0) | via ci-diffuser | https://github.com/JLLeitschuh/ktlint-gradle | Formatting lint (`ci.diffuser.ktlint`) |
| dev.detekt 2.0.0-alpha.6 | via ci-diffuser | https://github.com/detekt/detekt | Static analysis (`ci.diffuser.detekt`) |
| pyrlyn.wasm:wasm | local (included build) | https://github.com/pyrlyn/kotlin-wasm | The engine-neutral WebAssembly interface the plugin loader uses |
| pyrlyn.wasm:wasm-chasm (Chasm 2.3.0, `io.github.charlietap.chasm:chasm`) | local (included build) | https://github.com/CharlieTap/chasm | Runs plugins in a WebAssembly interpreter |
| com.github.burrunan.s3-build-cache 1.9.10 | local (settings) | https://github.com/burrunan/gradle-s3-build-cache | Optional S3/R2 remote build cache (`ci.diffuser.settings`); off without its keys |
