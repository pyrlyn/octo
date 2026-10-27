---
status: in progress
priority: P1
complexity: 2
readiness: 100%
agent: Claude Code / claude-opus-5-5
---

# T25. Setup script on worktree creation

A fresh worktree has no installed dependencies or `.env` files, so the agent's first turn is
spent on setup or fails. T3 Code runs a per-project script when it creates a worktree
(`runOnWorktreeCreate` in `t3.json`, see `docs/research.md` §2.1).

Done: a per-repository setup command (in the repository's Octo config file or Octo's config),
run in the new worktree before the agent starts, locally and on remote machines. Its output shows
in the task; a failure marks the task failed with the output and does not start the agent. Tests
cover success, failure and no script; `./gradlew check` passes.

## Execution plan

The creator chose Octo's `config.json` over a file in the repository: the user writes the command,
so nothing a repository ships runs without them.

1. `OctoConfig.worktreeSetup`: repository top-level path to a shell command.
2. `Workspace.runSetup(worktree, command)` runs `/bin/sh -c` in the worktree through the machine's
   own launcher, so remote machines run it over ssh. `ProcessLauncher.run` kills the process when
   cancelled, so a timed-out setup does not linger.
3. `Orchestrator` runs it after creating or forking a worktree and before the agent starts, with a
   timeout. The transcript shows it as a tool entry with the output's tail; a failure marks the task
   Failed with that output and the agent does not start.
4. App wiring, README and `docs/architecture.md`.
5. Tests: success, failure, timeout and no command (orchestrator, fake workspace); a real
   `/bin/sh` run in `GitWorkspaceTest`; config parsing. `./gradlew check`.
