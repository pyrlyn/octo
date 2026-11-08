# Configuration

Octo reads `config.json` from the Octo home: `OCTO_HOME`, or the platform's app data folder.
Every key is optional.

| Key | What it sets | Details |
| --- | --- | --- |
| `flags` | Optional features, by flag name: `{ "flags": { "fork": true } }`. | [Flags](#flags) |
| `defaultPermissionMode` | Permission mode of new tasks: `Supervised` (default), `AutoAcceptEdits`, `Auto` or `FullAccess`. | [using-octo.md](using-octo.md#permission-modes) |
| `worktreeSetup` | A repository's top-level path to a shell command run in each new worktree. | [using-octo.md](using-octo.md#worktree-setup) |
| `machines` | Remote machines (`id`, `host`, optional `port`, `worktrees`, `maxRunningAgents`, `repos`). Needs `remote-ssh`. | [remote-machines.md](remote-machines.md) |
| `remoteAccess` | `port` (default 47470) and `publicUrl` of the remote API. Needs `remote-access`. | [remote-access.md](remote-access.md) |
| `triggers` | Scheduled and webhook tasks. Needs `triggers`. | [features.md](features.md#triggers) |
| `maxRunningAgents` | Agents allowed at once on this computer. Needs `queue`. | [features.md](features.md#agent-queue) |
| `compactIdleMinTokens` | Context size from which idle Claude conversations are compacted (200K by default; 0 compacts all). Needs `compact-idle`. | [features.md](features.md#compacting-idle-conversations) |
| `maxChildTasks` | Child tasks, forks included, one task may have (default 10); 0 turns the messaging `create_task` tool off. | [features.md](features.md#agent-messaging) |
| `notifications` | Turns single notification events off, such as `{ "done": false }`. Needs `notifications`. | [features.md](features.md#notifications) |
| `appearance` | `theme` (`System`, `Light`, `Dark`), `transparency` (`Glass`, `Opaque`) and `density` (`Compact`, `Normal`, `Comfortable`). | |

## Flags

| Flag | Page |
| --- | --- |
| `demo-agent` | [using-octo.md](using-octo.md#demo-agent) |
| `antigravity` | Runs Google Antigravity through its ACP server. |
| `git-publish`, `agent-messaging`, `presets`, `handoff`, `fork`, `compare`, `sections`, `palette`, `usage`, `checkpoints`, `triggers`, `queue`, `compact-idle`, `previews`, `notifications`, `plugins` | [features.md](features.md) |
| `remote-ssh`, `move-task` | [remote-machines.md](remote-machines.md) |
| `remote-access`, `guest-share` | [remote-access.md](remote-access.md) |
