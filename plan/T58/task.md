---
status: in progress
priority: P2
complexity: 3
readiness: 100%
agent: Claude Code / claude-opus-5-5
---

# T58. MiMo Code and ZCode adapters

The creator asked for two more agents: Xiaomi's MiMo Code (`mimo`, an OpenCode fork with a native
ACP server) and Z.ai's ZCode (`zcode`, a terminal harness whose documented headless mode is one
`--prompt` run per turn with `--output-format stream-json`). Research is in `docs/research.md`
§6.8 and §6.9.

Done means: an `adapters:mimo` module drives `mimo acp` through the shared ACP client, an
`adapters:zcode` module runs each turn as `zcode --prompt=… --output-format stream-json` and
resumes the session with `--resume`, both are registered in the app, both are tested against
synthetic transcripts built from the CLIs' source, and the docs list them.

## Execution plan

1. Research both CLIs from primary sources and write `docs/research.md` §6.8 (MiMo Code) and
   §6.9 (ZCode), plus rows in §6.6.
2. `adapters/base`: move binary lookup, the version probe and the binary check out of
   `BaseAgentAdapter` into `CliAgentAdapter`, so an adapter that starts one process per turn can
   reuse them; `BaseAgentAdapter` keeps the one-process session start. No behaviour change.
3. `adapters/mimo`: `MimoAdapter : AcpAgentAdapter`, binary `mimo`, args `acp`, no login method
   (MiMo's `opencode-login` is an interactive sign-in).
4. `adapters/zcode`: `ZCodeAdapter : CliAgentAdapter` and `ZCodeSession`, one `zcode` process per
   prompt; maps `model.streaming`, `tool.updated`, `turn.completed`, `turn.failed` and the closing
   `result` line; permission modes Supervised → `build`, Auto-accept edits → `edit`,
   Full access → `yolo`; cancel kills the turn's process.
5. Tests with `FakeProcessLauncher`: an ACP turn for MiMo; for ZCode a turn with text, a tool call
   and usage, a resumed second turn, a failed turn, cancel, and the mode flags.
6. Register both in `settings.gradle.kts`, `app/build.gradle.kts`, `Adapters.kt` and
   `AdaptersTest`; update `README.md`, `docs/architecture.md` and `toolchain.md`.
7. `./gradlew check` clean, then a PR.
