# Remote machines

## Running tasks over SSH

`"remote-ssh": true` lets a task run on another machine. Octo runs your own `ssh` client
there, so your keys, agent and `~/.ssh/config` apply. It never asks for a password
(`BatchMode=yes`). The remote machine needs a POSIX login shell, `git` and the agent's CLI:

```json
{
  "flags": { "remote-ssh": true },
  "machines": [{ "id": "box", "host": "me@box.local", "port": 22, "worktrees": "/home/me/octo" }]
}
```

`port` and `worktrees` are optional. Worktrees go under `$HOME/.octo/worktrees` on the machine
by default. Publish works only for tasks on this computer for now.

On macOS and Linux, all of Octo's calls to one machine share a single SSH connection, so only
the first one pays for the handshake. Octo starts that connection itself and closes it when it
quits. Its socket lives in a folder only you can read: `$TMPDIR/octo` on macOS,
`$XDG_RUNTIME_DIR/octo` on Linux. A machine `id` that would make the socket path too long gets
a separate connection per call, with a warning. Windows always uses a connection per call.

## Moving a task

`"move-task": true` (with `remote-ssh`) adds "Move to…" to a task, which moves it to another
machine or back to this computer. The branch and its uncommitted and untracked changes go to a
new worktree there; the old worktree is deleted, and its branch stays in the old clone. Git
carries the code with the same `ssh`, straight from one clone to the other, or through
`relay.git` in the Octo home between two remote machines. Each machine needs its own clone of
the repository, listed under `repos` on its `machines` entry as this computer's path to the
path there. A fresh session starts on the new machine and your next prompt carries the
conversation over; checkpoints stay behind. A move that fails leaves the task where it was.

```json
{
  "flags": { "remote-ssh": true, "move-task": true },
  "machines": [{ "id": "box", "host": "me@box.local", "repos": { "/Users/me/src/app": "/home/me/src/app" } }]
}
```
