---
status: todo
priority: P1
complexity: 2
readiness: 0%
agent:
---

# T21. Queue and steer messages while the agent works

Today the composer is disabled while the agent works (`TaskViewModel.canSend`), so the user waits
for the turn to end before saying anything. T3 Code lets a follow-up either wait in a queue or
steer the running turn, and steers by default
([b808f7f](https://github.com/pingdotgg/t3code/commit/b808f7f),
[composer.md](https://github.com/pingdotgg/t3code/blob/36d4d71/docs/user/composer.md)).

Done: the user can send while the agent works. Adapters that can inject into a running turn
declare a capability and steer; the rest get a declared fallback: the message is queued per task,
shown as queued in the transcript, can be edited or removed, and is sent when the agent is idle.
The queue survives a restart. Tests cover both paths; `./gradlew check` passes.
