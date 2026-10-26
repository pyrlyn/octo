---
status: in progress
priority: P1
complexity: 3
readiness: 100%
agent: Claude Code / claude-opus-5-5
---

# T7. Grok Build adapter

Adapter for xAI Grok Build over ACP (`grok agent stdio`). Detects the community `grok` CLI and refuses it. Tests on fixtures only.

Check: Adapter tests pass on fixtures; the adapter is registered.

## Execution plan

1. `adapters:grok`: `GrokAdapter` on the shared ACP client, `grok agent stdio`, login through
   `cached_token`.
2. Tell Grok Build from the community `grok-dev` by `--version` output; refuse the latter before
   launch with `WrongAgentBinaryException` (core) through a `checkBinary` hook in
   `BaseAgentAdapter`.
3. Synthetic transcript; register in `app`; verify with `./gradlew check -Pocto.allNativeTargets=true`.
