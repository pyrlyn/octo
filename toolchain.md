# Toolchain

| Program | How to install | Why here | Source |
| --- | --- | --- | --- |
| JDK 25 (Temurin) | mise | Runs Gradle and the desktop app | https://adoptium.net |
| Gradle 9.7.1 | wrapper (`./gradlew`) | Build | https://github.com/gradle/gradle |
| Kotlin 2.4.21 | Gradle plugin | Language, Kotlin Multiplatform | https://github.com/JetBrains/kotlin |
| git | system | Version control, worktrees per task | https://git-scm.com |
| gh | brew | Repository and PRs | https://github.com/cli/cli |
| shared-settings | `packages/shared-settings` above the build (or `SHARED_SETTINGS_DIR`), local included build | Shared ktlint, detekt and settings conventions | `packages/shared-settings` (no remote) |
| kotlin-wasm | `packages/kotlin-wasm` above the build (or `KOTLIN_WASM_DIR`), local included build | The `wasm` interface and its Chasm adapter, shared with rtok | https://github.com/pyrlyn/kotlin-wasm |
| Rust (cargo, `wasm32-unknown-unknown` target) | rustup | Builds the demo plugin test fixtures (`core/src/commonTest/fixtures/plugins/build.sh`); not needed to build Octo | https://github.com/rust-lang/rust |
| sqlite3 | system (optional) | Reads SQLite databases for the `previews` flag; without it they are not previewed | https://sqlite.org |
| mermaid-cli (`mmdc`) | npm (optional) | Draws Mermaid diagrams for the `previews` flag; without it the source shows | https://github.com/mermaid-js/mermaid-cli |

## Gradle

| Package | Where | Source | Why here |
| --- | --- | --- | --- |
| org.jetbrains.kotlinx:kotlinx-coroutines-core 1.11.0 | local | https://github.com/Kotlin/kotlinx.coroutines | Concurrency, flows |
| org.jetbrains.kotlinx:kotlinx-coroutines-swing 1.11.0 | local | https://github.com/Kotlin/kotlinx.coroutines | Main dispatcher for the desktop app |
| org.jetbrains.kotlinx:kotlinx-coroutines-test 1.11.0 | local | https://github.com/Kotlin/kotlinx.coroutines | Tests |
| org.jetbrains.kotlinx:kotlinx-serialization-json 1.12.0 | local | https://github.com/Kotlin/kotlinx.serialization | Agent protocols, config, state, notebook previews |
| org.jetbrains.kotlinx:kotlinx-io-core 0.9.1 | local | https://github.com/Kotlin/kotlinx-io | Files and paths in common code |
| com.github.ajalt.clikt:clikt 5.1.0 | local | https://github.com/ajalt/clikt | Arguments, help and errors of the `octo` command |
| dev.inmo:krontab 3.1.0 | local | https://github.com/InsanusMokrassar/krontab | Matching cron schedules of triggers (JVM only) |
| com.soywiz.korge:korlibs-time 5.4.0 | local (through krontab) | https://github.com/korlibs/korlibs-time | The date type krontab takes and returns |
| org.jetbrains.kotlin:kotlin-test | local | https://github.com/JetBrains/kotlin | Tests |
| Compose Multiplatform 1.12.2 (`org.jetbrains.compose`) | local | https://github.com/JetBrains/compose-multiplatform | Desktop UI |
| org.jlleitschuh.gradle.ktlint 14.2.0 (ktlint 1.8.0) | via shared-settings | https://github.com/JLLeitschuh/ktlint-gradle | Formatting lint (`shared.settings.ktlint`) |
| dev.detekt 2.0.0-alpha.6 | via shared-settings | https://github.com/detekt/detekt | Static analysis (`shared.settings.detekt`) |
| pyrlyn.wasm:wasm | local (included build) | https://github.com/pyrlyn/kotlin-wasm | The engine-neutral WebAssembly interface the plugin loader uses |
| pyrlyn.wasm:wasm-chasm (Chasm 2.3.0, `io.github.charlietap.chasm:chasm`) | local (included build) | https://github.com/CharlieTap/chasm | Runs plugins in a WebAssembly interpreter |
| com.github.burrunan.s3-build-cache 1.9.10 | local (settings) | https://github.com/burrunan/gradle-s3-build-cache | Optional S3/R2 remote build cache (`shared.settings`); off without its keys |

## Bundled assets

| Asset | Where | Source | Why here |
| --- | --- | --- | --- |
| IBM Plex Sans 6.4.0 (Regular, Medium, SemiBold, Bold; OFL 1.1) | `design/src/jvmMain/resources/fonts` | https://github.com/IBM/plex | UI face of the Pyrlyn Glass design |
| JetBrains Mono 2.304 (Regular, Medium, Bold; OFL 1.1) | `design/src/jvmMain/resources/fonts` | https://github.com/JetBrains/JetBrainsMono | Code, paths and commands |
