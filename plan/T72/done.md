# T72. Done

IBM Plex Mono 6.4.0 (Regular, Medium, Bold) became Octo's one monospaced face, in
https://github.com/pyrlyn/octo/pull/91 (squash commit `66cccd9`):

- `design` exposes it as `MonoFont`; `Pyrlyn.type.mono` and the macOS `Mac.type.code` (through a
  `MacFonts.mono` override of kotlin-macos's Roboto Mono) use it, and the diff preview reads it
  instead of the system `Monospace`.
- The context and token line under the composer is set in it on both designs, still behind the
  `usage` flag.
- JetBrains Mono and its licence were removed; `toolchain.md` lists the change.

Checked: `./gradlew check` green locally against `pyrlyn/packages` `origin/main` (the local
`packages/` checkout lacked `kotlin-macos`); CI on the PR: `check` and `screenshots-linux` green, `screenshots-windows` cancelled when it merged.

T73 then took the same face to every text in the app.
