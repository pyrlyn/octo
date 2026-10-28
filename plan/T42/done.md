# T42. Done

The `agent-messaging` MCP server has a `create_task` tool next to `list_tasks`, `send_message`
and `wait_for_idle`.

- `create_task` takes a title, a prompt and an optional agent, model, base ref and machine. Each
  optional value defaults to the caller's. The tool starts the task through
  `OctoCore.createTask` in the caller's repository and returns the new id. A refused or failed
  creation (bad arguments, an unknown agent or machine, pending terms, a git error) comes back as
  the tool's error answer, so the agent can read it and try again.
- `NewTask.parentId` records the parent, using the same `Task.parentId` link as a fork, so the
  child shows under its parent in the sidebar. `Orchestrator.createTask` limits the child's
  permission mode to the parent's (`PermissionMode.atMost`). For a child on another machine it
  finds the parent repository's clone there through the move-task `repos` mapping.
- The terms gate and the start queue (T33) apply because the child goes through the same
  `createTask` path as a task the user starts.
- `maxChildTasks` in `config.json` (default 10; 0 turns the tool off) caps the children of one
  task, forks included. Children still being created count against the cap, and the cap check
  reads the state inside one atomic update, so concurrent calls cannot slip past it.
- The model and base ref must not start with a dash, because both reach a command line.
- Docs: the `agent-messaging` row in `docs/architecture.md` §6 and the README section.

PR: https://github.com/pyrlyn/octo/pull/52

## How it was checked

`./gradlew check` (all tests, ktlint, detekt) passed locally. GitHub Actions is not running
because of a billing problem.
New tests in `AgentMessagingTest`:

- the MCP handler lists `create_task`, creates a task and reports a missing prompt as an error;
- success: the child gets the parent link, repository, agent, base ref, model and a mode capped
  at the parent's, and its first prompt is sent;
- a child on another machine opens the parent's clone there;
- an unknown agent, an unknown machine and bad arguments are refused;
- the cap: a refused attempt uses no slot, a fork counts, the cap applies per task, and 0 turns
  the tool off.

`MachineConfigTest` covers the new config key and its default. The tests use fakes only and never
touch the real `HOME`.
