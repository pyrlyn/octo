# T65. Done

- `core`: `Task.activeAtMs` (epoch ms, persisted, null in older state files). The orchestrator
  stamps it when a task is created or forked and in `updateView`, the one path every status
  change takes.
- `feature/common`: `compactAge(Duration)` — `now` under a minute, then `m`, `h`, `d`.
- `ui`: `TaskRow.activeAtMs`; the macOS sidebar puts the age in `SidebarRow`'s trailing slot
  (kotlin-macos unchanged), the classic sidebar at the right of its row. A clock that ticks
  every 20 seconds keeps the label current.

Checked: `AgesTest` (formatting, a row without a time) and
`OrchestratorTest.aStatusChangeStampsTheTaskAndAnotherEventDoesNot`; `:core:jvmTest`, `:ui:test`,
ktlint and detekt clean. Not checked by eye in the running app.

PR: branch `t65-sidebar-age`.
