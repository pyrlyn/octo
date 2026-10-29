# T59. Done

PR: https://github.com/pyrlyn/octo/pull/59

## What was done

- **stdin lines end with LF.** `JvmRunningProcess.writeLine` wrote the platform separator, so on
  Windows `git update-ref --stdin` saw a CR after each ref and checkpoint refs were never deleted.
- **`GitWorkspace.repoRoot` is native on Windows.** git prints `C:/…`; a drive path now gets
  backslashes. POSIX paths of remote machines are left as they are.
- **Tests are host-aware.** Expected paths in `MoveTaskTest` and the `cli` `OctoTest` are built the
  way the code builds them; the token file mode is checked only on POSIX file systems.
  `SshProcessLauncherTest` no longer needs `/tmp`, so its pure checks run on Windows. Tests that
  need a POSIX shell (stand-in `ssh` and `gh`, `runSetup`), POSIX socket folders or symlinks skip
  only that part on Windows.

## How it was checked

- macOS: `./gradlew check` passes.
- Windows 11 VM on Parallels (ARM64, x64 Temurin 21 so Kotlin/Native builds `mingwX64`):
  `./gradlew check --continue` ran every test green; its one ktlint error was fixed and
  `./gradlew :ui:check` then passed.

## Left out

Worktree setup commands (T25) still run `/bin/sh -c`, so they fail on a Windows machine; the shell
has to be picked by the machine's OS.
