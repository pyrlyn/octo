---
status: in progress
priority: P0
complexity: 4
readiness: 90%
agent: Claude Code / claude-opus-5-5
---

# T3. Project skeleton

Gradle (Kotlin DSL) build with the modules from the architecture: `core`, `adapters:base`, one module per agent, `ui`, `app`. Base adapter, adapter registry, feature-flag system, an empty Compose window, ktlint, detekt and CI. Done when the build, tests and linters pass locally and in CI.

Check: `./gradlew check` is green locally and in CI; the app opens an empty window.

## Execution plan

1. `buildSrc` convention plugins (`octo.kmp`, `octo.compose`, `octo.lint`), version catalog,
   Gradle wrapper 9.7.1, `.editorconfig`, detekt config.
2. `core`: agent SPI (`AgentAdapter`, `AgentSession`, `AgentEvent`), `AdapterRegistry`,
   `FeatureFlag`/`FeatureFlags`, `OctoConfig`, the status reducer, `ProcessLauncher` with
   `JvmProcessLauncher`, `OctoPaths`.
3. `testing`: `FakeProcessLauncher` and transcript replay for fixture tests.
4. `adapters:base`: `BaseAgentAdapter`, `ProcessAgentSession`, `JsonRpcPeer`. The ACP client and
   the stream-JSON base land with the first adapter that needs them (T6, T4).
5. `ui` (empty `OctoApp`) and `app` (`main`, packaging).
6. CI workflow on `ubuntu-latest` with all native targets.
7. Verify: `./gradlew check`, `./gradlew :app:run`.
