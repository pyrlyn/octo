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

## Not done, and why

- No usage chart, sidebar agents list or toolbar token summary: `:ui` has no data for them yet.
- No native window vibrancy, bundled fonts, reduced transparency or reduced motion: the creator
  chose system fonts and an in-app backdrop, and the rest needs native interop.
- The OS title bar stays; the renders show a drawn macOS window frame.

## How it was checked

- `./gradlew check` is green, including ktlint, detekt and the 82 `:ui` tests.
- A throwaway `ImageComposeScene` test (not committed) rendered the main task, empty, agents, new
  task and palette screens in both themes with the macOS recipe. I compared those renders with the
  design renders. Windows and Linux differ only in recipe values and were not rendered.

PR: see the branch `t60-pyrlyn-glass`.
