---
status: in progress
priority: P2
complexity: 1
readiness: 90%
agent: Claude Code / claude-opus-5-5
---

# T72. Token line under the composer in IBM Plex Mono

The line under the composer (`↑ sent · ↓ received · tok/s`, shown while the `usage` flag is
on) is set in the proportional caption face, so its figures shift width as they tick up. The
creator wants it in a monospaced face: IBM Plex Mono. The line stays behind the `usage` flag.

Done means:

- IBM Plex Mono 6.4.0 (Regular, Medium) ships in `design/src/jvmMain/resources/fonts`, under
  the existing `LICENSE-IBM-Plex.txt`, with a row in `toolchain.md`.
- `design` exposes the family as `FiguresFont`.
- The context and token line under the composer uses it on both the macOS screen
  (`MacTask.kt`) and the Pyrlyn screen (`TaskScreen.kt`); size and colour stay as they are.

Plan:

1. Download the two TTFs from the `v6.4.0` tag of https://github.com/IBM/plex.
2. Add `FiguresFont` to `Fonts.kt` / `Fonts.jvm.kt`.
3. Swap the font family of the line in `MacPromptBox` and `TaskScreen`.
4. Add the `toolchain.md` row.

Check: `./gradlew check` is green; the macOS app shows the line in IBM Plex Mono with the
`usage` flag on.
