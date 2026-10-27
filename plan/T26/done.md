# T26. Done

The `compare` flag sends one prompt to several agents, or to several models of one agent. Each
gets its own task and branch, and they all share one `Task.group`. `OctoCore.compare` starts the
tasks and reports the picks that failed, without stopping the others. The new-task dialog has a
"Compare agents" mode: checkboxes for agents, with comma-separated models for each. The sidebar
lists a group's tasks together under its title.

PR: https://github.com/pyrlyn/octo/pull/34

Checked with `./gradlew check` locally: 383 tests, ktlint and detekt clean. `CompareTest` covers
grouping, dropping repeats, the model option and partial failure in core. `CompareViewModelTest`
covers the flag gate, terms settled first, partial failure in the dialog and the sidebar order.
CI did not run because of GitHub billing.
