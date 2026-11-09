---
status: in progress
priority: P2
complexity: 2
readiness: 90%
agent: Claude / claude-opus-5-5
---

# T72. JetBrains Mono for the whole app

The creator wants one face across Octo: JetBrains Mono for every text style, not only code.
Today the macOS design draws with Inter and Roboto Mono (from kotlin-macos `bundledFonts`), the
other platforms with IBM Plex Sans and JetBrains Mono, and a few feature views force the system
monospace (`FontFamily.Monospace`). Done means every text in the app, on every platform, is set in
the bundled JetBrains Mono, and the Figma file Octo macOS uses it in its text styles.

Plan:

1. `design`: `PyrlynTheme` builds the macOS `MacFonts` from Octo's own `MonoFont` for both text
   and code, and the non-macOS `PyrlynType` uses `MonoFont` too. Drop `UiFont`, the IBM Plex Sans
   files, their licence and the `toolchain.md` row, which nothing reads any more.
2. `feature/diff` `PreviewView` and `feature/devices` `DevicesDialog`: drop the
   `FontFamily.Monospace` / `FontFamily.Default` overrides so they inherit the theme face instead of
   the system one.
3. Figma [Octo macOS](https://www.figma.com/design/h9sViI6qnym6iDdXDZqxcq/Octo-macOS): text
   styles and loose text nodes switch their family to JetBrains Mono, keeping size and weight.

Check: `./gradlew ktlintCheck detekt build` passes; the app launched with `hotRun` shows JetBrains
Mono in the sidebar, transcript and dialogs; Figma text styles list JetBrains Mono only.
