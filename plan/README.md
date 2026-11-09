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
- **Agent transport.** ACP is the base transport (Cursor, Grok Build, Kimi Code, MiMo Code, Antigravity).
  Claude Code uses its native stream-json control protocol, Codex its `app-server` protocol and
  ZCode one stream-json `--prompt` run per turn, so Node is not
  a runtime dependency.
- **Storage (T66).** Everything Octo keeps between runs, except the configuration (`config.json`,
  the CLI's `cli.json`), lives in one SQLite database, `octo.db`, through Room 2.8.5 (the stable
  line) and the bundled SQLite driver. Room has no `mingwX64` build, so it sits in its own
  `storage` module behind the core's interfaces; the earlier JSON files are imported once.
- **Workflow.** The agent merges its own PR once tests and linters are green, then takes the next
  task.
- **macOS design (T63).** On macOS Octo draws with MacOS-Pyrlyn-UI, a native-looking macOS design, from the shared
  `kotlin-macos` package in pyrlyn/packages; Windows and Linux keep Pyrlyn Glass. Inter and Roboto Mono are
  bundled; the light palette is derived from the file's dark one. The composer and its pickers follow the
  creator's reference screenshots, and the window keeps the system's vibrancy behind see-through grounds.
