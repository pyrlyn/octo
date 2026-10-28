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

Agents in the sidebar lists every running agent: its icon, whether it is signed in (never the
login itself), the CPU and memory of its own processes, its model, how much of its context
window is used and its session's token totals. Each row has a stop button.

You can write to an agent while it works. Codex takes the message into the running turn. Other
agents get it once they are idle: it waits under the transcript as queued, where you can edit or
remove it, and the queue is kept across restarts.

To try Octo without installing an agent, turn on the built-in demo agent in `config.json` in
the Octo home (`OCTO_HOME`, or the platform's app data folder):

```json
{ "flags": { "demo-agent": true } }
```

Each task runs in one of four permission modes: Supervised (the agent asks before commands and
edits), Auto-accept edits, Auto (the agent's own review approves routine actions) and Full
access. Pick one in the new-task dialog or change it on a running task. An agent without the
chosen mode runs Supervised and says so in the transcript. Claude Code and Codex have all four;
the ACP agents run Supervised. New tasks take `defaultPermissionMode` from `config.json`:

```json
{ "defaultPermissionMode": "AutoAcceptEdits" }
```

A fresh worktree has no installed dependencies or `.env` files. `worktreeSetup` in `config.json`
names a command per repository (its top-level path) that Octo runs with `/bin/sh` in each new
worktree, forks included, before the agent starts:

```json
{ "worktreeSetup": { "/Users/me/src/app": "npm ci && cp ../app/.env ." } }
```

Its output shows in the task. If it fails or runs for more than 30 minutes, the task is marked
failed and the agent does not start. On a remote machine the command runs there, and the path is
the repository's path on that machine.

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
`127.0.0.1` and gives each Claude, Cursor, Grok, Kimi Code and Antigravity session three tools:
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

- `GET /tasks`, `POST /tasks` with `{repo, baseRef, agent, title, prompt?, options?, machine?, permissionMode?}`;
- `GET /tasks/{id}/transcript`;
- `POST /tasks/{id}/prompt` with `{text}`, and `POST /tasks/{id}/respond` with `{requestId}` plus
  one of `optionId`, `answers` or `cancelled: true`;
- `POST /tasks/{id}/cancel`, `/stop` and `/restart`.

Vendor terms are accepted only in the Octo window; until then creating a task answers 409.
`devices.json` keeps only a SHA-256 of each token, and Revoke locks a device out at once.

`"handoff": true` adds Switch agent to each task: pick another agent or model for the same
worktree and branch. A model change stays in the running session when the agent can switch live;
otherwise a fresh session starts, and your next prompt carries the conversation over from Octo's
transcript (the original request and as many recent messages and command outcomes as fit).

`"fork": true` adds Fork to each task: a new task on a new branch that starts from the task's
worktree as it is, uncommitted files included, listed under it in the sidebar. Claude and Codex
fork their own session; other agents get the conversation with your next prompt.

`"compare": true` adds Compare agents to the new-task dialog: one prompt goes to several agents,
or several models of one agent (comma-separated), each in its own task on its own branch. The
tasks sit together in the sidebar under the prompt's title, so their diffs are easy to compare.

`"sections": true` sorts each repository's tasks into Pinned, Active, Snoozed and Settled. The
menu in a task's header pins, snoozes (for an hour, a day, or until the agent's turn ends) or
settles it. A snoozed task comes back early when its agent blocks or fails. With `git-publish` on
as well, a task whose branch has a merged pull request is settled on its own; Octo asks `gh`
every five minutes.

`"palette": true` binds Cmd+K (Ctrl+K off macOS) to a command palette. It finds tasks by title
and by the text of their messages, and runs actions: new task, diff, publish, switch agent, fork,
stop, and the agents and devices screens. An action whose flag is off is not listed. Arrow keys
move, Enter runs, Escape closes.

`"usage": true` adds a Usage screen with token use per agent and model for today, 7 days, 30
days or all time. Claude Code and Codex are counted from their own local session logs
(`$CLAUDE_CONFIG_DIR` or `~/.claude`, `$CODEX_HOME` or `~/.codex`), so work outside Octo counts
too; other agents are counted from what Octo saw in its own runs. Octo reads only token counts,
never credentials. When an agent hits its usage limit the task says "Limited until HH:MM" and
offers Resume, which sends "Continue"; Octo never resumes on its own.

`"checkpoints": true` records each task's worktree before its first prompt and after every turn,
on hidden git refs under `refs/octo/`, without touching the index, the branch or the stash. A
checkpoint in the transcript offers "Revert to here", which brings the files back to that point
(after a confirmation) and drops the later checkpoints. Codex also drops the reverted turns from
its conversation; other agents are told what was reverted with their next prompt. The refs are
deleted with the task.

`"plugins": true` loads WebAssembly plugins from the `plugins` folder in the Octo home. A plugin
can add ACP agents and actions in a task's Plugins menu. A plugin cannot reach files, processes or
the network. A broken one is skipped with a warning. See `docs/plugins.md` for the ABI.

`./gradlew ktlintFormat` fixes formatting. CI also builds every Kotlin/Native target with
`-Pocto.allNativeTargets=true`; locally only the host's native target is built.
