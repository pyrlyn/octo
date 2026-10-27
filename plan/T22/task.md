---
status: todo
priority: P1
complexity: 2
readiness: 0%
agent:
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
