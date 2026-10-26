---
status: todo
priority: P1
complexity: 4
readiness: 0%
agent:
---

# T4. Claude Code adapter

Adapter for Claude Code over its stream-json control protocol: spawn, prompt, permission and question round-trips, status, resume, cancel, auth check, capabilities. Tests run on recorded or synthetic stream-json, never on a real account.

Check: Adapter tests pass on fixtures; the adapter is registered in the registry.
