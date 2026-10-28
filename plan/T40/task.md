---
status: todo
priority: P1
complexity: 2
readiness: 0%
agent:
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
