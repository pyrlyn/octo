---
status: todo
priority: P3
complexity: 2
readiness: 0%
agent:
---

# T52. Sidebar grouped by repository, with custom snooze options

With many repositories the sidebar becomes one long list. T3 Code groups threads into project
folders, filters by several projects, hides projects with no active threads and lets the user pick
the snooze choices ([4e4d4ba](https://github.com/pingdotgg/t3code/commit/4e4d4ba395), [c38b265](https://github.com/pingdotgg/t3code/commit/c38b265adc), [001d16e](https://github.com/pingdotgg/t3code/commit/001d16e969), [93a9e07](https://github.com/pingdotgg/t3code/commit/93a9e079b1)).

Done means:

- A sidebar option groups tasks under their repository, collapsible, with the status rollup on
  the group.
- A filter picks one or several repositories; groups with no active task can be hidden.
- With `sections` on, the snooze menu offers the durations listed in `config.json`.
- The view model has tests.
