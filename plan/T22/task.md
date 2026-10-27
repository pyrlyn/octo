---
status: in progress
priority: P1
complexity: 2
readiness: 10%
agent: Claude Code / claude-opus-5-5
---

# T22. One set of permission modes for every agent

Each adapter takes its own option today (`permissionMode` for Claude, `approvalPolicy` and
`sandbox` for Codex, `mode` for ACP), so the user must know every agent's vocabulary. T3 Code
offers four modes for all providers and maps them per provider, falling back to asking where a
provider has no equivalent ([permission-modes.md](https://github.com/pingdotgg/t3code/blob/36d4d71/docs/user/permission-modes.md)).

Done: a core `PermissionMode` (Supervised, Auto-accept edits, Auto, Full access) chosen in the
new-task dialog and changeable on a running task; each adapter maps it to its own option and
declares which modes it supports, an unsupported one falls back to Supervised. A default for new
tasks lives in the config. No agent-id checks in the core. Adapter tests cover the mapping;
`./gradlew check` passes.

## Execution plan

1. **Core.** `PermissionMode` in `octo.core.agent`; `AgentAdapter.permissionModes` (Supervised
   by default); `SessionSpec.permissionMode`; `AgentSession.setPermissionMode` for a live switch.
   `Task.permissionMode` (persisted), `NewTask.permissionMode` (null takes the config default
   `defaultPermissionMode`), `OctoCore.setPermissionMode`. The orchestrator passes the mode the
   adapter supports, says when it falls back to Supervised, and when a live switch fails keeps
   the mode for the next start. `AgentInfo.permissionModes` for the dialog.
2. **Claude.** `--permission-mode` from the mode (`default`, `acceptEdits`, `auto`,
   `bypassPermissions`); a live switch through the `set_permission_mode` control request. The
   raw `permissionMode` option goes.
3. **Codex.** `approvalPolicy`, `approvalsReviewer` and `sandbox` on `thread/start` and
   `thread/resume`; a live switch sends the overrides with the next `turn/start`. The raw
   `approvalPolicy` and `sandbox` options go.
4. **ACP agents.** Their modes are agent-defined, so they declare Supervised only.
5. **UI.** A permissions picker in the new-task dialog (the config default plus the agent's
   modes) and on the task screen; the remote API takes `permissionMode` on create.
6. **Tests and docs.** Core: default, fallback, live switch, failed switch, persistence.
   Claude and Codex: mapping and live switch fixtures. UI: dialog and task screen view models.
   `README.md`, `docs/architecture.md`; `./gradlew check`; a PR.
