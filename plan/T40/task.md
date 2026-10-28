---
status: in progress
priority: P1
complexity: 2
readiness: 10%
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

## Plan

1. Core (`core/notify`, common code): `NotificationConfig` (`blocked`, `failed`, `done`, all on by
   default) under `notifications` in `config.json`, the `notifications` flag in `CoreFlags`, and
   `taskAlerts(config, quietMs)`: a `Flow<OctoState>` operator that turns a task's move into
   Blocked, Failed or Done into an `Alert`. Alerts for one task wait a quiet period; a newer one
   replaces the older, and one whose state is gone by then (answered, seen) is dropped.
2. UI: `NotificationsViewModel` posts an alert unless the window is focused on that task; a
   click opens the alerted task, and so does the window gaining focus after an alert posted
   while it was in the background (macOS activates the app on a click but sends no event).
3. App: a Compose `Tray` with `TrayState.sendNotification` while the flag is on; its action
   brings the window to the front. No new dependency.
4. Docs: README, `docs/architecture.md` flag table and notes.
5. Check: core tests for the rule (virtual time), a view-model test for focus and click,
   `./gradlew check` (ktlint, detekt, tests), and a manual run with the flag on.
