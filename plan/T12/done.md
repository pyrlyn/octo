# T12. Done

## What was done

- `core`
  - `remote/SshProcessLauncher`: a `ProcessLauncher` that runs every process through the user's
    own `ssh`. The call is `ssh -T -o BatchMode=yes [-p port] -- host '<command>'`, so keys, the
    agent and `~/.ssh/config` stay the user's and Octo never prompts for or handles a password.
    - The remote command is `cd 'dir' && exec env 'K=V' 'cmd' 'arg'…` with POSIX single quotes.
    - Host, port and env names are validated. A host cannot start with `-`, so it can never
      become an ssh option.
    - `which` runs `command -v` remotely. ssh's own failure (exit 255) is reported as
      unreachable, not as "not installed".
  - `git/Directories`: `GitWorkspace` gets its directory checks through an interface.
    - `LocalDirectories` is the previous behaviour.
    - `ShellDirectories` runs `mkdir -p` and `test -d` through any launcher.
  - `orchestrator/Machine`:
    - `LocalMachine` keeps the old worktree path.
    - `RemoteMachine` has its own agents and workspace. Its paths always use `/`, and its root
      defaults to `$HOME/.octo/worktrees` there, looked up on first use.
    - `Orchestrator.open` takes `machines`, and every task call goes to the task's machine.
    - An unknown machine throws `UnknownMachineException`. A task whose machine is gone can still
      be removed while its worktree is kept.
  - `Task.machine` and `NewTask.machine`: null means this computer. Old state files still load.
  - The `remote-ssh` flag and `machines` in `config.json` (`MachineConfig.problem()` validates
    each entry).
  - `AgentCatalog`:
    - lists `machines`;
    - `agents(machine)` probes on that machine;
    - terms are looked up in any registry that has the agent.
  - Publishing refuses tasks on remote machines, since this computer's git and gh cannot reach
    them.
- `app`
  - With the flag on, each valid machine entry gets an SSH launcher, a git workspace over it and
    the remote adapter set: every built-in agent except the demo, which writes to the local disk.
    Antigravity runs with its Linux options.
  - A bad or duplicate entry is warned about and skipped.
  - With the flag off, nothing is read or checked.
- `ui`
  - The new-task form shows a Machine choice only when machines exist. Changing it reloads the
    agents, and a late probe of a machine the user already left is dropped.
  - The folder picker is hidden for a remote machine.
  - The task header shows "on <machine>" and hides Publish for remote tasks.
- README: how to configure machines. Architecture flag table updated.

## How it was checked

- `./gradlew check -Pocto.allNativeTargets=true`: all green.
- New tests:
  - `SshProcessLauncherTest` (JVM, 4) uses a fake `ssh` script first on a temp `PATH`. It
    drops the options and host and runs the command with a local `sh`, as sshd does. HOME and
    git config are sealed off. It checks:
    - quotes, spaces, `$`, backticks, `;`, an empty argument, a leading `-`, a quoted env value
      and a working directory with a quote all arrive verbatim;
    - the exact ssh options are passed;
    - `which` finds and misses commands remotely, and exit 255 throws `SshException`;
    - a real git repo works end to end through the launcher: root, worktree, dirty check, diff,
      and a missing worktree;
    - a host that looks like an option, a bad port and a bad env name are refused.
  - `MachineConfigTest` (common, 3): parsing, an old config without machines, validation, and
    quoting.
  - `OrchestratorTest`: a task on a remote machine uses that machine's adapter and workspace,
    the worktree goes under the remote root with `/`, and removal goes to the remote workspace.
    An unknown machine is refused.
  - `OctoTest`: machines appear only with the flag on, and a bad entry gives one warning.
  - `ViewModelTest`: choosing a machine reloads its agents, and the task is created on it.

## Not verified

- A real SSH server, and the remote agents' CLIs over a real connection.
- Stopping a task kills the local `ssh`. Without a tty the remote agent gets EOF on stdin and a
  closed stdout rather than a signal. ACP agents exit on that, but a CLI that ignores it may
  keep running remotely.
- A non-interactive remote shell may have a shorter `PATH` than a login terminal. An agent
  installed only on the interactive `PATH` then shows as not installed.
- Each process opens its own SSH connection; multiplexing (`ControlMaster`) is in `ideas.md`.
- The window, by eye (no visual check this time either).
- CI on GitHub: blocked by the account's billing, as for the previous tasks.
