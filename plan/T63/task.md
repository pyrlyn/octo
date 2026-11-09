---
status: in progress
priority: P1
complexity: 5
readiness: 60%
agent: Claude Code / claude-opus-5-5
---

# T63. Octo macOS design

The creator approved a native-looking macOS design for Octo: the Figma file
[Octo macOS](https://www.figma.com/design/h9sViI6qnym6iDdXDZqxcq/Octo-macOS?node-id=40-1092), page `Octo`
(node `40:1092`), 27 dark screens built from the MacOS-Pyrlyn-UI library: main window, switch agent, new task,
agents, diff, diff preview, settings (general, agents, devices), publish, share, move task, presets, usage,
devices, add machine, command palette, notifications, permission request, task menus (context, place,
permissions, plugins), onboarding (welcome, repository, agent, permissions), terms and worktree setup.

It is opaque, not glass: a chrome sidebar and toolbar, an editor-coloured content area, pill buttons, Inter and
Roboto Mono, 16-point stroke icons.

Decisions the creator took for this task:

- One task, one Octo PR. The shared package lives in another repository (pyrlyn/packages), so it ships as its own
  PR there and lands first, since Octo's CI checks out packages' `main`.
- macOS only. Windows and Linux keep the Pyrlyn Glass recipes from T60.
- Inter and Roboto Mono (both SIL OFL 1.1) are bundled, as the design uses them.
- The file has dark screens only; the light palette is derived in the same token structure for the creator to
  review.
- Generic pieces go to a new shared package, `packages/kotlin-macos`, with the most reuse possible; Octo keeps only
  what is Octo's.

Added by the creator while the task ran:

- The composer and the pickers (agent and model, permissions, repository, branch, machine) follow the creator's
  reference images: a raised 20-point card, borderless pickers with a chevron, a round send button.
- The window shows the system's blurred material (vibrancy) behind see-through chrome and content grounds; the
  opaque material and Reduce Transparency keep them solid.
- A gauge next to the composer opens, on hover, the session's context and tokens: sent, received, cache, and the
  output speed in tokens per second; a line under the composer shows the same totals.

Done: on macOS every one of the 27 screens matches its frame in both themes (dark from the file, light derived),
built from `pyrlyn.macos` components; Windows and Linux look as before; `./gradlew check` is green in Octo and in
kotlin-macos.

## Plan

1. **`packages/kotlin-macos`** (module `:macos`, package `pyrlyn.macos`, jvm and wasmJs, Compose runtime,
   foundation and ui only, no Material 3, as Octo's `:design`):
   - Tokens from the file's variables: `MacColors` (dark from Figma, light derived), `MacType` (UI/Title, Body,
     Body emphasized, Small, Small emphasized, Caption, Caption bold, Section label, Code/Body, Code/Small,
     Code/Terminal), `MacSpace`, `MacRadius`, the Overlay elevation; `MacTheme`.
   - Fonts: Inter and Roboto Mono in `jvmMain` resources behind `MacFonts`; a consumer may pass its own.
   - Icons: the library's 16-point stroke icons as `MacIcon`, from the file's SVG assets.
   - Components, each from its library component: button (primary, secondary, outline, destructive outline,
     ghost; regular and large), icon button, icon tile, chip, status, status dot, sidebar row, settings item,
     section label, search field, banner, message, command card, labeled divider, composer, sheet (dialog) with
     scrim, menu and menu item, pop-up button, text field, switch, radio row, stepper, settings row and group,
     list row, tree row, table header and row, code line, palette row, notification, meter, window controls.
   - README, AGENTS.md, plan/, roadmap.md, ideas.md, toolchain.md; a row in packages' README; tests for the
     tokens and the controls' states; `./gradlew check`.
2. **Octo `:design`**: depend on `pyrlyn.macos:macos` through `sharedBuild("kotlin-macos", "KOTLIN_MACOS_DIR")`.
   `PyrlynTheme` gets a look chosen with the platform recipe: on macOS `Pyrlyn.colors`, type, radii and fonts come
   from `MacTheme`, `Modifier.glass` paints the opaque surface, and the kit (buttons, dialog, menu, notice, field,
   switch, choice, chip, section label, table, empty state, meter) delegates to the `pyrlyn.macos` component.
   Glass on Windows and Linux stays as it is.
3. **Shell** (`:ui`): the macOS sidebar (header with New task, repository sections, task rows with status dots,
   footer with Agents, Usage, Devices, Settings), toolbar (summary and search field), content area.
4. **Task screen** (`:feature:task`): three-row header (title with Restart and Stop; status, branch and place;
   permission and context buttons, Switch agent, Fork, Diff, Publish), transcript (banner, messages, thinking,
   command card, edit summary, checkpoint divider), composer; request panel; menus and dialogs of the task
   (switch agent, publish, share, move, context, place, permissions, plugins, permission request).
5. **Panes and dialogs** in their feature modules: diff and diff preview, agents, usage, devices and add machine,
   palette, notifications, new task, presets, terms, worktree setup, settings (general, agents, devices),
   onboarding (welcome, repository, agent, permissions).
6. **Check**: `./gradlew check` in kotlin-macos and Octo; jscpd finds no new clone; no colour literal outside
   `:design` and the package; run the app on macOS and compare every screen with its frame in both themes;
   Windows and Linux recipes unchanged (existing screenshot tests).
7. `toolchain.md` (Octo and package), `docs/architecture.md`, `plan/README.md` decision; PR in packages, then
   the Octo PR.
