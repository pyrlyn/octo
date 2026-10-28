# T40. OS notifications — done

PR: https://github.com/pyrlyn/octo/pull/51

## What was done

- `notifications` flag in `CoreFlags`; `notifications` settings in `OctoConfig` (`blocked`,
  `failed`, `done`, each on by default).
- `octo.core.notify.NotificationRule` (common code): a task's entry into Blocked, Failed or Done
  starts a burst; the burst notifies once, with its latest event, after 2 s of quiet; a burst that
  ends where nothing awaits the user is dropped; a task notifies at most once per 20 s; the task
  the user is looking at in a focused window stays quiet; start-up only sets the baseline.
- `NotificationsViewModel` (ui) feeds `OctoCore.state` and the window focus into the rule and opens
  the last notified task on a click through `MainViewModel.select`.
- `NotificationTray` shows the notifications with Compose Desktop's `Tray` and
  `TrayState.sendNotification`; a click brings the window to the front. No new dependency.
- `Octo.notifications` and `Main.kt` wire it; README and `docs/architecture.md` describe it.

## Limits

AWT does not report which notification was clicked, so a click opens the task notified last. A
desktop without a system tray runs without notifications.

## How it was checked

- `NotificationRuleTest` (8 tests), `NotificationsViewModelTest` (3 tests) and
  `OctoTest.notificationsNeedTheirFlagAndReadTheirSettings`.
- `./gradlew check` passed locally: ktlint, detekt and every test (GitHub Actions is not running).
- Not checked by hand in the running app: the tray notification on each OS.
