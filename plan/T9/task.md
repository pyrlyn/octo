---
status: in progress
priority: P1
complexity: 4
readiness: 100%
agent: Claude Code / claude-opus-5-5
---

# T9. Core orchestration

Orchestrator in the core: tasks, each with its own git worktree and branch; parallel agent sessions; status tracking and rollup; prompts, answers, stop and restart; persisted sessions and layout restored on restart; diff of a task against its base.

Check: Core tests cover worktree lifecycle (against a temp git repo), persistence round-trip, status reducer and diff.

## Execution plan

1. `octo.core.git`: `Git` (runs the `git` CLI through `ProcessLauncher`), `Workspace` with the
   `GitWorkspace` implementation (repo root, worktree add, dirty check, remove, diff), and a
   common-code unified diff parser.
2. `octo.core.task`: `TaskId`, `Task`, `NewTask`, `Layout`, `PendingRequest`, `TaskView`,
   `OctoState`, the sidebar status rollup, and the pure transcript reducer.
3. `octo.core.persist`: `StateStore` for `state.json` on `AtomicFile`, keeping
   `state.json.1..3`, moving a corrupt file aside and falling back to a backup.
4. `octo.core.orchestrator`: the `OctoCore` interface and `Orchestrator` — one supervisor scope
   per task, `TermsGate.requireAcknowledged` before every start, resume through the stored
   session ref when the adapter can, cold start restores every task as `Stopped`.
5. Tests: diff parser, transcript reducer and rollup, persistence round-trip and fallback, the
   orchestrator over a fake adapter and workspace (common), and the worktree lifecycle and diff
   against a temp git repo with a temp `HOME` and `GIT_CONFIG_GLOBAL` (JVM).
6. `./gradlew ktlintFormat` and `./gradlew check -Pocto.allNativeTargets=true`.
