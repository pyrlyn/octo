---
status: todo
priority: P2
complexity: 4
readiness: 0%
agent:
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
