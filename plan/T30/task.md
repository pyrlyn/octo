---
status: todo
priority: P2
complexity: 2
readiness: 0%
agent:
---

# T30. Command palette

Every action needs the mouse. T3 Code has a Cmd+K palette that finds threads first, searches
message text and runs actions
([bb7783e](https://github.com/pingdotgg/t3code/commit/bb7783e)).

Done: Cmd+K (Ctrl+K elsewhere) opens a palette that searches tasks by title and transcript text
and runs the app's actions (new task, open diff, publish, stop, open screens); actions behind a
disabled flag are hidden. Keyboard-only use works. Tests cover the search ranking;
`./gradlew check` passes.
