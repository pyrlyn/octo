# T6. Done

- `adapters:base`: a generic ACP v1 client, `AcpSession` plus `AcpAgentAdapter`, for the
  native ACP agents (Cursor now, Grok and Antigravity next):
  - `initialize` with protocol version 1 and no fs or terminal capabilities, so the agent
    uses its own tools and asks Octo only for permissions; fs and terminal requests get
    `METHOD_NOT_FOUND`.
  - On `auth_required` (-32000) it calls `authenticate` once, and only with a method the
    adapter lists as reusing the CLI's own login; Octo never sends a key or token.
  - `session/new`; resume through `session/resume` when offered, else `session/load`, whose
    history replay is dropped because Octo keeps its own transcript. `session/set_mode` for
    the `mode` option.
  - `session/prompt` runs in the background until the turn ends; its stop reason maps to
    `TurnEnded`, and a JSON-RPC error to an error turn. The session waits for a running turn
    to report before it reports the exit.
  - `session/update`: message chunks become deltas plus one whole message; thought chunks
    one thought; tool calls and updates merge into `ToolCall` events (text and diff content
    as detail); `plan` becomes the plan.
  - `session/request_permission` offers the agent's own options; a dismissed prompt answers
    `cancelled`. `session/cancel` also withdraws every pending prompt, as the spec requires.
- `adapters:cursor`: `CursorAdapter` runs `cursor-agent acp` (not `agent`, which Grok Build
  also installs), logs in with `cursor_login`, and reads `status` for the auth state.
  `CursorSession` maps `cursor/ask_question` to questions (multi-select answers join labels
  with `RequestResponse.Answers.SEPARATOR`), `cursor/create_plan` to the plan, its text and
  an accept / reject prompt, `cursor/update_todos` (merge or replace) to the plan, and
  `cursor/task` to a tool call.
- `testing`: the shared `Events` collector, which the Claude, Codex and Cursor tests use now.
- `app`: `builtInAdapters` registers Cursor.

Sources:
- ACP schema `schema/v1/schema.json` and `meta.json` at tag `schema-v1.26.0` of
  `agentclientprotocol/agent-client-protocol` (checked 2026-10-26).
- https://cursor.com/docs/cli/acp (checked 2026-10-26, page carries no version).
- The `initialize` reply and the `auth_required` error of the local `cursor-agent`
  2026.09.23-86fc751, which is `verifiedVersion`. No prompt was sent and no model was called.

Not verified: the exact text `status` prints when logged out (the adapter reads
"Not logged in", else Unknown). Fixtures are synthetic, shaped after the sources above.

Checked: `./gradlew check -Pocto.allNativeTargets=true` green locally (5 Cursor tests).
GitHub Actions is still blocked by account billing.

PR: https://github.com/pyrlyn/octo/pull/8
