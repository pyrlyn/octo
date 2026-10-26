---
status: in progress
priority: P3
complexity: 5
readiness: 100%
agent: Claude Code / claude-opus-5-5
---

# T12. Remote machines over SSH

Optional, behind a flag: run tasks on a remote machine over SSH.

Check: Tests with a fake SSH transport; the flag hides the feature when off.

## Execution plan

1. Core: `SshProcessLauncher` runs every process through the user's own `ssh` client
   (`BatchMode=yes`, so Octo never sees a password; keys and the agent come from the user's setup).
   The remote command is POSIX single-quoted; hosts, ports and env names are validated so config
   values cannot become ssh options.
2. Core: a `Machine` (agents, workspace, worktree root) per task; `Task.machine` is null for this
   computer and persisted with a default, so old state files load. `GitWorkspace` gets its
   directories through an interface, so a remote machine uses `mkdir -p` and `test -d` over SSH.
3. Core: the `remote-ssh` flag and `machines` in `config.json`; the catalog lists machines and probes
   agents on the chosen one. Publishing refuses remote tasks for now.
4. App: build remote machines only with the flag on; an invalid entry is warned about and skipped.
5. UI: a machine choice in the new-task form, shown only when machines exist; the task header names
   the machine; Publish is hidden for remote tasks.
6. Tests: quoting and validation; a fake `ssh` script that runs the command locally drives the
   launcher and a real git workspace; orchestrator routing; the app's flag gate; the form.
7. Docs: README and architecture; full check; done.md; PR.
