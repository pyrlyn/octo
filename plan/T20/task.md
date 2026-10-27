---
status: in progress
priority: P2
complexity: 4
readiness: 100%
agent: Claude Code / claude-opus-5-5
---

# T20. Agents screen

A screen that lists the running agents, one row each, left to right:

1. The agent's icon: an SVG the adapter ships, or a monogram when it has none.
2. Credentials, never in plain text. Octo still never reads an agent's login or key (see
   `plan/README.md`), so the cell shows the sign-in state from the adapter's probe: a mask
   `••••••` when signed in, "Not signed in" or "Unknown" otherwise.
3. CPU % and memory of the agent's own process tree, not Octo's JVM. A task on a remote machine
   shows "remote": its local process is only the `ssh` client.
4. Model, context use (used / window) and session token totals (input / output, cache read and
   write when the agent reports them).
5. A stop button drawn as an icon, with a tooltip and a content description.

The screen opens from an "Agents" button in the sidebar and has no feature flag (the creator's
choice). Done: every running agent shows all five parts, stop works, tests cover the stats and
the masking, `./gradlew check` passes, and a PR is open.

## Execution plan

1. **Core events.** `AgentEvent.StatsUpdated(SessionStats)`: model, context used and window,
   session token totals; a null field keeps the last value. `TaskView.stats` folds them and is
   reset when the agent starts. Files: `core/agent/AgentEvent.kt`, `core/task/Task.kt`,
   `core/orchestrator/TaskViews.kt`, `Orchestrator.kt`.
2. **Process stats.** `RunningProcess.pid` and `AgentSession.processId` (null by default;
   `ProcessAgentSession` passes the pid through). `ProcessMonitor` in `core/process` with a JVM
   implementation: `ProcessHandle` for the tree and CPU time (CPU % from the delta between two
   samples), resident memory from `ps -o pid=,rss=,time=` on macOS and Linux and `Get-Process`
   on Windows, run through `ProcessLauncher`. On macOS the JDK has no CPU time for other
   processes, so `ps`'s `time` column fills it. A failed sample yields unknown values, never an
   error. `OctoCore.processUsage` samples local running tasks only while collected.
3. **Icons.** `AgentAdapter.icon` (an `AgentIcon` holding SVG markup) and `AgentInfo.icon`.
   Each built-in adapter ships its own glyph; the plugin adapter has none, since a plugin's SVG
   would be untrusted input for the renderer.
4. **Adapters.** Claude: model from `system/init`, context from each assistant message's usage,
   totals and cache from `result`, window from `modelUsage`. Codex: model from `thread/start`,
   totals, cache, context and window from `thread/tokenUsage/updated`. ACP base (Cursor, Grok,
   Kimi, Antigravity): model from the `model` config option (or the older `models` state),
   context from `usage_update`, totals from the `usage` in the `session/prompt` reply.
5. **UI.** `AgentsViewModel` (rows from `OctoCore.state`, `processUsage()` and the catalog's
   probes; formatting and masking as plain functions) and `AgentsScreen`; a sidebar button and
   `MainUiState.agentsOpen`; a stop icon drawn as an `ImageVector`.
6. **Tests.** Core: stats folding, `processUsage` over a fake monitor, the monitor's parsers and
   a live sample of a child process. Adapters: stats from fake output for Claude, Codex and ACP.
   UI: rows, masking, formatting and stop in `AgentsViewModel`.
7. **Docs and check.** `docs/architecture.md` (event, monitor, screen), `README.md`; then
   `./gradlew check` and a PR.
