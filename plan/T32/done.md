# T32. Done

Shipped in https://github.com/pyrlyn/octo/pull/40 behind the `triggers` flag.

- `config.json` `triggers`: a task template plus either a standard five-field cron `schedule`
  (local time) or a `webhook` (`secret`, `events`, `filters`).
- Schedules nap against the wall clock in steps of at most a minute; `triggers.json` keeps the
  last run, so missed runs collapse into one and a new trigger never runs for the past.
- Webhooks: `POST /api/v1/hooks/{id}` on the remote-access API, before the device-token check.
  The call is authenticated by a GitHub-style HMAC-SHA256 signature or a bearer secret, then
  checked against the event filter, the dotted JSON-path filters and a delivery id
  deduplicated over the last 100. The API answers 202 before the task starts.
- Cron matching uses krontab 3.1.0 on the JVM, behind the common `Cron` interface. It has no
  macosArm64 build. `KrontabCronParser` expands every field itself to cover these krontab
  behaviours: 0-based days and months, no 7 for Sunday, day of month ANDed with the weekday,
  and missing days clamped to the month's end. It also handles daylight-saving changes.
- Every problem is a warning, never fatal; `OctoCore.warn` carries the ones that come up at run time.

Checked with `./gradlew check` (ktlint, detekt, all tests), which passes locally:
- `TriggersTest` (virtual time);
- `KrontabCronParserTest` against real krontab, including the Europe/Berlin daylight-saving change;
- the hook route in `RemoteApiTest`, `RemoteHttpServerTest` (with the RFC 4231 HMAC vector) and `OctoTest`.

CI did not run because of the GitHub billing issue.
