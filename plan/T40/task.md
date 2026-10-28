---
status: in progress
priority: P1
complexity: 2
readiness: 100%
agent: Claude Code / claude-opus-5-5
---

# T40. OS notifications

Octo marks a task "done" in the sidebar, but a user who is in another app never learns that an
agent finished, failed or is waiting for an approval or an answer. T3 Code alerts on exactly these
events ([mobile-notifications.md](https://github.com/pingdotgg/t3code/blob/125b61954d/docs/user/mobile-notifications.md)); Octo's research ranked OS
notifications as part of the status ladder (`docs/research.md` §8, row 8) and they were never built.

Done means:

- Behind a new `notifications` flag, the desktop app shows an OS notification when a task becomes
  Blocked (approval or question), Failed, or finishes a turn (Done), and the window is not focused
  or the task is not the open one.
- Clicking the notification opens the app on that task.
- Settings in `config.json` choose which of the three events notify; a burst of events for one task
  shows one notification, not many.
- No new third-party dependency: Compose Desktop's tray notifications or the platform's own command.
- The rule that picks events is common code with tests; the UI wiring has a view-model test.

## Execution plan

1. `core`: the `notifications` flag in `CoreFlags`; `notifications` settings in `OctoConfig`
   (`blocked`, `failed`, `done`, each on by default); `octo.core.notify.NotificationRule`, a pure
   class that turns successive task lists into notifications. It starts a burst when a task's
   sidebar status enters Blocked, Failed or Done, restarts the quiet period on every later change,
   drops the burst if the task leaves those states, and releases one notification per task once
   the burst has been quiet for a moment, unless the window is focused on that very task.
2. `ui`: `NotificationsViewModel` feeds `OctoCore.state` and the window's focus into the rule,
   waits out the quiet period, emits what to show, and on a click opens the last notified task
   through `MainViewModel.select`. `NotificationTray` shows them with Compose Desktop's `Tray`
   and `TrayState.sendNotification` (no new dependency).
3. `app`: `Octo.notifications` (null with the flag off), `ViewModels.notifications()`, and
   `Main.kt` wires the tray, the window focus and bringing the window to the front on a click.
4. Docs: the flag row in `docs/architecture.md` §6 and the settings in the README.
5. Verify: unit tests for the rule (`core` commonTest) and a view-model test (`ui`), then
   `./gradlew check` (ktlint and detekt included).
