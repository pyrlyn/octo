---
status: todo
priority: P1
complexity: 2
readiness: 0%
agent:
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
