# T5. Done

- `adapters:base`: `JsonRpcAgentSession` (a JSON-RPC peer over the process plus user requests)
  and `UserRequests`, which ends every permission or question with exactly one
  `RequestResolved`, whether the user answers, the agent withdraws it or the stream ends.
  `JsonRpcHandler.onRequest` now receives the request id. Incoming request handlers start
  undispatched, so a request is registered before the next line is read and a notification
  that withdraws it right away finds it.
- `adapters:codex`: `CodexAdapter` runs `codex app-server` (JSON-RPC without the `jsonrpc`
  field); `login status` gives the auth state, Octo never reads `auth.json`. `CodexSession`:
  - `initialize`, `initialized`, then `thread/start` or `thread/resume` (cwd, model, approval
    policy `on-request` and sandbox `workspace-write` by default); the thread id is the
    session ref.
  - `turn/start` per prompt, `turn/interrupt` on cancel.
  - Items: agent message deltas and the completed text, reasoning, command executions, file
    changes, MCP tool calls and web searches become messages, thoughts and tool calls;
    `turn/plan/updated` the plan; the last token usage goes into `TurnEnded`;
    `turn/completed` maps `completed`, `interrupted` and `failed` (with its message).
  - Command and file-change approvals offer accept, accept for session and decline; a
    dismissed prompt answers `cancel`. `item/tool/requestUserInput` becomes questions.
    `serverRequest/resolved` withdraws a pending prompt. Every other server request,
    including account token refresh, gets `METHOD_NOT_FOUND`.
  - Status comes from turns and open requests; `thread/status/changed` only logs a system
    error.
- `app`: `builtInAdapters` registers Codex.

Source: the TypeScript schema from `codex app-server generate-ts`, codex-cli 0.162.1
(checked 2026-10-26); `verifiedVersion` is 0.162.1. Fixtures are synthetic, shaped after that
schema; no real account was used.

Checked: `./gradlew check -Pocto.allNativeTargets=true` green locally (5 Codex tests).
GitHub Actions is still blocked by account billing.

PR: https://github.com/pyrlyn/octo/pull/7
