---
status: in progress
priority: P2
complexity: 4
readiness: 10%
agent: Claude Code / claude-opus-5-5
---

# T23. Switch agent or model inside a task with a context handoff

A task is bound to the agent and model it started with. T3 Code switches provider or model
mid-thread and carries the conversation over as a budgeted selection of recent and original
messages, not an agent-written summary
([portable-handoffs.md](https://github.com/pingdotgg/t3code/blob/36d4d71/docs/user/portable-handoffs.md)).

Behind a feature flag. Done: the task screen offers another agent or model; a model change uses
the adapter's own switch when it has one, otherwise a fresh session. A new session receives a
handoff built from Octo's transcript within a token budget (the original request, the most
recent turns, command outcomes; the new request is never cut). The worktree and branch stay.
Tests cover the budget selection; `./gradlew check` passes.

## Execution plan

1. **Handoff.** `octo.core.handoff.Handoff.build`: a pure function over Octo's transcript. It
   keeps the original request (the first user message), then the most recent user and agent
   messages and command outcomes, newest first, until the token budget (about four characters a
   token) is spent. Thoughts and plans are dropped, a long command output keeps its tail, and the
   new request is always whole.
2. **Core.** `CoreFlags.HANDOFF`; `MODEL_OPTION` as the shared option name;
   `AgentSession.setModel` for an in-place switch; `OctoCore.switchAgent(task, agent, model)`.
   The same agent with [Capability.ModelSelection] switches the model in place. Otherwise the
   agent is stopped and a fresh session starts in the same worktree, and the task keeps
   `handoffFrom`, so the next prompt goes out with the handoff in front of it.
3. **Claude and Codex.** `setModel`: Claude through the `set_model` control request, Codex
   with `model` on the next `turn/start`.
4. **UI.** With the flag on, a "Switch agent" dialog on the task screen offers the agents on the
   task's machine and an optional model.
5. **Tests and docs.** Budget selection, the orchestrator's in-place and fresh-session paths, the
   Claude and Codex model switches, the dialog's view model. `README.md`,
   `docs/architecture.md`; `./gradlew check`; a PR.
