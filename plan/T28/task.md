---
status: todo
priority: P2
complexity: 2
readiness: 0%
agent:
---

# T28. Pin, snooze and settle tasks

With many tasks the sidebar becomes a flat list. T3 Code sorts threads into Pinned, Active,
Snoozed and Settled, and settles a thread when its pull request merges
([thread-sidebar.md](https://github.com/pingdotgg/t3code/blob/36d4d71/docs/user/thread-sidebar.md)).

Behind a feature flag. Done: the user can pin, snooze (until a time or the next event) and
settle a task; the sidebar shows the sections; a task whose PR (from T11) is merged is settled
automatically. The state is persisted. Tests cover the transitions; `./gradlew check` passes.
