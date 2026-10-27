# T23. Done

- `OctoCore.switchAgent(task, agent, model)` keeps the worktree and branch. A new model for the
  same agent goes to the running session when the adapter has `ModelSelection` (Claude's
  `set_model` control request; Codex sends `model` with the next `turn/start`).
- Any other switch, or a refused one, stops the agent and starts a fresh session. The task keeps
  `handoffFrom`, and the next prompt goes out once wrapped in a handoff that `Handoff.build` makes
  from Octo's transcript within a token budget; the new request is never cut. Queued prompts take
  the same path.
- UI: a Switch agent dialog on the task screen.

PR: https://github.com/pyrlyn/octo/pull/31

Checked with `./gradlew check` after merging `main` with T21 and T22: 348 tests, 0 failures (core
JVM and macOS, adapters, UI), ktlint and detekt clean. GitHub CI did not run: the account's billing
blocked every job.
