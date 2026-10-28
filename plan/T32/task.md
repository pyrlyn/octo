---
status: in progress
priority: P3
complexity: 4
readiness: 100%
agent: Claude Code / claude-opus-5-5
---

# T32. Scheduled and webhook-triggered tasks

Recurring work (nightly dependency bumps, triage) needs a person to start it. T3 Code runs
scheduled tasks and tasks triggered by webhooks, with event filters and duplicate suppression
([0c85b0d](https://github.com/pingdotgg/t3code/commit/0c85b0d),
[2ebe280](https://github.com/pingdotgg/t3code/commit/2ebe280)).

Behind a feature flag. Done: a schedule (cron) or a webhook on the loopback API starts a task
from a saved template (repository, agent, prompt, options); webhook filters match payload fields
and repeated deliveries do not start duplicates; missed runs while Octo was closed run once on
start. Tests use a fake clock; `./gradlew check` passes.

## Execution plan

The creator's decisions: krontab parses schedules; triggers live in `config.json`, with no
editor in the UI; a webhook proves itself with its trigger's secret, either as a GitHub-style
`X-Hub-Signature-256` HMAC of the body or as `Authorization: Bearer <secret>`.

1. Config: `triggers` in `config.json`. Each trigger has an `id`, a `schedule` (standard
   five-field cron, local time) or a `webhook` (`secret`, optional `events` matched against
   `X-GitHub-Event`, `filters` from a dotted payload path to the value it must equal), and a
   `task` template (repo, baseRef, agent, title, prompt, options, machine, permissionMode).
   A trigger with problems is skipped with a warning.
2. Core, common, behind the new `triggers` flag:
   - a `Cron` interface, so the library stays outside common code;
   - `Triggers`: one loop per schedule that naps in short steps against the clock. A run
     missed while Octo was closed happens once at start. A new trigger never fires for the
     past.
   - Webhook deliveries are checked and filtered, then claimed by `X-GitHub-Delivery` (or the
     body's hash) so a repeat does not start a second task. The task starts in the background
     so the sender gets its answer at once; a failed start frees the claim again.
   - `triggers.json` keeps the last run and recent deliveries. Failures become warnings
     through a new `OctoCore.warn`.
3. Core, JVM: krontab behind `Cron`. Standard cron is translated into krontab's format
   (seconds first, zero-based days and months, `w` weekdays). An expression that restricts both
   the day of the month and the weekday is refused, because krontab ANDs them where cron ORs
   them.
4. `RemoteApi`: `POST /api/v1/hooks/{id}` without a device token. The loopback server now
   passes request headers through.
5. App wiring: webhooks without `remote-access` give a warning.
6. Tests with a virtual clock: schedules, missed runs, a new trigger, failures, and webhook
   auth, filters and duplicates. Plus the cron translation against krontab, and the route.
7. Docs: README, architecture, toolchain (krontab), done.md, PR.
