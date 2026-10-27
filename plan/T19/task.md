---
status: in progress
priority: P2
complexity: 2
readiness: 80%
agent: Claude Code / claude-opus-5-5
---

# T19. Kimi Code adapter

Moonshot's Kimi Code CLI (`kimi`) is a CLI coding agent with a native ACP server, so Octo can run
it like Grok and Antigravity. Research is in `docs/research.md` §6.7.

Done means: an `adapters:kimi` module drives `kimi acp` through the shared ACP client, reuses the
CLI's own login, refuses the archived Python `kimi-cli` that installs the same binary name, is
registered in the app, and is tested against recorded and synthetic ACP transcripts.

## Execution plan

1. Research the CLI (docs, repository, ACP server source, a live `initialize` against a temp
   `KIMI_CODE_HOME`) and write `docs/research.md` §6.7 plus a row in §6.6.
2. `adapters/kimi`: `KimiAdapter : AcpAgentAdapter` with binary `kimi`, args `acp`, login method
   `login`, and a version check that accepts only Kimi Code's bare version line.
3. Tests with `FakeProcessLauncher`: a turn over a transcript whose `initialize` reply is recorded
   from `kimi` 0.39.1, the auth retry through `authenticate("login")`, and refusing the legacy
   CLI's `kimi, version 1.x` output.
4. Register the module in `settings.gradle.kts`, `app/build.gradle.kts` and `Adapters.kt`; update
   `README.md`, `docs/architecture.md` and `toolchain.md`.
5. `./gradlew check`, then a PR.
