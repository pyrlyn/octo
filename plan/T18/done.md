# T18. Done

With the `remote-ssh` flag on, every call to one remote machine now shares one SSH connection
(OpenSSH connection sharing).

- `SshProcessLauncher` takes an optional `SharedConnection(controlPath, scope, retry)`.
  - Before a call, it starts an Octo-owned master if none is running:
    `ssh -n -N … -o ControlMaster=yes -o ControlPersist=no -o ControlPath=<p>`.
  - A coroutine in the scope watches the master. Once it exits, the next call starts a new one,
    at most once per retry interval (30 s), so an unreachable host is not given a master on
    every call.
  - Clients pass `ControlMaster=no` and the same `ControlPath`. If there is no socket, ssh
    connects directly, so sharing fails open.
  - A stale socket is removed before a master starts.
  - `close()` ends the master; ssh removes its socket on SIGTERM.
  - `isValidControlPath` accepts only absolute `[A-Za-z0-9._/-]` paths that leave room for
    ssh's temporary socket name: at most 86 characters, under the 104-byte macOS limit.
- `OctoPaths.sockets`: `$TMPDIR/octo` on macOS, `$XDG_RUNTIME_DIR/octo` on Linux, none on
  Windows. It is never the Octo home, whose macOS path contains a space.
- App: `socketDir` creates the folder with mode 0700. It tightens a looser mode on a folder the
  user owns, and refuses a symlink, a file or another user's folder with a warning.
  `remoteMachines` builds each machine's socket path from its id; when ssh cannot use that path,
  it warns and turns sharing off for that machine. `Octo.close` ends the masters after the
  agents stop.
- Docs: README (remote machines), `docs/architecture.md` (flag table). The idea was removed from
  `ideas.md`.

Checked:
- `./gradlew ktlintFormat` and `./gradlew check -Pocto.allNativeTargets=true` pass.
- `SshProcessLauncherTest` was run three more times; all runs passed.
- New tests:
  - `SshProcessLauncherTest` uses a fake `ssh`. It checks the master's and the clients' options
    and that one master serves two calls. It checks that the stale socket is removed and that
    `close` ends the master process, after which calls connect directly. It checks that a master
    that exits is restarted, but not within the retry interval, and it checks the path rules.
  - `OctoPathsTest` covers the socket folder on each OS.
  - `RemoteMachinesTest` covers the private folder and mode, an untrusted folder, a socket path
    that is too long, and sharing off without a folder.
