---
status: todo
priority: P2
complexity: 4
readiness: 0%
agent:
---

# T23. Switch agent or model inside a task with a context handoff

A task is bound to the agent and model it started with. T3 Code switches provider or model
mid-thread and carries the conversation over as a budgeted selection of recent and original
messages, not an agent-written summary
([portable-handoffs.md](https://github.com/pingdotgg/t3code/blob/36d4d71/docs/user/portable-handoffs.md)).

Behind a feature flag. Done: the task screen offers another agent or model; a model change uses
the adapter's own switch when it has one, otherwise a fresh session. A new session receives a
handoff built from Octo's transcript within a token budget (the original request, the most
recent turns, command outcomes; the new request is never cut). The worktree and branch stay.
Tests cover the budget selection; `./gradlew check` passes.
