---
status: in progress
priority: P2
complexity: 3
readiness: 85%
agent: Claude Code / claude-opus-5-5
---

# T64. Open the task's worktree in another app

The creator asked for an "Open location" split button at the top right of the window, as in their reference: the
main part opens the selected task's worktree in the last app used, the chevron lists the apps (Finder, Terminal,
VS Code, Cursor, Zed, Xcode, Android Studio, RustRover, iTerm, Warp), each with its own icon. Without a selected
task the button is not shown.

Decisions the creator took:

- Its own task and PR, after T63.
- All three OSes, each with its own list of apps.
- Only the apps installed on this machine are listed.

Done: with the `open-in` flag on and a local task selected, the button sits at the top right on macOS, Windows and
Linux and opens the worktree in the chosen app; the last choice survives a restart; a remote task or no selection
hides it; the design is in the Figma file; `./gradlew check` is green in Octo and kotlin-macos.

## Plan

1. **Package** (`kotlin-macos`): `SplitButton` (a main action and a chevron that opens a menu) and a `MenuItem`
   with a leading image slot, with tests.
2. **Logic** (`feature/common`, `OpenIn.kt`): an `OpenApp` catalogue per OS (macOS app bundles in the
   Applications folders; Windows and Linux commands on `PATH` and known install paths), `AppOpener` that finds the
   installed ones and launches one on a folder (`open -a` on macOS, the command on Windows and Linux; the file
   manager through `Desktop.open`, moved here from settings), app icons through `FileSystemView`, with a glyph when
   the OS has none. Lookups and launches go through injected functions so tests need no real apps.
3. **Flag and config**: `open-in` in `CoreFlags`; `openIn` in `config.json` keeps the last app.
4. **View model** (`ui`): `OpenInViewModel` lists the apps once, opens the selected task's worktree, remembers the
   choice; null while the flag is off.
5. **UI**: the split button at the right of the toolbar on macOS and Windows and in the header bar on Linux, shown
   only for a selected local task.
6. **Figma**: the split button and its open menu in the Octo macOS file, next to the main window frame.
7. **Check**: unit tests for the catalogue and launch commands with fakes, the package tests, a `ShellShotsTest`
   render, `./gradlew check` in both builds, the app on macOS opening a worktree in Finder.
