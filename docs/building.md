# Building and running

Needs JDK 25 (JDK 21 too, for kotlin-wasm) and two shared builds in the workspace's `packages/`
(`../../packages/` from the main checkout): `shared-settings` supplies ktlint, detekt, repositories and the optional remote
build cache; [kotlin-wasm](https://github.com/pyrlyn/packages/tree/main/kotlin-wasm) is the plugin host's
WebAssembly interface. Every directory above the build is searched for `packages/`, so a nested
worktree finds them too; set `SHARED_SETTINGS_DIR` or `KOTLIN_WASM_DIR` when they live elsewhere.

```bash
./gradlew check        # build, tests, ktlint, detekt
./gradlew :app:run     # open the desktop app
./gradlew :app:hotRun  # open it with Compose Hot Reload: changed UI code reloads into the window
```

Gradle runs on JDK 25 and downloads it on first run when it is missing
(`gradle/gradle-daemon-jvm.properties`). `hotRun` needs a JetBrains Runtime 25 and downloads one
into `~/.gradle/chr` the first time. In IntelliJ IDEA, set the project SDK and the Gradle JVM to a
JDK 25; a project last opened in Android Studio points at its bundled `jbr-25`, which IntelliJ does
not have.
