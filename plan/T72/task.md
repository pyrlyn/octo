---
status: in progress
priority: P2
complexity: 2
readiness: 80%
agent: Claude Code / claude-opus-5-5
---

# T72. IBM Plex Mono as the one monospaced face

The line under the composer (`↑ sent · ↓ received · tok/s`, shown while the `usage` flag is
on) was set in a proportional face, so its figures shifted width as they ticked up. The
creator chose IBM Plex Mono for it, then asked for IBM Plex Mono as the default monospaced
face everywhere. Octo had three: JetBrains Mono on the Pyrlyn design, Roboto Mono from
kotlin-macos on the macOS design, and the system `Monospace` in the diff preview.

Done means:

- IBM Plex Mono 6.4.0 (Regular, Medium, Bold) ships in `design/src/jvmMain/resources/fonts`,
  under the existing `LICENSE-IBM-Plex.txt`; JetBrains Mono and its licence are gone.
- `design` exposes it as `MonoFont`; `Pyrlyn.type.mono` and the macOS `Mac.type.code` both
  use it, and the diff preview reads it instead of the system face.
- The context and token line under the composer is set in it on both designs; size and
  colour stay as they are. The line stays behind the `usage` flag.
- `toolchain.md` lists the change.

Plan:

1. Download the TTFs from the `v6.4.0` tag of https://github.com/IBM/plex.
2. Point `MonoFont` at them; override `MacFonts.mono` in `PyrlynTheme`.
3. Replace `FontFamily.Monospace` in `PreviewView.kt`; set the token line in `MonoFont`.
4. Drop the JetBrains Mono files and row.

Check: `./gradlew check` is green; the macOS app shows code, paths and the token line in
IBM Plex Mono.
