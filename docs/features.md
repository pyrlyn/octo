# Optional features

Each feature here is off until its flag is set under `flags` in `config.json`, for example `{ "flags": { "fork": true } }`. Remote machines and remote access have their own pages: [remote-machines.md](remote-machines.md), [remote-access.md](remote-access.md).

## Publishing pull requests

`"git-publish": true` adds a Publish action to each task: it commits the worktree, pushes the
branch and opens a pull request with your own `git` and `gh` login.

## Agent messaging

`"agent-messaging": true` lets agents talk to each other. Octo runs an MCP server on
`127.0.0.1` and gives each Claude, Cursor, Grok, Kimi Code, MiMo Code and Antigravity session four
tools: `list_tasks`, `send_message`, `wait_for_idle` and `create_task`. A message waits until its
target is idle and then arrives as a prompt, so a busy agent is never interrupted. Each session
gets its own token, passed through the environment, never on the command line. Tasks on remote
machines, Codex and ZCode get no tools yet.

## Team presets

`"presets": true` adds team presets. A preset is a named set of members, each with a role, an
agent and a first prompt. Create, edit and delete presets from the new-task form, under Team
preset → Edit presets…, and start every member on one repository at once. In a team of several
agents, each one is told its role and who its teammates are. Presets are kept in `octo.db`
in the Octo home. If they cannot be read, Octo shows a warning and never overwrites them.

## Switching agents

`"handoff": true` adds Switch agent to each task: pick another agent or model for the same
worktree and branch. A model change stays in the running session when the agent can switch live;
otherwise a fresh session starts, and your next prompt carries the conversation over from Octo's
transcript (the original request and as many recent messages and command outcomes as fit).
With "Ask for a summary first", on by default while the agent is idle, the agent you leave first
writes a short summary for the next one; if it is busy, asks for permission or takes over two
minutes, the handoff goes on with the transcript alone.

## Forking a task

`"fork": true` adds Fork to each task: a new task on a new branch that starts from the task's
worktree as it is, uncommitted files included, listed under it in the sidebar. Claude and Codex
fork their own session; other agents get the conversation with your next prompt.

## Comparing agents

`"compare": true` adds Compare agents to the new-task dialog: one prompt goes to several agents,
or several models of one agent (comma-separated), each in its own task on its own branch. The
tasks sit together in the sidebar under the prompt's title, so their diffs are easy to compare.

## Sidebar sections

`"sections": true` sorts each repository's tasks into Pinned, Active, Snoozed and Settled. The
menu in a task's header pins, snoozes (for an hour, a day, or until the agent's turn ends) or
settles it. A snoozed task comes back early when its agent blocks or fails. With `git-publish` on
as well, a task whose branch has a merged pull request is settled on its own; Octo asks `gh`
every five minutes.

## Command palette

`"palette": true` binds Cmd+K (Ctrl+K off macOS) to a command palette. It finds tasks by title
and by the text of their messages, and runs actions: new task, diff, publish, switch agent, fork,
stop, and the agents and devices screens. An action whose flag is off is not listed. Arrow keys
move, Enter runs, Escape closes.

## Usage and context

`"usage": true` adds a Usage screen with token use per agent and model for today, 7 days, 30
days or all time. Claude Code and Codex are counted from their own local session logs
(`$CLAUDE_CONFIG_DIR` or `~/.claude`, `$CODEX_HOME` or `~/.codex`), so work outside Octo counts
too; other agents are counted from what Octo saw in its own runs. Octo reads only token counts,
never credentials. When an agent hits its usage limit the task says "Limited until HH:MM" and
offers Resume, which sends "Continue"; Octo never resumes on its own. The task screen also gets a
Context button: how full the agent's context window is, and how its latest request splits into
cache read, cache write, input and output, as far as the agent reports it.

## Checkpoints

`"checkpoints": true` records each task's worktree before its first prompt and after every turn,
on hidden git refs under `refs/octo/`, without touching the index, the branch or the stash. A
checkpoint in the transcript offers "Revert to here", which brings the files back to that point
(after a confirmation) and drops the later checkpoints. Codex also drops the reverted turns from
its conversation; other agents are told what was reverted with their next prompt. The refs are
deleted with the task.

## Triggers

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

`octo.db` in the Octo home keeps each trigger's last run and recent delivery ids. A broken
trigger, or a task one cannot start, shows as a warning.

## Agent queue

`"queue": true` caps the agents running at once on each machine. `maxRunningAgents` in
`config.json` sets the cap for this computer, and the same key on a `machines` entry sets it for
that machine; a machine without it has no cap. A task started over the cap reads "Queued" and
waits its turn, taking prompts meanwhile; tasks start in the order they were queued as agents stop
or exit. "Start now" on a queued task starts it at once, over the cap.

```json
{ "flags": { "queue": true }, "maxRunningAgents": 3 }
```

## Compacting idle conversations

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

## File previews

`"previews": true` previews a changed file in the diff view instead of its diff: images and SVG,
Jupyter notebooks, zip-family archives (zip, jar, whl and the like), SQLite databases and Mermaid
diagrams. A Diff button switches back. SQLite needs `sqlite3` on the PATH; Mermaid needs
mermaid-cli (`mmdc`) and shows the source without it. Only files of 20 MB or less inside the
task's worktree are read, and only for tasks on this computer.

## Notifications

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

## Plugins

`"plugins": true` loads WebAssembly plugins from the `plugins` folder in the Octo home. A plugin
can add ACP agents and actions in a task's Plugins menu. A plugin cannot reach files, processes or
the network. A broken one is skipped with a warning. See [plugins.md](plugins.md) for the ABI.

