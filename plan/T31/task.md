---
status: in progress
priority: P3
complexity: 2
readiness: 10%
agent: Claude Code / claude-opus-5-5
---

# T31. Command-line client

Scripts and other tools cannot drive Octo without the UI. T3 Code has `t3 thread` to start,
message and read threads from a terminal
([955b4cc](https://github.com/pingdotgg/t3code/commit/955b4cc)).

Behind a feature flag. Done: an `octo` CLI over the core API (the loopback API from T15) with
`task list`, `task start`, `task send`, `task read` and `task wait`, JSON output on request,
token auth as for paired devices. Tests run it against a fake core; `./gradlew check` passes.

## Execution plan

The creator's decisions: the CLI is a client of the `remote-access` loopback server, so that
flag gates it and there is no new one; `octo pair <code>` stores the URL and token in a file
only the user can read, and `OCTO_URL` / `OCTO_TOKEN` override it; Clikt parses the arguments.

1. Core: the API's request and response shapes move out of `RemoteApi` into public types
   that the server and the CLI share. `OctoPaths` gains `cli.json`.
2. `:testing`: `FakeOctoCore` and `FakeCatalog` move there from the ui tests, so the CLI tests
   use the same fake core.
3. New `:cli` module (JVM, `application`, binary `octo`): a JDK `HttpClient` wrapper with
   bearer auth, the stored credentials (mode 0600), and the commands `pair`, `task list`,
   `task start`, `task send`, `task read` and `task wait`, each with `--json`.
   `task wait` polls until the turn ends: exit 0 idle, 1 failed or stopped, 2 blocked on the
   user, 124 on `--timeout`.
4. Tests: every command against the real loopback server over `FakeOctoCore`, with the
   credentials file in a temp `OCTO_HOME`; a server that is not listening gives a clear error.
5. Docs: README, architecture, toolchain (Clikt), done.md, PR.
