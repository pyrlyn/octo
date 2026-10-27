---
status: in progress
priority: P2
complexity: 3
readiness: 100%
agent: Claude Code / claude-opus-5-5
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

## Execution plan

1. Core (`octo.core.usage`): `UsageRecord`, the `UsageHistory` an adapter may offer through
   `AgentAdapter.usageHistory`, a `UsageLog` in `usage.json` filled by a recorder that watches
   each task's token totals, and a `UsageReport` that sums records per agent and model since a
   given moment. An agent with its own history is counted from it, never also from Octo's log.
2. Core: `AgentEvent.RateLimited(resetsAtMs)`; `TaskView.limit` set by it and cleared when the
   next turn starts. Flag `usage`.
3. Adapters: Claude reads `<config dir>/projects/**/*.jsonl` (assistant messages, deduplicated by
   message id) and maps `rate_limit_event` with status `rejected`; Codex reads
   `<CODEX_HOME>/sessions/**/rollout-*.jsonl` (`token_count` totals as deltas, model from
   `turn_context`) and maps `account/rateLimits/updated` with `rateLimitReachedType` set. Shared
   JSON-lines walking in `adapters:base`, re-reading only files whose size changed.
4. UI: a Usage screen (period presets: today, 7 days, 30 days, all) opened from the sidebar and
   the palette; a "Limited until HH:MM" banner on the task with a Resume button that sends
   "Continue" (restarting a stopped agent first).
5. Tests on recorded history files in temp dirs, never the real `HOME`; README and architecture;
   `./gradlew check`.
