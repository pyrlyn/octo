# T28. Done

The `sections` flag lets a task be pinned, snoozed or settled, and the sidebar lists each
repository's tasks in the sections Pinned, Active, Snoozed and Settled.

- `Task.place` is persisted with the task.
- A snooze lasts for an hour, a day, or until the agent's turn ends. Any snooze ends early if
  the agent blocks or fails.
- With `git-publish` on as well, `SettleMerged` asks `gh` every five minutes for merged pull
  requests and settles each task whose branch was merged.

PR: https://github.com/pyrlyn/octo/pull/35

How it was checked: `./gradlew check` passes locally with 399 tests; ktlint and detekt are clean.
The new tests are `PlaceTest`, `SettleMergedTest` and `SectionsViewModelTest`. CI did not run
because of the GitHub billing problem.
