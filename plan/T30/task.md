---
status: in progress
priority: P2
complexity: 2
readiness: 10%
agent: Claude Code / claude-opus-5-5
---

# T30. Command palette

Every action needs the mouse. T3 Code has a Cmd+K palette that finds threads first, searches
message text and runs actions
([bb7783e](https://github.com/pingdotgg/t3code/commit/bb7783e)).

Done: Cmd+K (Ctrl+K elsewhere) opens a palette that searches tasks by title and transcript text
and runs the app's actions (new task, open diff, publish, stop, open screens); actions behind a
disabled flag are hidden. Keyboard-only use works. Tests cover the search ranking;
`./gradlew check` passes.

## Execution plan

1. Flag `palette` (optional, like every other feature); `Octo`, `Main` and `ViewModels` pass it.
2. `ui/vm/Palette.kt`: `Command` (new task, diff, publish, switch agent, fork, stop, agents,
   devices) and a pure `rank` that lists tasks first (title prefix, word start, title substring,
   then message text with a snippet), then commands whose label matches.
3. `PaletteViewModel`: query, results from `core.state` and each task's transcript, selection
   moved by arrow keys, Enter runs. Commands that need a task, or whose flag is off, are left out.
4. `MainViewModel.run(command)`; the publish and switch-agent dialogs and the devices dialog move
   into `MainUiState` so a command can open them.
5. Cmd+K on macOS, Ctrl+K elsewhere, on the window; a `PaletteDialog` with a focused field.
6. Tests: ranking, availability, running commands; README and `docs/architecture.md`;
   `./gradlew check`.
