---
status: in progress
priority: P1
complexity: 4
readiness: 90%
agent: Claude Code / claude-opus-5-5
---

# T4. Claude Code adapter

Adapter for Claude Code over its stream-json control protocol: spawn, prompt, permission and question round-trips, status, resume, cancel, auth check, capabilities. Tests run on recorded or synthetic stream-json, never on a real account.

Check: Adapter tests pass on fixtures; the adapter is registered in the registry.

## Execution plan

1. Verify the control protocol against `claude-agent-sdk-python` v0.2.174 (`_internal/query.py`,
   `message_parser.py`), the SDK user-input docs and `claude --help`.
2. `adapters/claude`: `ClaudeAdapter` (args, `--session-id`/`--resume`, `auth status --json`) and
   `ClaudeSession` (initialize, prompts, stream mapping, `can_use_tool`, `AskUserQuestion`,
   `interrupt`, `control_cancel_request`).
3. Synthetic fixtures in `src/commonTest/fixtures`; tests for args, turn mapping, permission and
   question round-trips, cancel, error turns, probe.
4. Register the adapter in `app` (`builtInAdapters`).
5. Verify: `./gradlew check`.
