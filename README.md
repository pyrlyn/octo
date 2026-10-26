# Octo

An open, extensible desktop orchestrator for CLI coding agents. One window to run, watch and
coordinate several agents in parallel, each in its own git worktree, using your own agent
subscriptions.

Status: early development. See `docs/research.md`, `docs/architecture.md` and `plan/`.

## Build

Needs JDK 21.

```bash
./gradlew check      # build, tests, ktlint, detekt
./gradlew :app:run   # open the desktop app
```

To try Octo without installing an agent, turn on the built-in demo agent in `config.json` in
the Octo home (`OCTO_HOME`, or the platform's app data folder):

```json
{ "flags": { "demo-agent": true } }
```

`"git-publish": true` adds a Publish action to each task: it commits the worktree, pushes the
branch and opens a pull request with your own `git` and `gh` login.

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

`./gradlew ktlintFormat` fixes formatting. CI also builds every Kotlin/Native target with
`-Pocto.allNativeTargets=true`; locally only the host's native target is built.
