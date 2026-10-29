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

## Not done, and why

- No native window vibrancy, bundled fonts, reduced transparency or reduced motion: the creator
  chose system fonts and an in-app backdrop, and the rest needs native interop.
- The OS title bar stays; the renders show a drawn macOS window frame.

## How it was checked

- `./gradlew check` is green, including ktlint, detekt and the 85 `:ui` tests; core tests cover
  the series buckets, and `:ui` tests cover the chart buckets and the toolbar summary.
- A throwaway `ImageComposeScene` test (not committed) rendered the main task, empty, agents, new
  task, palette and usage screens in both themes with the macOS recipe, with process and usage
  data for the sidebar agents, toolbar summary and chart. I compared those renders with the
  design renders. Windows and Linux differ only in recipe values and were not rendered.

PR: see the branch `t60-pyrlyn-glass`.
