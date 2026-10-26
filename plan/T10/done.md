# T10. Done

## What was done

- `core`: `AgentCatalog`, with `RegistryAgentCatalog` behind it, is the UI's only view of adapters.
  It probes every registered agent in parallel, returns an agent's pending terms, and records the
  user's acknowledgement through `TermsGate`.
- `adapters/demo`: `DemoAdapter`, an in-process agent behind the `demo-agent` flag (off by
  default). Each prompt plays one turn: a thought, the plan, a tool call, then a permission
  request. Allow appends the prompt to `octo-demo.md` in the worktree and streams a reply.
  Reject skips the write; cancel ends the turn. Lets a whole task run without a real agent or
  account.
- `ui`, MVVM: each view model exposes one immutable state as a `StateFlow`. Local edits update
  that state synchronously, so text fields never lag; data from core is merged in by a collector.
  A failing action becomes an error in the state, never a crash.
  - `MainViewModel`: tasks grouped by repository with the status rollup; selection, sidebar
    width and diff toggle, persisted through `updateLayout`; start-up and core warnings.
  - `TaskViewModel`:
    - live transcript, status and pending requests;
    - prompt draft, restored if sending fails;
    - permission options, question answers (single, multi and free text), dismiss;
    - cancel, stop, restart;
    - remove, with a second confirmation for a dirty worktree;
    - marks finished turns seen while open.
  - `NewTaskViewModel`: repository, base branch, title (falls back to the prompt's first line),
    prompt, agent choice (only installed agents can be picked). When `createTask` throws
    `TermsNotAcknowledgedException` it shows the vendor's terms; accepting acknowledges them and
    retries.
  - `DiffViewModel`: loads on open and on refresh, and keeps the selected file while it still
    has changes.
  - `ViewModels` builds them, so composables never hold core.
- `ui` composables render state and forward actions:
  - sidebar with status dots;
  - a draggable divider that persists only the final width;
  - the task screen: header actions, transcript that follows new output, request panel, prompt
    box (Ctrl/Cmd+Enter sends), remove dialogs;
  - the new-task dialog: a folder picker, agent notes (not installed, logged out, version
    drift) and the terms dialog with a link to the vendor's page;
  - the diff screen: file list with +/- counts, and hunks with added and removed lines
    highlighted.
  No experimental Material 3 API is used.
- `app`: `Octo.open` reads `config.json` (an unreadable or invalid file falls back to defaults
  with a warning). It builds the registry, `TermsGate`, `GitWorkspace`, `StateStore` and
  `Orchestrator`. `Main` shows the window and closes the core on exit. The demo adapter is
  registered with the other adapters.
- detekt: `MagicNumber` now ignores property declarations, because UI sizes and colours live in
  named properties.
- Docs: the demo adapter row is in `docs/architecture.md`; README says how to turn the demo on.

## How it was checked

- `./gradlew check -Pocto.allNativeTargets=true`: all green (JVM and native tests, ktlint,
  detekt).
- New tests:
  - `DemoAdapterTest` (2);
  - `ViewModelTest` (6, over a fake `OctoCore`):
    - grouping, rollup and layout;
    - prompt, answers, cancel and seen marking;
    - dirty-worktree removal;
    - error with the draft restored;
    - agent choice and the terms flow;
    - diff selection across refreshes.
  - `AdaptersTest`: the demo agent is absent without its flag.
  - `EndToEndTest` (app) drives the real `Orchestrator`, `GitWorkspace` on a temp repo (temp
    `HOME`, git config sealed off) and the demo agent through the view models: create the task,
    see the permission request, allow it, see the reply, the diff shows `octo-demo.md` with one
    added line, stop.
- `./gradlew :app:run` with a temp `OCTO_HOME` and the demo flag on: the window opens and the
  log shows no exceptions.
- CI on `pyrlyn/octo` is blocked by GitHub billing, so the PR has no CI run.

## Not verified

- The window was not inspected visually: access to it was declined, so layout and look were
  not checked by eye.
- The folder picker (`JFileChooser`) and the terms link (`LocalUriHandler`) were not clicked.
- Windows and Linux were not launched.
