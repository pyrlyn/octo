# T22. Done

- A core `PermissionMode` (Supervised, Auto-accept edits, Auto, Full access) kept on the task and
  persisted; the default comes from `defaultPermissionMode` in `config.json`.
- Adapters declare the modes they have; a mode an agent lacks runs Supervised with a notice.
  A running task switches live where the agent allows it, otherwise on its next start.
- Claude maps the modes to `--permission-mode` and `set_permission_mode`; Codex to
  `approvalPolicy`, `approvalsReviewer` and `sandbox` / `sandboxPolicy`. ACP agents declare
  Supervised only.
- UI: a Permissions choice in the new-task dialog and a Permissions menu on the task screen.

PR: https://github.com/pyrlyn/octo/pull/30

Checked with `./gradlew check` after merging `main` with T21: 321 tests, 0 failures (core JVM and
macOS, adapters, UI), ktlint and detekt clean. GitHub CI did not run: the account's billing blocked
every job.
