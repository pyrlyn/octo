---
status: todo
priority: P1
complexity: 2
readiness: 0%
agent:
---

# T25. Setup script on worktree creation

A fresh worktree has no installed dependencies or `.env` files, so the agent's first turn is
spent on setup or fails. T3 Code runs a per-project script when it creates a worktree
(`runOnWorktreeCreate` in `t3.json`, see `docs/research.md` §2.1).

Done: a per-repository setup command (in the repository's Octo config file or Octo's config),
run in the new worktree before the agent starts, locally and on remote machines. Its output shows
in the task; a failure marks the task failed with the output and does not start the agent. Tests
cover success, failure and no script; `./gradlew check` passes.
