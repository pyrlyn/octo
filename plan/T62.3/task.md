---
status: in progress
priority: P2
complexity: 3
readiness: 100%
agent: Claude Code / claude-opus-5-5
---

# T62.3. A summary from the leaving agent

The T23 handoff is a selection from Octo's transcript. The leaving agent knows more: what it
tried, what is left. When it is alive and idle, it can write a short summary before it stops.

Done: the Switch agent dialog has an "Ask for a summary first" checkbox, on by default when the
agent is alive and idle. The core sends a fixed summary prompt through `AgentSession.prompt`,
waits for the turn to end within a timeout, and puts the reply in front of the transcript
handoff. A rate-limited, failed, busy or stopped agent, a timeout or an error means no summary:
the handoff goes on with the transcript alone and the transcript says why. The summary turn is
generic (no agent-specific code). The API and CLI take the same choice. Tests for the summary path, the timeout and each fallback.

## Execution plan

1. Core: `OctoCore.switchAgent(..., summary: Boolean = false)`. In `Orchestrator.switchLocked`,
   before the old agent stops: when asked and the agent is running, idle, has nothing pending and
   no usage limit, send a fixed summary prompt through `startTurn` (not recorded as a user
   message), wait for the turn to end within two minutes, and take the agent's last message from
   that turn. A block, a failure, a timeout (the turn is cancelled) or an error means no summary,
   with a notice saying why; the switch goes on.
2. `Task.handoffSummary`, set next to `handoffFrom` and cleared when the handoff goes out.
   `Handoff.build(..., summary)` puts it under its own heading after the original request,
   clipped to a third of the budget, and leaves the same message out of the recent lines.
3. UI: an "Ask for a summary first" checkbox in the Switch agent dialog, shown when the switch
   hands over, on by default when the task's agent is idle, disabled otherwise.
4. API and CLI: `SwitchBody.summary`, `octo task switch --summary`.
5. Tests: the handoff text with a summary (`HandoffTest`), the summary path, the timeout, each
   fallback (`SwitchSummaryTest`), the dialog's view model, the route. Docs; `./gradlew check`.
