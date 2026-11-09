# T64. Done

An "Open location" split button at the top right opens the selected local task's worktree in another app, behind the
`open-in` flag.

- `AppOpener` (`feature/common`) lists the apps installed on this machine from a catalogue per OS and launches one on
  a folder: `open -a` on macOS, the program in the folder on Windows and Linux, `Desktop.open` for the file manager.
- macOS icons come from each bundle's `.icns`, Windows icons from the shell, and a glyph for the app's kind otherwise.
- `openIn` in `config.json` keeps the last app, which becomes the button's main action.
- `OpenInViewModel` and `OpenInButton`: shown in the toolbar on macOS and Windows and in the header bar on Linux;
  hidden with no selection or for a remote task; a banner explains a failed launch.
- `SplitButton` was added to kotlin-macos (pyrlyn/packages#7, tag 0.0.4), and CI now checks out 0.0.4.
- Figma: `Split button` and `App menu item` components and the "Octo — Open in (macOS)" screen.

PR: pyrlyn/octo#81 (squash `1f6d6e5`).

Checked:

- Unit tests for each OS's catalogue and launch commands, icns parsing, and the view model (visibility, last app
  across a restart, error).
- `ShellShotsTest` renders on macOS and Windows chrome.
- `./gradlew check` locally and CI green.
- The app ran, but macOS returned no capture of its window, so the live menu was not checked by eye.
