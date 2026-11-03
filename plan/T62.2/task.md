---
status: in progress
priority: P2
complexity: 2
readiness: 100%
agent: Claude Code / claude-opus-5-5
---

# T62.2. Hand over through the remote API and the CLI

`switchAgent` is reachable only from the desktop UI. A paired device and the `octo` command
should be able to hand a task to another agent too.

Done: with `handoff` and `remote-access` on, `RemoteApi` takes a switch request (agent and an
optional model) for a task and answers with the task's new agent, or the error a refused or
concurrent handoff gives; `octo task switch <task> <agent> [--model …]` calls it. The request and
response bodies live in `access/ApiBodies.kt`. Tests for the API route and the CLI command.

## Execution plan

1. `access/ApiBodies.kt`: `SwitchBody(agent, model)`; the reply is the task's `TaskBody`.
2. `RemoteApi`: a `handoff` constructor flag; with it, `POST tasks/{id}/switch` calls
   `OctoCore.switchAgent` and answers 200 with the task. Without it the route is 404. Errors
   map as today: a concurrent handoff and unaccepted terms 409, an unknown agent 400.
3. `app`: pass the `handoff` flag to `RemoteApi`.
4. `cli`: `TaskSwitchCommand` (`octo task switch <id> <agent> [--model] [--json]`), printing id,
   agent and status; registered next to the other `task` commands.
5. Tests: `RemoteApiTest` (switch, flag off, unknown agent, method), `OctoTest` (the command
   against a real `RemoteHttpServer` over a fake core). `README.md`, `docs/architecture.md`;
   `./gradlew check`; a PR.
