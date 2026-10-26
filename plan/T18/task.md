---
status: in progress
priority: P3
complexity: 3
readiness: 100%
agent: Claude Code / claude-opus-5-5
---

# T18. Share one SSH connection per remote machine

Every git call and agent process on a remote machine opens its own SSH connection, so each one
pays the full handshake and authentication. Octo should open one master connection per machine
and run everything else over it (OpenSSH connection sharing), and fall back to plain connections
whenever sharing cannot work.

Done: with the `remote-ssh` flag on, the first process on a machine starts an Octo-owned master
connection, later processes reuse it, a master that exits is started again on demand, and closing
Octo ends the masters. The control socket lives in a short private per-user directory, never in
the Octo home: the macOS app data folder has a space in its path and socket paths are limited to
about 104 bytes. Hosts without such a directory (Windows, Linux without `XDG_RUNTIME_DIR`) and
paths ssh would misread run without sharing, with a warning for the latter.

Check: `./gradlew check -Pocto.allNativeTargets=true` passes, with tests for the master's and
clients' options, one master per machine, restart after exit, shutdown on close, path checks and
the socket directory.

## Execution plan

1. Core `OctoPaths`: an optional `sockets` directory, `$TMPDIR/octo` on macOS (private per user)
   and `$XDG_RUNTIME_DIR/octo` on Linux; none on Windows, whose OpenSSH has no connection sharing.
2. Core `SshProcessLauncher`: an optional `SharedConnection(controlPath, scope)`.
   - Before the first launch, start `ssh -N -n -o ControlMaster=yes -o ControlPersist=no` through
     the local launcher and watch it in the scope; start it again after it exits, at most once per
     retry interval so an unreachable host does not spawn a master per call.
   - Clients pass `ControlMaster=no` and the same `ControlPath`; with no socket yet, ssh connects
     directly, so sharing fails open.
   - `close()` ends the master; a stale socket left by a crash is removed before a master starts.
   - `isValidControlPath`: absolute, `[A-Za-z0-9._/-]` only (no space, `%` or `~` for ssh to
     expand), and short enough for ssh's temporary socket name.
3. App: create the socket directory with mode 0700 and refuse one owned by someone else; build
   each machine's control path from its id, warn and skip sharing when it is invalid; close the
   masters in `Octo.close` after the agents stop.
4. Tests in `SshProcessLauncherTest` (fake `ssh`), `OctoPathsTest` and the app tests.
5. Docs: README remote machines paragraph, `docs/architecture.md`; done.md, PR, merge.
