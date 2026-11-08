# T62.3. Done

- `OctoCore.switchAgent(..., summary)`. When asked and the agent is running, idle, has nothing
  pending and no usage limit, `Orchestrator` sends a fixed summary prompt through `startTurn`
  (not recorded as a user message) before it stops the agent, waits up to two minutes for the
  turn to end, and keeps the agent's last message from that turn as `Task.handoffSummary`.
  A busy, blocked, failed or rate-limited agent, an empty reply, an error or the timeout (the
  turn is cancelled) leave a "No summary: …" notice and the switch goes on.
- `Handoff.build(..., summary)` puts the summary under its own heading after the original
  request, clipped to a third of what is left of the budget, and leaves that message out of the
  recent lines. It goes out once, with the next prompt; a fork keeps a pending one, and a second
  switch before any prompt keeps the first summary.
- UI: "Ask for a summary first" in the Switch agent dialog, shown for a handoff, on by default
  while the agent is idle and disabled otherwise.
- API and CLI: `SwitchBody.summary`, `octo task switch --summary`.
- `README.md` and `docs/architecture.md`.

Tests: `SwitchSummaryTest` (the summary goes out with the handoff and once; a timeout; a
permission request; a busy and a stopped agent; no summary turn without asking), `HandoffTest`
(heading, no repeat, a long summary is clipped and recent messages still fit),
`SwitchAgentViewModelTest` (default on, the user's choice, a busy agent), `OctoTest` (`--summary`).

Checked with `./gradlew check`: tests, ktlint and detekt clean.
