# T4. Done

- `adapters/claude`: `ClaudeAdapter` runs `claude -p` with stream-json in and out,
  `--include-partial-messages` and `--permission-prompt-tool stdio`. A new session gets an
  Octo-generated `--session-id`, so the ref is known at once; `--resume=<id>` resumes. The probe
  reads `claude auth status --json` (`loggedIn` only; no credentials are touched). Verified
  version: 2.1.310.
- `ClaudeSession` sends the `initialize` control request, maps `system/init`, text deltas,
  assistant text, thinking, tool use and results, `TodoWrite` (plan), and `result` (usage, cost,
  error text; `is_error` with subtype `success` counts as an error). `can_use_tool` becomes a
  permission request (allow, always allow when Claude offers rules, reject) or, for
  `AskUserQuestion`, a question whose answers go back keyed by question text.
  `control_cancel_request` resolves the request; `cancel()` sends `interrupt` and the next result
  reads as cancelled. Other control requests get an error reply.
- `adapters:base`: null-safe JSON accessors for untrusted agent output. `ProcessAgentSession`
  is the base for NDJSON CLIs, so no separate `StreamJsonSession` was needed.
- `app`: `builtInAdapters` registers Claude.

Sources checked: `claude-agent-sdk-python` v0.2.174 (commit 0e89b29), 
https://code.claude.com/docs/en/agent-sdk/user-input.md (2026-10-26), `claude --help` 2.1.304.
Fixtures are synthetic, shaped after those sources; no real account was used.

Checked: `./gradlew check` green locally (6 Claude tests). GitHub Actions is still blocked by
account billing.
