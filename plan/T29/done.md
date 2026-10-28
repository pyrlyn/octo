# T29. Done

PR: https://github.com/pyrlyn/octo/pull/37

## What was done

- **`usage` feature flag.** It adds the Usage screen, which shows token use per agent and model for today, 7 days, 30 days or all time. You open it from the sidebar or the palette.
- **`UsageHistory` on `AgentAdapter`.** Claude Code and Codex read their own local session logs, and only token counts:
  - Claude reads `projects/*.jsonl` and counts each reply once by `message.id`.
  - Codex reads its rollout files and counts the growth of their running totals.
- **`UsageLog` (`usage.json`).** It records the growth of each task's `SessionStats` when a turn ends. Agents without their own history are counted from it. `UsageReport` merges the two sources without counting anything twice.
- **`AgentEvent.RateLimited`.** It comes from Claude's rejected `rate_limit_event` and from Codex's `account/rateLimits/updated` with a reached limit. `TaskView.limit` holds it until the next turn.
  - The task screen shows "Limited until HH:MM" with Resume, which sends "Continue" and restarts the agent first if it stopped.
  - The sidebar row says "Limited".

## How it was checked

`./gradlew check` is green: ktlint, detekt, and 430 tests on JVM. The new tests cover:
- Claude and Codex history, run against fixture homes in temp dirs.
- The recorder and log, including a restart and a broken file.
- The report merge.
- Limit events from recorded transcripts.
- `LimitTest` in core.
- `UsageViewModelTest` in ui.
