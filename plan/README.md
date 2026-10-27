# Octo

https://github.com/pyrlyn/octo

Desktop orchestrator for CLI coding agents, in Kotlin.

## Decisions

Taken by the creator after the Phase 1 research (`docs/research.md`):

- **UI target — option A.** Compose Multiplatform desktop (JVM) for the UI. The core and the
  adapters are Kotlin Multiplatform with `commonMain` code compiled for `jvm`, `macosArm64`,
  `linuxX64` and `mingwX64`, so a native headless daemon can reuse them later.
- **Vendor terms.** Octo spawns the agent CLI the user installed, under the user's own login. Octo
  never reads, stores or forwards agent credentials. Antigravity sits behind a feature flag that
  shows the vendor's terms before first use.
- **Agent transport.** ACP is the base transport (Cursor, Grok Build, Kimi Code, Antigravity). Claude Code
  uses its native stream-json control protocol and Codex its `app-server` protocol, so Node is not
  a runtime dependency.
- **Workflow.** The agent merges its own PR once tests and linters are green, then takes the next
  task.
