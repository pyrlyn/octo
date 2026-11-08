# T62.2. Done

- `SwitchBody(agent, model?)` in `access/ApiBodies.kt`, shared by `RemoteApi` and the CLI.
- `RemoteApi` takes the `handoff` flag (passed by `app`). With it, `POST tasks/{id}/switch` calls
  `OctoCore.switchAgent` and answers 200 with the task on its new agent; without it the route is
  404. A second switch while one is under way answers 409, an unknown agent 400.
- `octo task switch <id> <agent> [--model …] [--json]` prints id, agent and status.
- `FakeOctoCore.switchAgent` now moves the task to the agent, as the real core does.
- `README.md` and `docs/architecture.md` list the route and the command.

Tests: `RemoteApiTest` (a switch stops the old agent and starts the new one with the model, an
unknown agent, a wrong method, an unknown task, a concurrent switch is 409, no route without the
flag) and `OctoTest` (the command, text and JSON, and an unknown task, against the real loopback
server).

Checked with `./gradlew check`: tests, ktlint and detekt clean.
