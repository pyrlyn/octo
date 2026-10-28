# T40. Done

PR: https://github.com/pyrlyn/octo/pull/53; the per-task cooldown came from https://github.com/pyrlyn/octo/pull/51

## What was done

- **Flag and settings.** The `notifications` flag. `notifications` in `config.json` (`blocked`, `failed`, `done`, all on by default) chooses which events notify.
- **Rule.** `taskAlerts` (`core/notify`, common code) turns the state stream into alerts for a task that moves into Blocked, Failed or Done. Each task's alert waits 1.5 s. A newer one replaces it, and one whose state is gone by then is dropped, so a burst shows once. A task alerts at most once in 20 s; a later alert is held until then. The first state is only a baseline.
- **UI.** `NotificationsViewModel` posts an alert unless the window is focused on that task. A click opens the alerted task. macOS only activates the app on a click, so the window gaining focus after an alert posted in the background opens that task once.
- **App.** A Compose `Tray` posts the notifications and brings the window forward on its action. No new dependency.
- **Docs.** The README and the architecture note.

## How it was checked

`./gradlew check` is green: ktlint, detekt and all tests.
- `TaskAlertsTest` covers the quiet period, the start-up baseline, bursts, stale events, the cooldown and the config.
- `NotificationsViewModelTest` covers focus, the open task, clicks and the flag.
- `MachineConfigTest` parses the key and its defaults.
- `./gradlew :app:run` with the flag on in a temp `OCTO_HOME` started without errors. A real notification and a click on it were not tried.
