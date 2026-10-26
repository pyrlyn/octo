---
status: in progress
priority: P1
complexity: 3
readiness: 100%
agent: Claude Code / claude-opus-5-5
---

# T6. Cursor adapter

Adapter for Cursor over ACP (`cursor-agent acp`), including the `cursor/ask_question` and `cursor/create_plan` extensions. Tests on fixtures only.

Check: Adapter tests pass on fixtures; the adapter is registered.

## Execution plan

1. `adapters:base`: a generic ACP v1 client (`AcpSession`, `AcpAgentAdapter`) for Cursor, Grok
   and Antigravity: handshake, login retry through the CLI's own login, `session/new`,
   `session/resume` or `session/load`, prompts, updates, permissions, cancel.
2. `adapters:cursor`: `cursor-agent acp` plus `cursor/ask_question`, `cursor/create_plan`,
   `cursor/update_todos` and `cursor/task`.
3. Synthetic transcripts; register the adapter in `app`; verify with
   `./gradlew check -Pocto.allNativeTargets=true`.
