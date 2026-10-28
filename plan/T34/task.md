---
status: in progress
priority: P2
complexity: 4
readiness: 100%
agent: Claude Code / claude-opus-5-5
---

# T34. Move a task to another machine

A task is tied to the machine it was created on: its worktree, its branch and its agent's session
live there. Moving a task hands it over from this computer to a remote machine, back, or between
two remote machines. The move keeps the task's branch, its uncommitted work and its conversation.

Decided with the creator:

- **Transport.** Git's own ssh transport carries the code, with the machine's ssh settings: the
  user's keys and port, plus the shared connection where there is one. The remote `origin` is
  never touched. Uncommitted and untracked work travels as a snapshot commit on a hidden ref and
  comes back as uncommitted changes. Between two remote machines the objects go through a bare
  relay repository in the Octo home.
- **Repository.** A `machines` entry in `config.json` gets `repos`, which maps a repository's
  path on this computer to its clone on that machine. A task can move only between machines that
  have its repository in that map.
- **Conversation.** It carries over as a handoff (T23). A fresh session on the new machine gets
  the conversation from Octo's transcript with the next prompt.
- **Old worktree.** It is deleted once the new one is ready. The branch stays in the old clone, as
  it does when a task is removed.

Done means:

- Behind a `move-task` flag that also needs `remote-ssh`, `OctoCore.moveTask` moves a task.
- The task screen offers "Move to…" for machines that can take the task.
- A failed move leaves the task where it was, with a notice.
- Core, app and UI have tests.
- The README and the architecture doc describe the move.

## Execution plan

1. **Config.** Add `MachineConfig.repos` and the `move-task` flag. Validate the map: absolute
   paths only.
2. **Transport.** `SshProcessLauncher` gives git its ssh command and URL. `Workspace` gets
   operations to push and fetch hidden refs between clones, to cut the task branch from a ref,
   and to delete refs. `GitTransfer` picks the route: local→remote push, remote→local fetch, or
   remote→remote through the relay.
3. **Orchestrator.**
   1. `moveTask` runs under the task lock and checks the target first: a configured machine, its
      repository in the map, the agent and its terms.
   2. It stops the agent, snapshots the worktree and moves the refs.
   3. It creates the branch and worktree on the target, restores the snapshot and runs
      `worktreeSetup`.
   4. It then points the task at the new machine, repository and worktree, as a handoff with
      checkpoints cleared. It removes the old worktree, deletes the hidden refs and starts the
      agent.
   5. A failure before the switch cleans up the target and leaves the task as it was.
4. **UI.** "Move to…" on the task screen opens a machine picker; `TaskViewModel` calls
   `moveTask`.
5. **Tests.**
   - Fake workspaces and transport: success, a dirty worktree, an unmapped repository, a failed
     transfer, the conversation handoff.
   - Config parsing.
   - The app's flag gating.
   - The VM.
6. **Docs.** README, the architecture doc, `plan/README.md` if a decision belongs there.

Check: `./gradlew check` passes. The tests cover a move both ways, a failed move, and the target
checks.
