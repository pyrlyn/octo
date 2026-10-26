---
status: todo
priority: P0
complexity: 4
readiness: 0%
agent:
---

# T3. Project skeleton

Gradle (Kotlin DSL) build with the modules from the architecture: `core`, `adapters:base`, one module per agent, `ui`, `app`. Base adapter, adapter registry, feature-flag system, an empty Compose window, ktlint, detekt and CI. Done when the build, tests and linters pass locally and in CI.

Check: `./gradlew check` is green locally and in CI; the app opens an empty window.
