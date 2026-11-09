---
status: in progress
priority: P2
complexity: 2
readiness: 90%
agent: Claude / claude-opus-5-5
---

# T65. Time since last activity in the sidebar

A sidebar row says what a task's agent is doing, not when it last did anything. Each row shows
how long ago the agent's status last changed, as a compact age (`now`, `3m`, `2h`, `1d`),
right-aligned in the row.

Done: the age shows on every task row of the macOS sidebar and the classic sidebar, ticks
forward while the app is open, and survives a restart.

## Execution plan

1. `core`: `Task.activeAtMs` (epoch ms, persisted, null in older state files). Set when a task
   is created or forked and whenever the orchestrator changes a task's status.
2. `feature/common`: `compactAge(Duration)` — `now` under a minute, then `m`, `h`, `d`.
3. `ui`: `TaskRow.activeAtMs`; both sidebars show the age in the row's trailing slot
   (`SidebarRow.trailing` from kotlin-macos), recomputed every minute.
4. Tests: age formatting, the stamp on a status change, the row mapping. ktlint and detekt clean.
