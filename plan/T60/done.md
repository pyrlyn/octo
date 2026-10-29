# T60. Done

## What changed

- `ui/.../theme/`: Pyrlyn colours (dark, light), the macOS, Windows and Linux recipes (radii,
  control and row height, highlight, depth, body size) chosen by `currentHostOs()`, type,
  `Modifier.glass` (shadows, veil, tint, sheen, specular line, rim) and `GlassBackdrop`. `OctoTheme`
  maps the tokens into the Material 3 colour scheme, typography and shapes, and drops the 48dp
  touch target, which a desktop pointer does not need.
- `Kit.kt`, `Fields.kt`: primary, secondary, danger and ghost buttons, `GlassDialog`, `GlassMenu`,
  `Notice`/`Banner`, pane header, table header and rows, section label, empty state, meter, accent
  tile, and `GlassField`, a compact field with its label above it.
- Shell: backdrop, inset glass sidebar (Octo mark, repo groups, task rows with hover and selected
  states), toolbar with the palette search field, glass content panel; empty and first-run states.
- Task screen: two-row header, permission chip, transcript cards, checkpoint rule, queue rows,
  floating prompt, request panel, limit notice, remove dialog.
- Diff, agents and usage panes on the shared table and empty-state pieces. `AgentRow` now carries
  the auth state and the context share, so the agents table draws a status dot and a context bar.
- Every dialog and menu goes through the kit; errors show as notices. The palette opens near the
  top as a glass panel with Tasks, Messages and Commands sections.
- Usage chart: `UsageReport.totals` takes bucket edges and returns a token series; the usage pane
  draws one bar per hour (today) or day (7 and 30 days) in the local zone. No chart for all time.
- Sidebar agents: one `AgentsViewModel` now lives as long as the window, so the sidebar lists the
  running agents (task title, context bar, CPU and memory) and the agents pane reuses it. Process
  sampling therefore runs whenever agents run, not only while the agents pane is open.
- Toolbar summary: `SummaryViewModel` shows today's tokens and the number of running agents. It
  recounts on every new usage record and every ten minutes, keeps the last figure when a history
  cannot be read, and shows only the agent count while the `usage` flag is off.

- Second design drop:
  - IBM Plex Sans 6.4.0 and JetBrains Mono 2.304 (both OFL 1.1, licences beside them) ship as
    `:ui` resources and replace the system fonts.
  - Menus and dialogs draw an opaque underlay under their veil, since Compose has no backdrop blur
    and the text behind them showed through.
- Settings (`SettingsViewModel`, `SettingsScreen`): sections General, Agents, Permissions, Devices,
  Appearance and Advanced over every feature flag, the default permission mode, worktree setup per
  repository, the remote-access port, paired devices and ssh machines, plus the raw file and a
  reset. Opened from the sidebar and the palette.
  - Every change goes straight to `config.json` through core's new `ConfigFile`. It edits the JSON
    tree, so keys Octo does not know survive, and it refuses to overwrite a file it cannot parse.
  - Values are checked with the same rules core applies at start and refused in Octo's own words.
  - Core reads the config only at start, so the screen asks for a restart.
- Appearance (`appearance` in `config.json`): theme System, Light or Dark; Glass or Opaque
  material; Compact, Normal or Comfortable rows (4dp off or on the platform's sizes). These apply
  at once.
- Onboarding (`OnboardingViewModel`, `OnboardingScreen`): shown on the first start (no
  `config.json` and no tasks).
  - It checks the repository is a git checkout and takes an optional setup command.
  - It lists the agents found and lets you pick an installed one, warning about vendor terms that
    are still pending.
  - It then sets the default permission mode.
  - Finishing writes `config.json` and opens the new-task dialog filled in with those choices.
    Skipping writes an empty one, so the guide does not come back.

- Module split (the creator asked for it in this task):
  - `:design` (Kotlin Multiplatform, JVM target only, since Compose has no Linux or Windows
    native target) holds the tokens (colours, type, spacing, platform recipes, glass) and the
    reusable components. It imports nothing from `:core`: the host OS and row density map onto a
    recipe in the shell, and the agent icon is a plain SVG string. Fonts and SVG drawing are the
    only `jvmMain` code.
  - `:feature:common` holds what several features share: status dots and words, the auth colour,
    token counts, agent choice, the vendor terms dialog and its accept-and-retry step (the
    new-task and switch-agent view models each had a copy), attachments and the folder picker.
  - `:feature:{task,diff,newtask,agents,usage,devices,palette,settings,onboarding}` each own their
    screens and view models. The task screen takes a `TaskPlaces` interface that the main view
    model implements, so no feature depends on the shell.
  - `:ui` is the shell: window, sidebar, toolbar, tray, the main, notification and summary view
    models, and the view model graph. Tests that build a `MainViewModel` or `ViewModels` stay
    there; the rest moved with their view models.

## Not done, and why

- No native window vibrancy, reduced transparency or reduced motion: the creator chose an in-app
  backdrop, and the rest needs native interop. Appearance has no "System" transparency and no high
  contrast for the same reason.
- The OS title bar stays; the renders show a drawn macOS window frame.

## How it was checked

- `./gradlew check` is green, including ktlint, detekt and the 97 UI tests (61 in `:ui`, the rest
  in the feature modules) plus a `:design` test of the row-density step.
  - Core tests cover the series buckets and `ConfigFile`: unknown keys survive, machines are added
    and replaced by id, and a broken file is never overwritten.
  - `:ui` tests cover the chart buckets, the toolbar summary, the settings view model (sections,
    restart notice, refusals, broken file, reset) and onboarding (every step, skip, terms, staying
    hidden). The settings and onboarding tests run on temp files, never the real home folder.
- A throwaway `ImageComposeScene` test (not committed) rendered the main task, empty, agents, new
  task, palette and usage screens in both themes with the macOS recipe, with process and usage
  data for the sidebar agents, toolbar summary and chart. I compared those renders with the
  design renders. Windows and Linux differ only in recipe values and were not rendered.
- The same harness rendered every settings section, the broken-file notice and the four onboarding
  steps in both themes. I compared those with the proposed screens.
- After the split the same harness rendered the task, empty, agents, new-task and palette screens
  in both themes; all ten PNGs are byte-identical to the renders from before it. Changing the
  `Gap` token alone changed all ten, then it was put back.
- jscpd over the UI modules finds 12 clones (91 lines), down from 15 (124) before the split; what
  is left is import blocks and a one-line `showError` per view model.
- The design archive was corrected to match: the agent step of onboarding picks one agent with
  radio buttons, and its last step says Full access stays in Settings › Permissions.

PR: see the branch `t60-pyrlyn-glass`.
