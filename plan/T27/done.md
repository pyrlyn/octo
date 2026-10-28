# T27. Done

PR: https://github.com/pyrlyn/octo/pull/38

## What was done

- **`checkpoints` feature flag.** Off by default. While it is on, Octo snapshots the worktree before a task's first turn and after every turn.
- **Snapshots on hidden refs.** `GitWorkspace.snapshot` writes the worktree's files into a commit through a separate index file and points `refs/octo/<task>/<n>` at it. The real index, the stash, HEAD and the branch stay as they are. A task keeps its last 100 checkpoints, and removing the task deletes its refs.
- **Revert.** `OctoCore.revert` refuses while a turn runs. Otherwise it restores the files and drops the later checkpoints; the branch keeps its commits.
- **The agent's conversation.**
  - `AgentSession.rollback` is new. Codex implements it with app-server `thread/revert`.
  - It is used only when every dropped turn ran in the live session.
  - Otherwise, or when the rollback fails, the next prompt carries a note that the files were reverted. Claude has no rewind option, so it always gets the note.
- **UI.** Checkpoint rows in the transcript with "Revert to here" and a confirm dialog.

## How it was checked

`./gradlew check` is green: ktlint, detekt, and 444 tests. The new tests cover:
- Snapshot, restore and ref deletion against real git in a temp repo, in `GitWorkspaceTest`.
- Recording, revert with and without rollback, a revert across a restart, a failed snapshot, removal, and the flag off, in `CheckpointTest`.
- Codex `thread/revert` against a recorded transcript.
- `RevertViewModelTest` in ui.
