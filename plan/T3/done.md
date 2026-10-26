# T3. Done

- Build: `buildSrc` convention plugins (`octo.kmp`, `octo.compose`, `octo.lint`), version catalog,
  Gradle wrapper 9.7.1, ktlint 1.8.0 and detekt 2.0.0-alpha.6 wired into `check` (detekt per
  source set on multiplatform modules), warnings as errors.
- `core`: agent SPI, adapter registry with flag-gated adapters, feature flags with capability
  requirements, `config.json` parsing that falls back to defaults, the status reducer
  (blocked until every open request is answered), `ProcessLauncher` with a JVM implementation
  that starts children from an explicit environment and kills the process tree, `OctoPaths`.
- `testing`: `FakeProcessLauncher` and a transcript format (`>` expected input, `<` stdout,
  `!` stderr, `x` exit) for fixture tests without real agents.
- `adapters:base`: `BaseAgentAdapter` (binary lookup, version probe with drift flag, auth hook,
  session start with cleanup on a failed handshake), `ProcessAgentSession`, `JsonRpcPeer`
  (optional `jsonrpc` field, concurrent incoming requests, ordered notifications).
- `ui` and `app`: an empty Compose window titled Octo, packaging for dmg, msi and deb.
- CI: `.github/workflows/ci.yml` runs `./gradlew check -Pocto.allNativeTargets=true`.

Checked: `./gradlew check` green locally (jvm and macosArm64 tests); `./gradlew :app:run` opened
the window without errors; CI on the PR.
