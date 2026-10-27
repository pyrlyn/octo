---
status: todo
priority: P3
complexity: 4
readiness: 0%
agent:
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
