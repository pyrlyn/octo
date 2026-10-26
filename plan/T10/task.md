---
status: in progress
priority: P1
complexity: 4
readiness: 100%
agent: Claude Code / claude-opus-5-5
---

# T10. MVP UI

Compose UI wired to the core: task list with status, new task with agent and base branch, live output per agent, prompt box, answers to questions and permissions, stop and restart, diff review, layout persisted.

The UI follows MVVM (creator's decision): a view model per screen holds one immutable state
as a `StateFlow` and turns user actions into `OctoCore` calls; composables only render state
and forward actions.

Check: The app runs a fake agent end to end; UI state tests pass.

## Execution plan

1. Core: an `AgentCatalog` interface (agents with their probe, pending terms, acknowledging terms)
   with a registry-backed implementation, so the UI never touches adapters.
2. `adapters/demo`: an in-process demo agent behind the `demo-agent` flag (off by default). It
   asks for permission, writes a file into the worktree and streams a reply, so a whole task can
   run without a real agent or account.
3. `ui` view models, each exposing one `StateFlow`:
   - `MainViewModel`: tasks grouped by repository with the status rollup, selection, layout;
   - `TaskViewModel`: transcript, pending requests, prompt draft, stop, cancel, restart, remove;
   - `NewTaskViewModel`: the form, agent choice, the terms notice;
   - `DiffViewModel`: files and hunks.
4. Composables that only render that state: sidebar, task screen, request panel, new-task
   dialog with the terms notice, diff screen, a draggable sidebar divider.
5. `app`: build the paths, config, registry, `Orchestrator` and catalog, show the window, and
   close the core on exit.
6. Tests: every view model over a fake `OctoCore`; an end-to-end test in `app` that drives the
   real orchestrator, a temp git repo and the demo agent through the view models.
7. `./gradlew ktlintFormat`, `./gradlew check -Pocto.allNativeTargets=true`, and a launch of the
   app.
