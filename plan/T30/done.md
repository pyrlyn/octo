# T30. Done

The `palette` flag binds Cmd+K (Ctrl+K off macOS) to a command palette. It finds tasks by title
and by the text of their messages, and runs the app's actions:

- new task, diff, publish, switch agent, fork and stop;
- the agents screen and the devices screen.

Actions behind a flag that is off are hidden. The palette works from the keyboard alone: arrows
move the selection, Enter runs it, Escape closes.

PR: https://github.com/pyrlyn/octo/pull/36

How it was checked:

- `./gradlew check` passes locally: 405 tests, ktlint and detekt clean.
- `PaletteTest` covers the search ranking, snippets, which commands are available, and running
  commands.
- The dialog was not checked by hand in the running app.
- CI did not run, because of the GitHub billing problem.
