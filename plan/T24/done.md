# T24. Done

- `OctoCore.forkTask` makes a new task on a new branch cut from the parent's HEAD, with the
  parent's uncommitted and untracked files copied over through a separate git index, so the parent
  is untouched. The fork keeps the parent's agent, options, permission mode and transcript, and
  `Task.parentId` nests it under the parent in the sidebar.
- An adapter with `Capability.Fork` copies its own session (Claude: `--resume --fork-session` with
  an Octo-chosen `--session-id`; Codex: `thread/fork`). Other agents start fresh and get T23's
  handoff with the next prompt. The parent's session is never stored on the fork.
- UI: a Fork action on the task screen, behind the `fork` feature flag.

PR: https://github.com/pyrlyn/octo/pull/32

Checked with `./gradlew check` after merging T21–T23: 362 tests, 0 failures (core JVM and macOS,
adapters, UI), ktlint and detekt clean. The git snapshot is tested against a real repository.
GitHub CI did not run: the account's billing blocked every job.
