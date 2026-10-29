---
status: in progress
priority: P1
complexity: 4
readiness: 100%
agent: Claude Code / claude-opus-5-5
---

# T60. Pyrlyn Glass design for the desktop UI

The UI runs on stock Material 3 colours. The creator approved the Pyrlyn Glass design: 33 screens
(main window, banners, request panel, menus, dialogs, diff, agents, usage, new task, presets,
publish, switch agent, devices, palette) for macOS, Windows and Linux, dark and light. The renders
and Compose tokens came as `octo-pyrlyn-glass-screens.zip`, built from a prototype outside the repo.

Done: every screen in `:ui` follows the design on all three desktop OSes in both themes. Material 3
components stay, styled from Pyrlyn tokens; the window, sidebar, content panel, menus, dialogs and
prompt get the glass surfaces. No native window vibrancy (the creator chose an in-app backdrop).

The second design drop added an opaque underlay under floating surfaces, bundled IBM Plex Sans and
JetBrains Mono, and 11 proposed screens. The creator chose to bundle the fonts and to build the
proposed settings (general, agents, permissions, devices, appearance, advanced, error) and
first-run onboarding (welcome, repository, agent, permissions) in this task, writing to
`config.json`.

## Plan

1. `ui/.../theme/`: Pyrlyn colours (dark, light), the three desktop recipes (radii, control and row
   height, body size), type, `Modifier.glass`, the backdrop, and `OctoTheme`, which maps all of it
   into a Material 3 colour scheme, typography and shapes.
2. Shared kit in `:ui`: primary, secondary and ghost buttons on the platform control shape, glass
   dialog with the `AlertDialog` slots, banner, empty state, pane header, table header and rows,
   section label, status colours from the tokens.
3. Shell (`OctoApp`, `Sidebar`): backdrop, inset glass sidebar, toolbar with the palette search
   field, glass content panel with the banners inside; empty and first-run states.
4. Task screen: header rows, permission chip, transcript cards (message, plan, tool, checkpoint),
   queue rows and the floating prompt; request panel; revert and remove dialogs.
5. Panes: diff, agents, usage, with empty states.
6. Dialogs and menus: new task (agent, preset, compare, error), vendor terms, presets, publish,
   switch agent, devices, palette, permission, place, plugin, context, move, share.
7. Verify: `./gradlew :ui:check ktlintCheck detekt`, then run the app and compare with the renders
   on macOS in both themes.
8. Second drop: fonts as `:ui` resources, the floating underlay and an opaque material, a core
   `ConfigFile` that edits `config.json` in place, `appearance` in the config, settings and
   onboarding view models and screens, tests, renders compared with the proposed screens.
9. Split `:ui` (the creator asked for it in this task):
   - `:design`, Kotlin Multiplatform (JVM target only, since Compose has no Linux or Windows
     native target): Pyrlyn colours, type, spacing, platform recipes, glass, and the reusable
     components (buttons, field, switch, segmented control, dialog, menu, notice, tables, meter,
     glyph, key cap). No `:core` types and no app state; controls keep only their own UI state.
   - `:feature:common`: app-level pieces several features share (status dots and words, auth
     colour, token counts, agent choice, vendor terms dialog, attachments, directory picker,
     coroutine helpers).
   - `:feature:{task,diff,newtask,agents,usage,devices,palette,settings,onboarding}`: each owns
     its screens and view models and builds them from `:design`. The task screen takes a
     `TaskPlaces` interface instead of the main view model.
   - `:ui` stays the shell: window, sidebar, toolbar, tray, main and notification view models,
     the view model graph, and the host OS → platform recipe mapping.
   - Tests move with their view models; the mixed `ViewModelTest` stays in `:ui`.
   - Check: `./gradlew check` green; no colour literals outside `:design`; jscpd finds no clone
     across the new modules.
10. Drop Material 3 as the base (the creator asked for it in this task): Pyrlyn's theme and
    controls are its own, on Compose foundation only.
    - `PyrlynTheme` provides only Pyrlyn's locals: colours, platform, type, opaque, content
      colour, text style, text selection colours and a Pyrlyn state-layer indication instead of
      Material's ripple. No `MaterialTheme`, colour scheme, shapes or typography.
    - `:design` gains `Text`, `GlassCheckbox`, `GlassRadio`, `GlassMenuItem`, `GlassSelect` and
      `GlassChip`; the buttons, dialog, menu and field decoration are rewritten on foundation
      (`Popup`, `Dialog`, `BasicTextField`), drawn to the prototype's specs.
    - Every feature module and `:ui` move off `androidx.compose.material3`;
      `compose.material3` leaves `design/build.gradle.kts`.
    - Check: `./gradlew check` green; `git grep material3` finds nothing in code or build files;
      renders compared with the design screens in both themes.
