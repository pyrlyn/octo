---
status: in progress
priority: P2
complexity: 3
readiness: 100%
agent: Claude Code / claude-opus-5-5
---

# T33. Task queue with a parallel limit

Every new task starts its agent at once, so a burst of tasks (a compare across five agents, a
preset, triggers) can start more agent processes than the computer or a remote machine handles.
A cap on live agents per machine lets a new task wait for a free slot instead.

Decided with the creator:

- A slot is held by a live agent process, idle or not; it frees when the agent stops, exits or
  fails, or its task is removed.
- The limit is per machine: one for this computer, one for each remote machine.
- It is set in `config.json` (`maxRunningAgents` at the top level and on each machine) behind a
  `queue` flag. A waiting task reads as Queued and offers Start now.

Done means: with the flag on, a task over the limit is Queued; it starts on its own, in order,
when a slot frees on its machine; Stop drops it from the queue; Start now starts it over the
limit; a first prompt or a prompt sent while queued is delivered once it starts; with the flag
off nothing changes.

## Execution plan

1. Core: `AgentStatus.Queued` and `SidebarStatus.Queued`; an `AgentSlots` class in
   `orchestrator` (per-machine holders and a FIFO of waiting starts, updated atomically).
2. `Orchestrator`: `startAgent` claims a slot or queues the task; slots are released when an
   agent's event stream ends on its own, on stop and on a failed start, and handed to the next
   waiting task; restart and agent switch keep their slot; `close` empties the queue first.
   Prompts to a queued task go to its message queue. `OctoCore.startNow`.
3. Config: `OctoConfig.maxRunningAgents`, `MachineConfig.maxRunningAgents`, `CoreFlags.QUEUE`;
   `app` passes the limits to `Orchestrator.open` only with the flag on, warning about values
   below 1.
4. UI: the Queued label and colour, and a Start now action on a queued task.
5. Tests: `AgentSlotsTest`, orchestrator tests (queueing, order, per machine, stop, start now,
   restart keeps the slot, prompts while queued, agent exit frees a slot), config and app flag
   tests, a UI view-model test.
6. Docs: README flag paragraph, architecture flag row and a module note.

Check: `./gradlew check` (ktlint, detekt, all tests).
