---
status: todo
priority: P2
complexity: 3
readiness: 0%
agent:
---

# T29. Usage and limits view

Octo shows token figures only for running sessions. T3 Code reads each agent's own history and
shows token use, cache savings and a per-model breakdown, and marks a thread "Limited" when the
provider's usage limit is hit, with a resume ([usage.md](https://github.com/pingdotgg/t3code/blob/36d4d71/docs/user/usage.md)).

Behind a feature flag. Done: a Usage screen with totals per agent and model over a chosen period,
from the history Octo records and, where an adapter can read it, the agent's own local history;
a rate-limit event from an agent shows the task as limited with the reset time and a resume
action. Octo still never reads credentials. Tests use recorded history; `./gradlew check`
passes.
