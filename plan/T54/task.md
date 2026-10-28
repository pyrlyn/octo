---
status: todo
priority: P3
complexity: 4
readiness: 0%
agent:
---

# T54. Run the core as a background service

Remote access (T15) and the `octo` command (T31) need the desktop app to be open. T3 Code runs as a
user service ([background-service.md](https://github.com/pingdotgg/t3code/blob/125b61954d/docs/user/background-service.md)).

Done means:

- `octo service install|status|restart|uninstall` sets up a headless core as a user launchd agent
  on macOS and a systemd user unit on Linux.
- The headless core serves the remote-access API; the desktop app attaches to it when it runs
  instead of starting a second core (the single-instance lock decides).
- Uninstall leaves tasks, worktrees and settings intact.
- Service file generation has tests; nothing touches the real `HOME` in tests.
