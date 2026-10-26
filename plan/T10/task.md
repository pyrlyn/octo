---
status: todo
priority: P1
complexity: 4
readiness: 0%
agent:
---

# T10. MVP UI

Compose UI wired to the core: task list with status, new task with agent and base branch, live output per agent, prompt box, answers to questions and permissions, stop and restart, diff review, layout persisted.

The UI follows MVVM (creator's decision): a view model per screen holds one immutable state
as a `StateFlow` and turns user actions into `OctoCore` calls; composables only render state
and forward actions.

Check: The app runs a fake agent end to end; UI state tests pass.
