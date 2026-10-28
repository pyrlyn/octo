---
status: todo
priority: P3
complexity: 3
readiness: 0%
agent:
---

# T51. Import existing agent sessions

A user who starts Octo already has conversations in Claude Code or Codex and cannot continue them
in Octo. T3 Code scans and imports existing agent sessions (`AgentSessionScanner.ts`, see
`docs/research.md` §2.1; [thread-migration.md](https://github.com/pingdotgg/t3code/blob/125b61954d/docs/user/thread-migration.md)).

Done means:

- Adapters that can resume a session by id declare an import capability and list sessions for a
  repository from their own history files (`ClaudeHistory`, `CodexHistory`).
- An Import dialog lists them; importing creates a task on the session's working tree and resumes
  the session; the transcript is rebuilt from history.
- Octo only reads the history files and never changes them.
- Adapter tests use recorded history files in a temp config dir.
