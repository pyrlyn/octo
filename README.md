# Octo

An open, extensible desktop orchestrator for CLI coding agents. One window to run, watch and
coordinate several agents in parallel, each in its own git worktree, using your own agent
subscriptions.

Status: early development. See `docs/research.md`, `docs/architecture.md` and `plan/`.

Agents: Claude Code, Codex, Cursor, Grok Build, Kimi Code, MiMo Code, ZCode and, behind a flag,
Antigravity, each the CLI you installed and signed in to yourself. Plugins can add more.

## Build

Needs JDK 25 (JDK 21 too, for kotlin-wasm) and two shared builds in the workspace's `packages/`
(`../../packages/` from the main checkout): `shared-settings` supplies ktlint, detekt, repositories and the optional remote
build cache; [kotlin-wasm](https://github.com/pyrlyn/packages/tree/main/kotlin-wasm) is the plugin host's
WebAssembly interface. Every directory above the build is searched for `packages/`, so a nested
worktree finds them too; set `SHARED_SETTINGS_DIR` or `KOTLIN_WASM_DIR` when they live elsewhere.

```bash
./gradlew check        # build, tests, ktlint, detekt
./gradlew :app:run     # open the desktop app
./gradlew :app:hotRun  # open it with Compose Hot Reload: changed UI code reloads into the window
```

Gradle runs on JDK 25 and downloads it on first run when it is missing
(`gradle/gradle-daemon-jvm.properties`). `hotRun` needs a JetBrains Runtime 25 and downloads one
into `~/.gradle/chr` the first time. In IntelliJ IDEA, set the project SDK and the Gradle JVM to a
JDK 25; a project last opened in Android Studio points at its bundled `jbr-25`, which IntelliJ does
not have.

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
chosen mode runs Supervised and says so in the transcript. Claude Code, Codex and ZCode have all four;
the ACP agents run Supervised. ZCode runs each turn headless, so an action that would need your
answer fails inside the turn instead of asking. New tasks take `defaultPermissionMode` from `config.json`:

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
`127.0.0.1` and gives each Claude, Cursor, Grok, Kimi Code, MiMo Code and Antigravity session four
tools: `list_tasks`, `send_message`, `wait_for_idle` and `create_task`. A message waits until its
target is idle and then arrives as a prompt, so a busy agent is never interrupted. Each session
gets its own token, passed through the environment, never on the command line. Tasks on remote
machines, Codex and ZCode get no tools yet.

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

- `GET /tasks`, `POST /tasks` with `{repo, baseRef, agent, title, prompt?, options?, machine?, permissionMode?, attachments?}`;
- `GET /tasks/{id}/transcript`;
- `POST /tasks/{id}/prompt` with `{text, attachments?}`, and `POST /tasks/{id}/respond` with `{requestId}` plus
  one of `optionId`, `answers` or `cancelled: true`;

`attachments` is a list of `{name, mimeType?, data}` with each file's bytes in base64, at most 20 MB
a file; a message may be files alone.
- `POST /tasks/{id}/cancel`, `/stop` and `/restart`.

Vendor terms are accepted only in the Octo window; until then creating a task answers 409.
`devices.json` keeps only a SHA-256 of each token, and Revoke locks a device out at once.

`"guest-share": true` (with `remote-access`) adds Share to a task: it makes a link a guest opens
in a browser to watch the task and propose messages. A proposal reaches the agent only when you
press Send under it. The link is shown once; a new link or Revoke ends the old one, and only a hash
of its token is kept in `shares.json`. Set `"remoteAccess": { "publicUrl": "https://box.tailnet.ts.net" }`
to your tunnel's address so links point there and its pages may call the API; without it links
point at `127.0.0.1`, for a guest on `ssh -L`. The token sits after `#`, so the browser never
sends it in the address and no log along the tunnel holds it.

The `octo` command drives the same API from a terminal or a script. Build it with
`./gradlew :cli:installDist`; it lands in `cli/build/install/octo/bin/octo`. Turn on
`remote-access`, then run `octo pair <code>` with a code from Devices. The URL and token go to
`cli.json` in the Octo home, readable only by you; `OCTO_URL` and `OCTO_TOKEN` override them, and
the token is sent only to the Octo it was paired with.

- `octo task list`;
- `octo task start --base main --agent claude --title "Fix the build" [--repo .] [--prompt …] [--mode auto] [--machine …] [--option key=value] [--attach file]`, which prints the new id;
- `octo task send <id> [message] [--attach file]`; repeat `--attach` for more files, read from
  your current directory;
- `octo task read <id>`, which prints the transcript one entry per line;
- `octo task wait <id> [--timeout seconds]`, which returns when the turn ends: exit 0 when the task
  is idle, 1 when it failed or stopped, 2 when it waits for an answer, 124 on timeout.

`--json` on `list`, `start`, `read` and `wait` prints Octo's JSON instead of text.

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
offers Resume, which sends "Continue"; Octo never resumes on its own. The task screen also gets a
Context button: how full the agent's context window is, and how its latest request splits into
cache read, cache write, input and output, as far as the agent reports it.

`"checkpoints": true` records each task's worktree before its first prompt and after every turn,
on hidden git refs under `refs/octo/`, without touching the index, the branch or the stash. A
checkpoint in the transcript offers "Revert to here", which brings the files back to that point
(after a confirmation) and drops the later checkpoints. Codex also drops the reverted turns from
its conversation; other agents are told what was reverted with their next prompt. The refs are
deleted with the task.

`"triggers": true` starts the tasks listed under `triggers` in `config.json` on their own. A
trigger has an `id`, a `task` (`repo`, `baseRef`, `agent`, `title`, and optionally `prompt`,
`options`, `machine`, `permissionMode`) and exactly one of:

- `schedule`: standard five-field cron (`minute hour day month weekday`, numbers only) in the
  computer's time zone, such as `"0 9 * * 1-5"`. A run missed while Octo was closed or the
  computer slept happens once when Octo is back; a new trigger never runs for the past.
- `webhook`: `{"secret": "...", "events": [...], "filters": {...}}`, called as
  `POST /api/v1/hooks/<id>` on the `remote-access` API, so it also needs that flag and the user's
  tunnel. The caller signs the body with the secret (16 characters or more) as GitHub does
  (`X-Hub-Signature-256`) or sends `Authorization: Bearer <secret>`. `events` limits the
  `X-GitHub-Event` values; `filters` maps a dotted path in the JSON body, such as
  `"pull_request.base.ref"`, to the value it must have. A repeated delivery starts nothing.

```json
"triggers": [
  {"id": "nightly", "schedule": "0 2 * * *",
   "task": {"repo": "/src/app", "baseRef": "main", "agent": "claude", "title": "Nightly audit", "prompt": "Audit the dependencies"}},
  {"id": "review", "webhook": {"secret": "<long random string>", "events": ["pull_request"]},
   "task": {"repo": "/src/app", "baseRef": "main", "agent": "codex", "title": "Review the new PR"}}
]
```

`triggers.json` in the Octo home keeps each trigger's last run and recent delivery ids. A broken
trigger, or a task one cannot start, shows as a warning.

`"queue": true` caps the agents running at once on each machine. `maxRunningAgents` in
`config.json` sets the cap for this computer, and the same key on a `machines` entry sets it for
that machine; a machine without it has no cap. A task started over the cap reads "Queued" and
waits its turn, taking prompts meanwhile; tasks start in the order they were queued as agents stop
or exit. "Start now" on a queued task starts it at once, over the cap.

```json
{ "flags": { "queue": true }, "maxRunningAgents": 3 }
```

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

`"compact-idle": true` compacts a large idle Claude conversation two minutes before its prompt
cache expires; each request tells Octo whether the cache
lasts five minutes or an hour. The summary reads the context while it is still cached,
so the next prompt after a long pause does not pay to cache the whole conversation again. A
prompt or a stop cancels it, a snoozed or settled task is left alone, and each turn is compacted
at most once. The transcript says when it happens. `compactIdleMinTokens` sets how many tokens
the context must hold, 200K when it is left out; with a 200K context window Claude compacts on its
own before it gets there, so lower it for such models. 0 compacts every idle conversation.

```json
{ "flags": { "compact-idle": true }, "compactIdleMinTokens": 120000 }
```

`"previews": true` previews a changed file in the diff view instead of its diff: images and SVG,
Jupyter notebooks, zip-family archives (zip, jar, whl and the like), SQLite databases and Mermaid
diagrams. A Diff button switches back. SQLite needs `sqlite3` on the PATH; Mermaid needs
mermaid-cli (`mmdc`) and shows the source without it. Only files of 20 MB or less inside the
task's worktree are read, and only for tasks on this computer.

`"notifications": true` shows an OS notification when a task needs you or is done while you
look elsewhere: the agent waits for an approval or an answer, fails, or finishes its turn, and the
window is not focused or another task is open. A burst of events for one task shows one
notification, and one that is already answered or seen is skipped. A task notifies at most once in
20 s; a later event waits until then. Clicking it opens the task.
`notifications` in `config.json` turns single events off; all three are on when it is left out.
Octo shows a tray icon (on macOS, in the menu bar) while the flag is on, since that is how a
desktop Java app posts notifications.

```json
{ "flags": { "notifications": true }, "notifications": { "done": false } }
```

`"plugins": true` loads WebAssembly plugins from the `plugins` folder in the Octo home. A plugin
can add ACP agents and actions in a task's Plugins menu. A plugin cannot reach files, processes or
the network. A broken one is skipped with a warning. See `docs/plugins.md` for the ABI.

`./gradlew ktlintFormat` fixes formatting. CI also builds every Kotlin/Native target with
`-Pocto.allNativeTargets=true`; locally only the host's native target is built.

The CI workflows are written in Kotlin with
[github-workflows-kt](https://github.com/typesafegithub/github-workflows-kt): each
`.github/workflows/<name>.main.kts` generates the YAML next to it, which GitHub runs. Edit the
script, never the YAML, then regenerate and commit both; CI fails when they differ. It needs the
Kotlin command-line compiler (`kotlin`) and a JDK:

```bash
.github/workflows/ci.main.kts
```
