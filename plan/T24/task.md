---
status: todo
priority: P3
complexity: 3
readiness: 0%
agent:
---

# T24. Fork a task

Trying a different approach today means starting over. T3 Code forks a thread, keeps lineage
at the app level, uses the provider's native fork when it exists and a context handoff otherwise
(`ThreadForkService.ts`, see `docs/research.md` §2.1).

Behind a feature flag; builds on T23's handoff. Done: "Fork" on a task creates a new task on a
new branch from the current worktree state, linked to its parent in the sidebar; an adapter with
a native fork capability forks its session, others get the handoff. Tests cover both paths;
`./gradlew check` passes.
