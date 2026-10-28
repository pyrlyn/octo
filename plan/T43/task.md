---
status: todo
priority: P1
complexity: 4
readiness: 0%
agent:
---

# T43. Integrated terminal per task

To run a command in a task's worktree the user leaves Octo for another terminal and finds the
path by hand. T3 Code has a terminal per thread with kept scrollback ([terminal.md](https://github.com/pingdotgg/t3code/blob/125b61954d/docs/user/terminal.md)).
Octo reserved the `pty-terminal` flag for this in `docs/architecture.md` §6.

Done means:

- Behind `pty-terminal`, the task screen opens a shell in the task's worktree, on the task's machine
  (through ssh for remote tasks), in a panel the user can show and hide.
- Scrollback is bounded (lines and bytes) and kept while the app runs; the shell is killed with its
  process tree when the task is removed or the app quits.
- A PTY and a terminal-emulator view need third-party libraries: pick the best maintained ones,
  wrap them behind Octo's own interfaces and get the creator's approval before adding them.
- Core code (session lifetime, scrollback limits) has tests with a fake PTY.
