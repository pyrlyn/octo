---
status: in progress
priority: P2
complexity: 4
readiness: 20%
agent: Claude Code / claude-opus-5-5
---

# T62. Hand a session from one agent to another, one agent at a time

The creator wants a running session (its worktree, branch and context) to pass from one agent
to another, for example from Claude Code to Cursor, while only one agent ever works in that
session at a time.

T23 already switches a task's agent or model behind the `handoff` flag: `OctoCore.switchAgent`
stops the agent under the task's lock, starts the next one in the same worktree, and the next
prompt carries a budgeted handoff built from Octo's transcript. This task closes what T23 left
out, in four subtasks, each its own PR:

- T62.1: the core enforces one live agent per task and refuses a second concurrent handoff.
- T62.2: the handoff through the `remote-access` API and the `octo` CLI.
- T62.3: optionally ask the leaving agent for a summary that goes with the handoff.
- T62.4: hand over on its own when the agent hits a usage limit.

Everything new stays behind the existing `handoff` flag, except the single-agent guarantee,
which holds with every flag off. No agent-specific code in core or UI. Done when the four
subtasks are closed.
