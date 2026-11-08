# Using Octo

Settings live in `config.json` in the Octo home; see [configuration.md](configuration.md).

## Agents

Agents in the sidebar lists every running agent: its icon, whether it is signed in (never the
login itself), the CPU and memory of its own processes, its model, how much of its context
window is used and its session's token totals. Each row has a stop button.

You can write to an agent while it works. Codex takes the message into the running turn. Other
agents get it once they are idle: it waits under the transcript as queued, where you can edit or
remove it, and the queue is kept across restarts.

## Demo agent

To try Octo without installing an agent, turn on the built-in demo agent in `config.json` in
the Octo home (`OCTO_HOME`, or the platform's app data folder):

```json
{ "flags": { "demo-agent": true } }
```

## Permission modes

Each task runs in one of four permission modes: Supervised (the agent asks before commands and
edits), Auto-accept edits, Auto (the agent's own review approves routine actions) and Full
access. Pick one in the new-task dialog or change it on a running task. An agent without the
chosen mode runs Supervised and says so in the transcript. Claude Code, Codex and ZCode have all four;
the ACP agents run Supervised. ZCode runs each turn headless, so an action that would need your
answer fails inside the turn instead of asking. New tasks take `defaultPermissionMode` from `config.json`:

```json
{ "defaultPermissionMode": "AutoAcceptEdits" }
```

## Worktree setup

A fresh worktree has no installed dependencies or `.env` files. `worktreeSetup` in `config.json`
names a command per repository (its top-level path) that Octo runs with `/bin/sh` in each new
worktree, forks included, before the agent starts:

```json
{ "worktreeSetup": { "/Users/me/src/app": "npm ci && cp ../app/.env ." } }
```

Its output shows in the task. If it fails or runs for more than 30 minutes, the task is marked
failed and the agent does not start. On a remote machine the command runs there, and the path is
the repository's path on that machine.
