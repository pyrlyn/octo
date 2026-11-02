---
status: todo
priority: P2
complexity: 3
readiness: 0%
agent:
---

# T62.3. A summary from the leaving agent

The T23 handoff is a selection from Octo's transcript. The leaving agent knows more: what it
tried, what is left. When it is alive and idle, it can write a short summary before it stops.

Done: the Switch agent dialog has an "Ask for a summary first" checkbox, on by default when the
agent is alive and idle. The core sends a fixed summary prompt through `AgentSession.prompt`,
waits for the turn to end within a timeout, and puts the reply in front of the transcript
handoff. A rate-limited, failed, busy or stopped agent, a timeout or an error means no summary:
the handoff goes on with the transcript alone and the transcript says why. The summary turn is
generic (no agent-specific code). Tests for the summary path, the timeout and each fallback.
