# T34. Done

PR: https://github.com/pyrlyn/octo/pull/43

## What was done

- **Flag and config.** The `move-task` flag needs `remote-ssh`. A machine's `repos` in `config.json` maps a local repository to its clone on that machine; only machines with a clone are targets.
- **The move.**
  - The agent stops, and the worktree is snapshotted into `refs/octo-move/<id>` on top of the branch tip.
  - The ref travels by git over ssh. Between two remote machines it goes through `relay.git` in the Octo home.
  - The target checks the branch out at the tip in a new worktree and restores the snapshot.
  - The hidden refs are deleted on every side, and the old worktree is removed (fail open, with a notice).
  - The conversation carries over as a handoff. Checkpoints are forgotten.
- **Failures.** Any failure takes the new worktree away and leaves the task where it was, with a notice. The agent restarts if it was running.
- **UI.** "Move to…" in the task header, "Move to another machine" in the palette, and a dialog that picks the target.
- **Refactoring.** `StateSaver`, `PromptQueues`, `Runtime` and the process usage flow moved out of `Orchestrator`. `RefTransfer` was split from `Workspace`.

## How it was checked

`./gradlew check` is green: ktlint, detekt and all tests.

`MoveTaskTest` covers these cases on fake workspaces:
- local to remote with the handoff;
- remote to local;
- remote to remote via the relay;
- unmapped targets;
- a failed move restarting the agent;
- forgotten checkpoints.

`GitWorkspaceTest` carries a real git snapshot through a bare relay to a clone.
