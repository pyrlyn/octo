---
status: todo
priority: P2
complexity: 3
readiness: 0%
agent:
---

# T48. See and stop the dev servers a task started

Agents start dev servers and watchers that keep running after the turn; the user has to find their
ports and kill them by hand. T3 Code lists the servers a thread started and stops them
([a27ec88](https://github.com/pingdotgg/t3code/commit/a27ec88bfa)).

Done means:

- `ProcessMonitor` reports the listening ports of a task's process tree, on local and remote
  machines.
- The task screen lists them with Open (browser on `localhost:port`, forwarded over the shared ssh
  connection for remote tasks) and Stop (kills that process tree only).
- Stopping or removing a task stops them.
- Core has tests with a fake process table.
