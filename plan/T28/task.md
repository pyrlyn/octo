---
status: in progress
priority: P2
complexity: 2
readiness: 100%
agent: Claude Code / claude-opus-5-5
---

# T28. Pin, snooze and settle tasks

With many tasks the sidebar becomes a flat list. T3 Code sorts threads into Pinned, Active,
Snoozed and Settled, and settles a thread when its pull request merges
([thread-sidebar.md](https://github.com/pingdotgg/t3code/blob/36d4d71/docs/user/thread-sidebar.md)).

Behind a feature flag. Done: the user can pin, snooze (until a time or the next event) and
settle a task; the sidebar shows the sections; a task whose PR (from T11) is merged is settled
automatically. The state is persisted. Tests cover the transitions; `./gradlew check` passes.

## Execution plan

1. Core: `Task.place` (`TaskPlace`: Active, Pinned, `Snoozed(untilMs)`, Settled), persisted;
   `OctoCore.setPlace`. A snooze without a time ends with the turn; blocked or failed wakes any
   snooze; a coroutine on an injected `Clock` wakes timed ones. New flag `sections`.
2. `SettleMerged` with `MergedBranches` (`gh pr list --state=merged` per repository, every five
   minutes), started by the app when `sections` and `git-publish` are on.
3. UI: sidebar sections per repository (a task moves with its forks, a comparison with its first
   task); a place menu in the task header.
4. Tests: place transitions and persistence, snooze wake-ups, merged settling, sidebar order.
   README, `docs/architecture.md`, `./gradlew check`.
