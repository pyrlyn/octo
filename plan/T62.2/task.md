---
status: todo
priority: P2
complexity: 2
readiness: 0%
agent:
---

# T62.2. Hand over through the remote API and the CLI

`switchAgent` is reachable only from the desktop UI. A paired device and the `octo` command
should be able to hand a task to another agent too.

Done: with `handoff` and `remote-access` on, `RemoteApi` takes a switch request (agent and an
optional model) for a task and answers with the task's new agent, or the error a refused or
concurrent handoff gives; `octo switch <task> <agent> [--model …]` calls it. The request and
response bodies live in `access/ApiBodies.kt`. Tests for the API route and the CLI command.
