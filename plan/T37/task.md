---
status: todo
priority: P3
complexity: 5
readiness: 0%
agent:
---

# T37. Share a task with a guest

A link lets someone else watch a task live and send messages that the owner approves before they
reach the agent, as T3 Code does ([fb76594](https://github.com/pingdotgg/t3code/commit/fb76594)).
Octo has no hosted relay, so the first step is to decide with the creator how a guest reaches the
user's computer.

Done means: a guest with the link sees the task's transcript and status live, can propose a
message, and the owner approves or rejects it; the link can be revoked; tests cover the flow.
