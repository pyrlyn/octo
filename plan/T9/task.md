---
status: todo
priority: P1
complexity: 4
readiness: 0%
agent:
---

# T9. Core orchestration

Orchestrator in the core: tasks, each with its own git worktree and branch; parallel agent sessions; status tracking and rollup; prompts, answers, stop and restart; persisted sessions and layout restored on restart; diff of a task against its base.

Check: Core tests cover worktree lifecycle (against a temp git repo), persistence round-trip, status reducer and diff.
