---
status: in progress
priority: P2
complexity: 4
readiness: 100%
agent: Claude Code / claude-opus-5-5
---

# T27. Checkpoints on hidden git refs with revert

A bad turn can only be undone by hand. T3 Code snapshots the worktree after each turn on hidden
git refs and reverts a turn in step with the provider conversation (`docs/research.md` §2.1,
§8 item 27).

Behind a feature flag. Done: after every turn Octo records the worktree state (tracked and
untracked files) on a ref under `refs/octo/`, never touching the index, branch or stash; the
transcript offers "Revert to here", which restores the files and, where the adapter can, rolls
the agent's session back, otherwise tells the agent what was reverted. Refs are removed with the
task. Tests cover snapshot, revert and cleanup; `./gradlew check` passes.

## Execution plan

1. Core: a `checkpoints` flag. `Workspace.snapshot` and `Workspace.restore` go through a separate
   index, the same way `forkWorktree` does, so the user's index, branch and stash are never touched.
   - `snapshot` records tracked and untracked files as a commit on `refs/octo/<task>/<n>`.
   - `restore` makes the files match a checkpoint with a two-tree `read-tree -m -u`.
   - `deleteRefs` drops a task's refs.
   - Tested against real git in `GitWorkspaceTest`.
2. Orchestrator:
   - It records a checkpoint before a task's first prompt and at every `TurnEnded`, while the task
     still reads as working, so no new turn can change files mid-snapshot.
   - Checkpoints are kept on `Task`, at most 100.
   - Each checkpoint adds `TranscriptEntry.Checkpoint`.
   - `OctoCore.revert(task, checkpoint)` restores the files of an agent that is not mid-turn.
   - Revert then tries `AgentSession.rollback(turns)`. Rollback only happens when every later
     turn ran in the agent's current session; otherwise the next prompt carries a note saying what
     was reverted.
   - Later checkpoints are dropped.
   - `removeTask` deletes the refs.
   - A failed snapshot is a notice, never fatal.
3. Codex: `rollback` sends `thread/revert` with the first dropped turn's id. The protocol was
   checked in codex `rust-v0.162.1`, `app-server-protocol/src/protocol/v2/thread.rs`:
   `ThreadRevertParams { threadId, beforeTurnId }`. It only changes history, not files. Claude has
   no documented in-process rewind (`claude --help`, 2.1.304), so it gets the note.
4. UI: each checkpoint row in the transcript gets "Revert to here", behind a confirmation dialog.
   The button is shown only while the flag is on and the checkpoint still exists.
5. Docs: README and architecture.
6. Verify with `./gradlew check`, then open the PR.
