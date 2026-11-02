---
status: todo
priority: P2
complexity: 3
readiness: 0%
agent:
---

# T62.4. Hand over on a usage limit

When an agent hits its usage limit (`AgentEvent.RateLimited`), the task stalls until the limit
lifts. It could go on with another agent instead.

Done: `config.json` takes `handoffFallbacks`, an ordered list of agent picks (agent and an
optional model) for every task; a task can override it with its own list, set in the task
screen and kept in `state.json`. On a usage limit, with `handoff` on, the core hands the task
to the first pick that is installed on the task's machine, is not the current agent and has not
hit a limit in this run, and resends the interrupted request through the handoff. No pick left,
or an empty list, keeps today's behaviour (wait and Resume). Tests for the pick order, the
per-task override, an exhausted list, and the flag off.
