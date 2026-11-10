---
status: in progress
priority: P2
complexity: 2
readiness: 80%
agent: Claude / claude-opus-5-5
---

# T73. IBM Plex Mono for every text

T72 made IBM Plex Mono the one monospaced face, beside IBM Plex Sans (Pyrlyn design) and Inter
(macOS design) for the rest of the text. The creator wants a single face for the whole app: every
text style, on every platform, in IBM Plex Mono, and the Figma file Octo macOS to match.

Plan:

1. `design`: `PyrlynTheme` builds the macOS `MacFonts` from `MonoFont` for both text and code,
   and the non-macOS `PyrlynType` uses `MonoFont` for every style. Drop `UiFont`, the IBM Plex Sans
   files and its `toolchain.md` row; keep `LICENSE-IBM-Plex.txt`, which covers Plex Mono.
2. `feature/diff` `PreviewView` and `feature/devices` `DevicesDialog`: drop the per-text font
   overrides so they inherit the theme face.
3. Figma [Octo macOS](https://www.figma.com/design/h9sViI6qnym6iDdXDZqxcq/Octo-macOS): text
   styles and loose text nodes switch to IBM Plex Mono, keeping size; Semi Bold maps to Bold,
   which is how Compose resolves weight 600 against the bundled Regular, Medium and Bold.

Check: `./gradlew ktlintCheck detekt build` passes; the app shows IBM Plex Mono in the sidebar,
transcript and dialogs; Figma text styles list IBM Plex Mono only.
