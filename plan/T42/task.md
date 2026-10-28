---
status: in progress
priority: P1
complexity: 2
readiness: 100%
agent: Claude Code / claude-opus-5-5
---

# T42. Let agents start tasks through the messaging MCP server

Octo's `agent-messaging` MCP server lets an agent list tasks, message them and wait for them
(`list_tasks`, `send_message`, `wait_for_idle`), but not start one, so a lead agent cannot split work
into parallel tasks by itself. T3 Code's MCP server launches threads and delegates them to another
machine ([888e444](https://github.com/pingdotgg/t3code/commit/888e444c1a), [594f1ce](https://github.com/pingdotgg/t3code/commit/594f1ce2eb),
[outside-agents.md](https://github.com/pingdotgg/t3code/blob/125b61954d/docs/user/outside-agents.md)); `docs/research.md` §8 row 21 asked for "spawn task".

Done means:

- A `create_task` tool takes a title, a prompt, an optional agent and model, an optional base ref
  and an optional machine; it creates the task through `OctoCore.createTask` in the caller's
  repository and returns the new task id.
- The new task records its parent, like a fork, and shows under it in the sidebar.
- The task queue (T33), the terms gate and permission modes apply as for a task the user starts;
  the new task never gets a more permissive mode than its parent.
- A per-task cap on child tasks in `config.json` stops a runaway agent.
- Core and MCP handler tests cover success, an unknown agent or machine, and the cap.

## Execution plan

1. `NewTask.parentId`: `Orchestrator.createTask` records it, caps the permission mode at the
   parent's (`PermissionMode.atMost`), and on another machine finds the parent repository's clone
   there through the move-task `repos` mapping.
2. `OctoConfig.maxChildTasks` (default 10, 0 turns the tool off), passed to `AgentMessaging`.
3. `AgentMessaging.createTask`: checks the arguments (title, prompt, model and base ref, which
   reach the agent CLI and `git`), counts the caller's children plus the ones still being created
   against the cap, and creates the child in the caller's repository; the agent, model, base ref
   and machine default to the parent's.
4. `McpHandler`: the `create_task` tool and its schema; a refused or failed creation is the tool's
   error answer.
5. Tests: `AgentMessagingTest` (success, unknown agent, unknown machine, cap, mode cap, handler),
   `OctoConfigTest` for the new key.

Files: `core/.../task/Task.kt`, `core/.../agent/PermissionMode.kt`,
`core/.../orchestrator/Orchestrator.kt`, `core/.../config/OctoConfig.kt`,
`core/.../messaging/{AgentMessaging,McpHandler}.kt`, `app/.../Octo.kt`, tests next to them.

Verify: `./gradlew :core:jvmTest :app:test ktlintCheck detekt`.
