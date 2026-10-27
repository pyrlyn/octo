---
status: in progress
priority: P1
complexity: 2
readiness: 10%
agent: Claude Code / claude-opus-5-5
---

# T21. Queue and steer messages while the agent works

Today the composer is disabled while the agent works (`TaskViewModel.canSend`), so the user waits
for the turn to end before saying anything. T3 Code lets a follow-up either wait in a queue or
steer the running turn, and steers by default
([b808f7f](https://github.com/pingdotgg/t3code/commit/b808f7f),
[composer.md](https://github.com/pingdotgg/t3code/blob/36d4d71/docs/user/composer.md)).

Done: the user can send while the agent works. Adapters that can inject into a running turn
declare a capability and steer; the rest get a declared fallback: the message is queued per task,
shown as queued in the transcript, can be edited or removed, and is sent when the agent is idle.
The queue survives a restart. Tests cover both paths; `./gradlew check` passes.

## Execution plan

1. **Core.** `Capability.Steer` and `AgentSession.steer(text)` (throws by default). `Task.queue`
   holds `QueuedPrompt(id, text)`, so it is persisted with the task. `Orchestrator.sendPrompt`
   runs under the task lock: an idle or starting agent gets the prompt; a busy one is steered
   when its adapter declares `Steer` and the steer succeeds, otherwise the text is queued. Sending
   marks the turn started at once, so a second sender never sees a stale idle. When the agent
   turns idle, the head of the queue is sent. `OctoCore.editQueued` and `removeQueued`. Files:
   `core/agent/AgentAdapter.kt`, `core/task/Task.kt`, `Orchestrator.kt`, `OctoCore.kt`.
2. **Codex.** Declares `Steer`; `steer` calls `turn/steer` with the active turn as
   `expectedTurnId` (schema from `codex app-server generate-ts`, 0.162.1). Claude's stream-json
   input and ACP have no in-turn injection, so they use the queue.
3. **UI.** The composer stays enabled while the agent works; queued prompts show below the
   transcript with edit and remove. Files: `TaskViewModel.kt`, `TaskScreen.kt`.
4. **Tests.** Core: steer, queue, delivery on idle, edit and remove, steer failure falls back to
   the queue, the queue survives a reload. Codex: `turn/steer` request. UI: send while working,
   edit and remove.
5. **Docs and check.** `docs/architecture.md`, `README.md`; `./gradlew check`; a PR.
