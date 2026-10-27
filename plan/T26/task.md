---
status: todo
priority: P2
complexity: 2
readiness: 0%
agent:
---

# T26. Send one prompt to several agents

Comparing agents on one task means creating each task by hand. T3 Code sends one prompt to
several models at once, each in its own worktree (`docs/research.md` §2.1, §8 item 20).

Behind a feature flag. Done: the new-task dialog accepts several agents (and models); Octo
creates one task per choice with its own branch, the same prompt and a shared group shown in the
sidebar, so the diffs can be compared. Tests cover group creation and partial failure;
`./gradlew check` passes.
