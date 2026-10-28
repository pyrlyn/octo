---
status: in progress
priority: P1
complexity: 2
readiness: 100%
agent: Claude Code / claude-opus-5-5
---

# T59. Green `./gradlew check` on Windows

CI runs on Linux only, so nothing has checked Octo on Windows. A run of `./gradlew check` on a
Windows 11 VM (x64 JDK 21) failed 19 JVM tests in `core`, `app`, `cli` and `ui`; ktlint, detekt
and the `mingwX64` build and tests passed. Some failures are Octo bugs on Windows, the rest are
tests that assume a POSIX host.

Done: `./gradlew check` passes on Windows and on macOS. Real bugs are fixed in the code; a test
that needs a POSIX shell, POSIX permissions or symlinks skips only that part on Windows, the way
`JvmProcessLauncherTest` already does.

## Plan

1. Bugs:
   - `JvmRunningProcess.writeLine` ends lines with the platform separator, so on Windows every
     stdin line carries a CR and `git update-ref --stdin` rejects ref deletions (checkpoints).
     Always write LF.
   - `GitWorkspace.repoRoot` returns git's `C:/…` form on Windows while every other path is
     native. Turn a drive path into backslashes; POSIX paths of remote machines stay as they are.
2. Tests built on POSIX assumptions:
   - `MoveTaskTest`, `cli` `OctoTest`: build the expected path the way the code does.
   - `cli` `OctoTest`: check the token file mode only where the file system has POSIX modes.
   - `SshProcessLauncherTest`: the socket folder falls back to the temp dir without `/tmp`; the
     tests that run the stand-in `sh` script skip on Windows (no connection sharing there, T18).
   - `RemoteMachinesTest` socket folder tests, `GitWorkspaceTest.setupRunsInTheWorktreeWithAShell`
     (`runSetup` is `/bin/sh` by T25), `TaskPublisherTest` stand-in `gh` script: skip on Windows.
   - `FilePreviewerTest`: the symlink case runs only where links can be made.
3. Verify: `./gradlew check` on macOS and on the Windows 11 VM.

Out of scope: worktree setup commands on a Windows machine (`runSetup` needs a shell chosen by the
machine's OS) — reported to the creator.
