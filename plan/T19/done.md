# T19. Done

Added `adapters:kimi`: `KimiAdapter` runs Kimi Code CLI through `kimi acp` on the shared ACP client,
retries a session with `authenticate("login")` (which only re-checks the stored token), and refuses
the archived Python `kimi-cli` by its `kimi, version 1.x` output. It is registered in the app and
gets Octo's MCP servers, so agent messaging works for it. Research with sources is in
`docs/research.md` §6.7 and §6.6; README, `docs/architecture.md` and `plan/README.md` list it.

PR: https://github.com/pyrlyn/octo/pull/22

Checked: `./gradlew check` passes locally (the three `KimiAdapterTest` tests and `AdaptersTest`).
The fixture's `initialize` reply was recorded from `kimi` 0.39.1 with `KIMI_CODE_HOME` pointed at a
temp dir. GitHub Actions did not run on the PR: the account's billing blocked the job before it
started. No live session with a signed-in account was run.
