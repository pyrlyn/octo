---
status: in progress
priority: P3
complexity: 3
readiness: 90%
agent: Claude Code / claude-opus-5-5
---

# T24. Fork a task

Trying a different approach today means starting over. T3 Code forks a thread, keeps lineage
at the app level, uses the provider's native fork when it exists and a context handoff otherwise
(`ThreadForkService.ts`, see `docs/research.md` §2.1).

Behind a feature flag; builds on T23's handoff. Done: "Fork" on a task creates a new task on a
new branch from the current worktree state, linked to its parent in the sidebar; an adapter with
a native fork capability forks its session, others get the handoff. Tests cover both paths;
`./gradlew check` passes.

## Execution plan

1. **Flag and SPI.** Add the `fork` core flag. Add `Capability.Fork` and `SessionSpec.fork`: with
   `resume`, the session continues a copy.
2. **Worktree snapshot.** `Workspace.forkWorktree` cuts a branch from the source's HEAD. It then
   copies the uncommitted and untracked files through a separate `GIT_INDEX_FILE`, leaving the
   source untouched.
3. **Core.** `OctoCore.forkTask` creates a new task with `Task.parentId` and copies the
   transcript.
   - A native fork passes the parent's session to `startAgent`; it is never stored on the
     child.
   - Any other agent gets T23's handoff on the next prompt.
4. **Adapters.**
   - Claude: `--resume --fork-session --session-id`.
   - Codex: `thread/fork`.
5. **UI.**
   - A Fork button behind the flag opens the new task.
   - The sidebar lists forks under their parent, indented.
6. **Tests.**
   - Git snapshot on a real repository.
   - Orchestrator: the native and the handoff paths.
   - Adapter fixtures.
   - Sidebar lineage and flag gating.
   - Then `./gradlew check`.

