# T42. Done

PR: PR_LINK

## What was done

- **Tool.** The messaging MCP server has a `create_task` tool: a title and a prompt, plus an
  optional agent, model, base ref and machine. It returns the new task's id. A refused or failed
  creation (pending terms, a git failure) is the tool's error answer, so the agent can retry.
- **Core.** `NewTask.parentId` makes `Orchestrator.createTask` record the parent, so the child
  shows under it in the sidebar, and caps its permission mode at the parent's
  (`PermissionMode.atMost`). On another machine, the parent repository's clone there comes from
  that machine's `repos` mapping. The terms gate and the start queue apply because the child goes
  through `createTask` like any other task.
- **Defaults.** The agent, base ref and machine default to the parent's. The parent's options
  (its model) carry over when the agent is the same.
- **Guards.** The title, prompt, model and base ref are checked before anything runs. A model or
  ref that starts with a dash is refused, since both reach a command line.
- **Cap.** `maxChildTasks` in `config.json` (default 10, forks included) caps the children one
  task may have, and 0 turns the tool off. Children still being set up count, so parallel calls
  cannot go over the cap.
- **Docs.** The README and the architecture flag table describe the tool and the setting.

## How it was checked

`./gradlew ktlintCheck detekt jvmTest test` is green.
- `AgentMessagingTest`:
  - a child in the parent's repository, with its model, the parent's base ref and a mode capped
    at the parent's;
  - a child on a remote machine that opens the mapped clone;
  - an unknown agent or machine and bad arguments;
  - the cap, with a fork counting and a refused call not using up a slot;
  - a zero cap;
  - `create_task` over JSON-RPC, a missing prompt included.
- `MachineConfigTest` parses `maxChildTasks` and its default.
