---
status: in progress
priority: P1
complexity: 4
readiness: 100%
agent: Claude Code / claude-opus-5-5
---

# T5. Codex CLI adapter

Adapter for Codex over `codex app-server` JSON-RPC: threads, turns, approvals, status from `thread/status/changed`, resume, interrupt, auth check. Tests on fixtures only.

Check: Adapter tests pass on fixtures; the adapter is registered.

## Execution plan

1. `adapters:base`: a shared `JsonRpcAgentSession` and `UserRequests` (requests waiting on the
   user, each closed by exactly one `RequestResolved`); request handlers get the JSON-RPC id.
2. `adapters:codex`: `CodexAdapter` (`codex app-server`, `login status`) and `CodexSession`
   mapping the v2 schema from `codex app-server generate-ts` to `AgentEvent`s.
3. Synthetic transcripts for a turn, approvals and questions, resume plus interrupt and errors.
4. Register the adapter in `app`; verify with `./gradlew check -Pocto.allNativeTargets=true`.
