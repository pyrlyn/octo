# T9. Done

## What was done

All in `core` (common code unless noted):

- `octo.core.git`
  - `Git` runs the `git` CLI through `ProcessLauncher`. The binary is resolved through the
    launcher's `PATH`. It passes `core.quotepath=off` so non-ASCII paths reach the parser as they are.
  - `Workspace`, implemented by `GitWorkspace`:
    - repository root;
    - `worktree add -b`;
    - dirty check (`status --porcelain`);
    - `worktree remove [--force]`, which never deletes the branch and prunes when the directory
      is already gone;
    - diff: `git diff <merge-base(base, HEAD)>` plus every untracked file through
      `diff --no-index /dev/null`.
  - `DiffParser` turns unified diff text into `FileDiff` and `Hunk` values. It handles added,
    deleted, renamed and binary files, paths with spaces, and "No newline" markers. Malformed
    input is skipped, never thrown on.
- `octo.core.task`
  - `TaskId`, `Task` (persisted), `NewTask`, `PendingRequest`, `TaskView`, `Layout`, `OctoState`.
  - `SidebarStatus` and `rollup()` follow the ladder Blocked > Failed > Working > Starting > Done >
    Idle > Stopped. Done means idle and not yet seen.
  - `Transcript`, a pure reducer:
    - deltas stream into one partial message, which the final message replaces;
    - tool calls and the plan update in place;
    - requests are marked resolved;
    - errors, cancels and exits become notices;
    - the transcript is bounded to 500 entries.
- `octo.core.persist.StateStore` writes `state.json` through `AtomicFile`.
  - It keeps `state.json.1..3`.
  - On load, a corrupt file is moved to `*.corrupt` and the newest readable backup is used, with
    a warning. Loading never fails.
  - Unknown fields are ignored. Partial text is never persisted, and each transcript tail is
    capped at 200 entries.
- `octo.core.orchestrator`
  - `OctoCore` is the UI-facing API: state, transcript, create, prompt, respond, cancel, stop,
    restart, remove, diff, layout, markSeen.
  - `Orchestrator` implements it:
    - one supervisor scope per task, so a crash in one agent stays in that task;
    - lifecycle calls for the same task are serialized by a per-task lock;
    - `TermsGate.requireAcknowledged` runs before every start, and `createTask` checks it before
      touching the repository;
    - worktrees go to `<octo home>/worktrees/<repo>/<slug>-<id>` on the branch `octo/<slug>-<id>`;
    - restart resumes through the stored `AgentSessionRef` when the adapter has `Resume`;
      otherwise it starts fresh and adds a notice saying so;
    - a failed start shows on the task as Failed instead of throwing;
    - a dirty worktree is removed only with `force`;
    - cold start restores every task as Stopped;
    - changes are saved in the background (conflated), and only when the persisted form changes;
    - `close()` stops every agent and writes the final state. It belongs to `app`, so it is not
      on `OctoCore`.
- `docs/architecture.md` §7 now matches the API.

## How it was checked

- `./gradlew check -Pocto.allNativeTargets=true`: JVM and native tests, ktlint and detekt are all
  green.
- New tests:
  - `DiffParserTest` (2);
  - `TranscriptTest` (4, including the rollup);
  - `StateStoreTest` (3: round trip with three backups, corrupt-file fallback, unknown fields);
  - `OrchestratorTest` (8, over a fake adapter and workspace: create, block, respond, done/seen,
    stop, restart with and without resume, crash isolation, failed start, the terms gate,
    dirty-worktree removal, state surviving a restart);
  - `GitWorkspaceTest` (JVM, 2), against a temp git repo with a temp `HOME`, `XDG_CONFIG_HOME`
    and `GIT_CONFIG_GLOBAL`, and with `GIT_CONFIG_NOSYSTEM` set.
- CI on `pyrlyn/octo` is blocked by GitHub billing, so the PR has no CI run.

## Not verified

- Windows: `git diff --no-index /dev/null <file>` relies on Git for Windows mapping `/dev/null`.
  This is not tested here; the git tests run on macOS only.
- Native targets have no `ProcessLauncher` yet, so the git layer is exercised on the JVM only.
  The orchestrator, store and parsers run on native too.

## Notes for T10

- Test code that drives `Orchestrator` from `runTest` must advance with `testScheduler.runCurrent()`.
  `TestScope.advanceUntilIdle()` stops once only `backgroundScope` work is left, so the event
  collectors never run.
- The UI shows `TermsGate.pending(adapter)` and calls `acknowledge` on the user's OK, then
  retries `createTask`.

PR: https://github.com/pyrlyn/octo/pull/12
