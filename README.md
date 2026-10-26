# Octo

An open, extensible desktop orchestrator for CLI coding agents. One window to run, watch and
coordinate several agents in parallel, each in its own git worktree, using your own agent
subscriptions.

Status: early development. See `docs/research.md`, `docs/architecture.md` and `plan/`.

## Build

Needs JDK 21 and a checkout of the shared `ci-diffuser` build next to this one
(`../ci-diffuser`). It supplies ktlint, detekt, repositories and the optional remote build
cache. Set `CI_DIFFUSER_DIR` when it lives elsewhere, for example from a worktree outside `apps/`.
The plugin host also needs a checkout of [kotlin-wasm](https://github.com/pyrlyn/kotlin-wasm) at
`../../packages/kotlin-wasm`, or `KOTLIN_WASM_DIR`.

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

On macOS and Linux, all of Octo's calls to one machine share a single SSH connection, so only
the first one pays for the handshake. Octo starts that connection itself and closes it when it
quits. Its socket lives in a folder only you can read: `$TMPDIR/octo` on macOS,
`$XDG_RUNTIME_DIR/octo` on Linux. A machine `id` that would make the socket path too long gets
a separate connection per call, with a warning. Windows always uses a connection per call.

`"agent-messaging": true` lets agents talk to each other. Octo runs an MCP server on
`127.0.0.1` and gives each Claude, Cursor, Grok and Antigravity session three tools:
`list_tasks`, `send_message` and `wait_for_idle`. A message waits until its target is idle and
then arrives as a prompt, so a busy agent is never interrupted. Each session gets its own token,
passed through the environment, never on the command line. Tasks on remote machines and Codex
get no tools yet.

`"presets": true` adds team presets. A preset is a named set of members, each with a role, an
agent and a first prompt. Create, edit and delete presets from the new-task form, under Team
preset → Edit presets…, and start every member on one repository at once. In a team of several
agents, each one is told its role and who its teammates are. Presets are kept in `presets.json`
in the Octo home. If that file cannot be read, Octo shows a warning and never overwrites it.

`"remote-access": true` serves a JSON API for a phone or another computer at
`http://127.0.0.1:47470/api/v1`; `"remoteAccess": { "port": 47470 }` changes the port. It listens
on this computer only. Reach it through your own tunnel, such as
`ssh -L 47470:127.0.0.1:47470 <this computer>` or `tailscale serve 47470`, which also encrypts
the traffic. Pair a device from Devices in the sidebar: Octo shows a one-time code that works for
five minutes and five tries. `POST /api/v1/pair` with `{"code", "name"}` returns the device's
token; every other call sends it as `Authorization: Bearer <token>`. The calls:

- `GET /tasks`, `POST /tasks` with `{repo, baseRef, agent, title, prompt?, options?, machine?}`;
- `GET /tasks/{id}/transcript`;
- `POST /tasks/{id}/prompt` with `{text}`, and `POST /tasks/{id}/respond` with `{requestId}` plus
  one of `optionId`, `answers` or `cancelled: true`;
- `POST /tasks/{id}/cancel`, `/stop` and `/restart`.

Vendor terms are accepted only in the Octo window; until then creating a task answers 409.
`devices.json` keeps only a SHA-256 of each token, and Revoke locks a device out at once.

`"plugins": true` loads WebAssembly plugins from the `plugins` folder in the Octo home. A plugin
can add ACP agents and actions in a task's Plugins menu. A plugin cannot reach files, processes or
the network. A broken one is skipped with a warning. See `docs/plugins.md` for the ABI.

`./gradlew ktlintFormat` fixes formatting. CI also builds every Kotlin/Native target with
`-Pocto.allNativeTargets=true`; locally only the host's native target is built.
