---
status: in progress
priority: P2
complexity: 2
readiness: 100%
agent: Claude Code / claude-opus-5-5
---

# T26. Send one prompt to several agents

Comparing agents on one task means creating each task by hand. T3 Code sends one prompt to
several models at once, each in its own worktree (`docs/research.md` §2.1, §8 item 20).

Behind a feature flag. Done: the new-task dialog accepts several agents (and models); Octo
creates one task per choice with its own branch, the same prompt and a shared group shown in the
sidebar, so the diffs can be compared. Tests cover group creation and partial failure;
`./gradlew check` passes.

## Execution plan

1. Core: `CoreFlags.COMPARE`; `TaskGroup(id, title)` on `NewTask` and `Task`, persisted.
   `OctoCore.compare(request, picks)` (an extension, like `startPreset`) creates one task per
   agent and model with the same prompt and one group. A pick that fails is reported and the rest
   still start.
2. UI: a third mode in the new-task dialog, "Compare agents", shown while the flag is on. Pick
   agents by checkbox, each with an optional comma-separated list of models (one task per model).
   Every chosen agent's vendor terms are settled first. A partial failure keeps the dialog open
   and says what did not start.
3. Sidebar: a group's tasks sit together under a header with the group's title, indented.
4. Wiring through `Octo`, `Main` and `ViewModels`; README and `docs/architecture.md`.
5. Tests: group creation, partial failure, the model option and the sidebar order (core and UI
   view models). `./gradlew check`.
