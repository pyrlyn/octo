# Octo — architecture

Phase 2 of the brief. It builds on [`research.md`](research.md) and on the creator's decisions in
[`plan/README.md`](../plan/README.md): Compose Desktop (JVM) UI over a Kotlin Multiplatform core;
the agent CLI the user installed, run under the user's own login; ACP plus native protocols.

## 1. Goals and non-goals

**Goals.**
- Run several CLI coding agents in parallel, each in its own git worktree and branch.
- Show live output and status (working, blocked, idle, failed).
- Send prompts, answer questions and permission requests, stop and restart.
- Persist sessions and layout, and restore them on restart.
- Review the diff of each task.
- Add an agent by adding one module, with no change to the core or the UI.

**Non-goals for the MVP.**
- A native headless daemon. The core stays native-ready (§3), but only the JVM build ships.
- PTY terminals. None of the five target agents needs one, because all of them have a
  structured protocol (research §6). The `pty-terminal` flag is reserved for later.
- Credentials. Octo never reads, stores or forwards an agent's login or API key.

## 2. Toolchain

| Item | Version | Why this one |
| --- | --- | --- |
| Kotlin | 2.4.21 | Latest stable |
| Gradle | 9.7.1 (wrapper) | Newest Gradle inside KGP 2.4.21's documented range (≤ 9.7.x) |
| JDK | 21 (Temurin) | Compose packaging needs ≥ 17; the machine default |
| Compose Multiplatform | 1.12.2 | Latest stable (1.13 is beta) |
| ktlint | 1.8.0 via `org.jlleitschuh.gradle.ktlint` 14.2.0 | Required by the brief |
| detekt | 2.0.0-alpha.6 (`dev.detekt`) | The only line built against Kotlin 2.4 (1.23.8 predates it); falls back to 1.23.8 if the alpha breaks |

**Runtime libraries.** All are first-party JetBrains libraries, so the brief's approval rule for
new third-party dependencies does not apply:

| Library | Version | Use |
| --- | --- | --- |
| kotlinx-coroutines | 1.11.0 | Concurrency, flows |
| kotlinx-serialization-json | 1.12.0 | Agent protocols, persisted state |
| kotlinx-io | 0.9.1 | Files, atomic moves |
| Compose Multiplatform | 1.12.2 | UI only |

Tests use `kotlin-test` and `kotlinx-coroutines-test`.

**Choices that avoid a dependency.**
- **No ACP SDK.** The official Kotlin SDK is JVM-only, and the core must compile to Kotlin/Native.
  We implement the small subset of JSON-RPC 2.0 and ACP we use in `adapters:base`. The same peer
  also serves Codex `app-server`, which omits the `jsonrpc` field.
- **No git library.** JGit lacks worktree support, and libgit2 has no KMP wrapper. We run the
  `git` CLI through the core's process API.
- **No database.** State is one JSON document written atomically (§8), so the workspace "no raw
  SQL" rule does not come into play.
- **No DI framework.** Constructor injection, wired by hand in `app`.

## 3. Modules

```
octo/
├── core/                 KMP: jvm, macosArm64, linuxX64, mingwX64
├── adapters/
│   ├── base/             KMP: same targets; depends on core
│   ├── claude/           KMP; depends on adapters:base
│   ├── codex/            "
│   ├── cursor/           "
│   ├── grok/             "
│   ├── kimi/             "
│   ├── antigravity/      "
│   └── plugin/           " (the generic ACP adapter for agents plugins declare)
├── ui/                   JVM + Compose; depends on core
└── app/                  JVM + Compose Desktop application; depends on everything, wires it
```

Dependencies point down only: `app → ui → core` and `app → adapters:* → adapters:base → core`.
Neither `ui` nor `core` depends on any adapter.

**`core`** owns the domain and the agent service-provider interface (SPI):
- the model (`Task`, `AgentSessionRef`, `AgentStatus`) and the status reducer;
- the adapter SPI (`AgentAdapter`, `AgentSession`, `AgentEvent`, `Capability`) and the
  `AdapterRegistry`;
- feature flags;
- the `ProcessLauncher` interface and the git and worktree services built on it;
- persistence;
- the orchestrator and the internal API (`OctoCore`);
- the plugin loader (`octo.core.plugins`), over the engine-neutral `wasm` interface from
  kotlin-wasm; the app hands it the Chasm runtime.

`commonMain` holds everything except platform implementations. `jvmMain` holds
`JvmProcessLauncher` (`ProcessBuilder`), `JvmProcessMonitor` and the platform paths.
`JvmProcessMonitor` implements the `ProcessMonitor` interface: it walks an agent's process tree
with `ProcessHandle`, takes CPU time from it and resident memory from one `ps` call (macOS,
Linux) or `Get-Process` call (Windows) per sample. On macOS the JDK reports CPU time only for its
own process, so `ps` supplies it there. Native targets compile
`commonMain` only. A native `ProcessLauncher` comes with the headless daemon; it is an interface,
not an `expect`, so nothing is missing at link time.

**`adapters:base`** holds the shared logic for building adapters:
- `BaseAgentAdapter` — binary lookup on `PATH`, version probe with a verified-version drift
  warning, environment building, an auth-check hook, capability declaration, and session start;
- `LineChannel` — newline-framed stdin/stdout over a spawned process, with stderr captured as log
  events;
- `JsonRpcPeer` — requests, responses, notifications and server-to-client requests, with an option
  to omit the `jsonrpc` field;
- `AcpAgentAdapter` — a generic ACP client (`initialize`, `session/new`, `session/load`,
  `session/prompt`, `session/update`, `session/request_permission`, `session/cancel`) with hooks
  for agent-specific launch arguments and extension methods;
- `StreamJsonSession` — the base for NDJSON event-stream CLIs.

**`adapters:<agent>`** holds only what is specific to that agent: the binary name, arguments,
event mapping, extensions and fixtures.

| Module | Transport | Binary |
| --- | --- | --- |
| `claude` | `claude -p --input-format stream-json --output-format stream-json --verbose --permission-prompt-tool stdio` (control protocol) | `claude` |
| `codex` | `codex app-server` (JSON-RPC over stdio, no `jsonrpc` field) | `codex` |
| `cursor` | ACP `cursor-agent acp` plus `cursor/*` extensions | `cursor-agent` (`agent` collides with Grok's binary) |
| `grok` | ACP `grok agent stdio` | `grok` (the official xAI build; refuses the community CLI) |
| `kimi` | ACP `kimi acp` | `kimi` (Kimi Code CLI; refuses the archived Python `kimi-cli`) |
| `antigravity` | ACP `agy_acp_server` | Behind the `antigravity` flag, with a terms notice |
| `demo` | In-process, no binary | Behind the `demo-agent` flag; asks for permission and writes `octo-demo.md` |
| `plugin` | ACP, with the arguments a plugin's manifest gives | The bare command the manifest names, on `PATH` |

**`ui`** follows MVVM, the creator's choice. Each screen has a view model that reads `OctoCore`
flows and exposes one immutable UI state as a `StateFlow`, plus methods for user actions that
call `OctoCore`. Composables only render that state and forward actions; they never touch
`OctoCore` or adapters. The UI talks only to `OctoCore`.

The agents screen lists running tasks with the adapter's icon, the probe's sign-in state (shown
as a mask; Octo has no secret to show), `OctoCore.processUsage` for CPU and memory, and the
task's `SessionStats` for model, context and tokens. A task on a remote machine shows "remote"
instead of process figures, because its local process is only the ssh client.

The command palette (`PaletteViewModel`) searches `OctoCore.state` and each task's transcript.
`rank` lists tasks first, in this order: title prefix, then a title word that starts with the
query, then a title that contains it, then a user or agent message that contains it, shown with
a snippet. Commands whose label matches come after the tasks. A command only opens state in
`MainViewModel` (`run`). The publish and switch-agent dialogs, and the devices dialog, live in
`MainUiState`, so the palette and the buttons open the same dialogs. The window binds the
shortcut.

The usage screen (`UsageViewModel`) asks `UsageReport.totals` for a period. An agent whose
adapter has a `UsageHistory` (Claude's `projects/*.jsonl`, Codex's rollout files) is counted only
from it, so nothing is counted twice; every other agent is counted from `UsageLog`
(`usage.json`), which records the growth of each task's `SessionStats` when a turn ends. A history
that cannot be read becomes a warning on the screen.

OS notifications (`NotificationsViewModel`) feed `OctoCore.state` and the window's focus into
the common `NotificationRule`, which turns a task's entry into Blocked, Failed or Done into one
notification per burst. Compose Desktop's tray shows them; AWT does not say which notification
was clicked, so a click opens the task notified last.

**`app`** holds `main()`, the adapter list, platform paths and the Compose Desktop packaging.

**`cli`** is the `octo` command (Clikt). It talks to a running Octo only through the
`remote-access` API, as a paired device, so it needs no flag of its own. The request and response
bodies (`access/ApiBodies.kt`) are shared with `RemoteApi`, so both ends read the same shapes.

## 4. The adapter SPI (core)

```kotlin
interface AgentAdapter {
    val id: AgentId                      // "claude", "codex", …
    val displayName: String
    val icon: AgentIcon?                 // a small SVG for the agents screen; null shows a monogram
    val capabilities: Set<Capability>    // Resume, Cancel, Permissions, Questions, Plan, Models, …
    val permissionModes: Set<PermissionMode> // the modes it maps; Supervised at least
    suspend fun probe(): AgentProbe       // installed? version? verified version? auth state?
    fun start(spec: SessionSpec): AgentSession
}

data class SessionSpec(
    val workingDir: String,
    val resume: AgentSessionRef?,         // the agent's own session id, when resuming
    val env: Map<String, String>,         // extra variables, e.g. a config-dir override in tests
    val options: Map<String, String>,     // model, …
    val permissionMode: PermissionMode,   // already one the adapter has
)

interface AgentSession {
    val events: Flow<AgentEvent>          // hot; completes when the process exits
    val processId: Long?                  // the agent's root process, for CPU and memory figures
    suspend fun prompt(text: String)
    suspend fun steer(text: String)       // into the running turn; only with Capability.Steer
    suspend fun respond(requestId: String, response: RequestResponse)
    suspend fun setPermissionMode(mode: PermissionMode) // in place; throws when it cannot
    suspend fun cancel()                  // cancel the current turn
    suspend fun close()                   // end the process
}
```

`AgentEvent` is the normalized vocabulary every adapter maps into:

| Event | Fields | Purpose |
| --- | --- | --- |
| `SessionReady` | `ref` | The agent's own session id, used for resume |
| `TurnStarted` | — | A turn began |
| `MessageDelta` / `Message` | role, text | Assistant or user text |
| `Thought` | text | Reasoning text |
| `ToolCall` | id, title, kind, status, detail | Tool activity |
| `PermissionRequested` | id, title, options | Needs an answer |
| `QuestionAsked` | id, questions | Needs an answer |
| `RequestResolved` | id | A pending request is closed |
| `PlanUpdated` | entries | The agent's plan |
| `TurnEnded` | `stopReason`, usage | `EndTurn`, `Cancelled`, `MaxTurns`, `Refusal`, `Error` |
| `StatsUpdated` | model, context used and window, session token totals | What the agent reports about its session; a null field keeps its last value |
| `Log` | stream, line | stderr and other diagnostics |
| `Failed` | message | The adapter or the protocol failed |
| `Exited` | code | The process ended |

**Rules.**
- The core never branches on `AgentId`. When a capability is missing, there is a declared
  fallback: without `Resume`, a restart starts a fresh session and the UI says so.
- An adapter must settle every open request on cancel and on exit, by emitting `RequestResolved`
  or `TurnEnded(Cancelled)`.
- Adapters never read or write agent credentials. Auth state comes from the CLI's own status
  command or its error output.

**Registry.** `AdapterRegistry` is built in `app` from a list:

```kotlin
listOf(ClaudeAdapter(...), CodexAdapter(...), CursorAdapter(...), GrokAdapter(...), KimiAdapter(...), AntigravityAdapter(...))
```

Adding an agent means a new module and one line in that list. The registry drops adapters whose
feature flag is off.

## 5. Status model

The status is the pure function `AgentStatusReducer.reduce(state, event)`, shared by every
adapter:

```
Starting ──SessionReady──▶ Idle ──TurnStarted──▶ Working ──TurnEnded(EndTurn|MaxTurns)──▶ Idle
                                                    │  ▲
                        PermissionRequested/QuestionAsked  RequestResolved (count back to 0)
                                                    ▼  │
                                                 Blocked
Any ──Failed / TurnEnded(Error|Refusal) / Exited(≠0 while not closing)──▶ Failed
Any ──Exited while closing──▶ Stopped
```

- Pending requests are counted in a set, so `Blocked` lasts until all of them resolve. This is
  AionUi's ref-counted "requires action".
- "Done" means idle but not yet seen by the user. It is a UI flag, not an agent state (herdr).
- Task status rollup for the sidebar uses this priority: `Blocked > Failed > Working > Starting >
  Done > Idle > Stopped`. This is T3 Code's ladder, with failures raised.

## 6. Feature flags

```kotlin
data class FeatureFlag(val id: String, val default: Boolean, val requires: Set<Capability> = emptySet())

class FeatureFlags(config: Map<String, Boolean>) {
    fun isEnabled(flag: FeatureFlag): Boolean
    fun isAvailable(flag: FeatureFlag, adapter: AgentAdapter): Boolean  // enabled AND capabilities present
}
```

- Flags are declared next to the feature that owns them. Core flags live in `core`'s
  `CoreFlags`.
- Values come from `config.json` in the Octo home; a missing key means the flag's default.
- Every optional feature defaults to **off**. The core works with every flag off.

| Flag | Task |
| --- | --- |
| `antigravity` | T8 |
| `git-publish` (commit, push, PR) | T11 |
| `remote-ssh` (tasks on machines from `config.json`, through the user's `ssh`; one shared connection per machine since T18) | T12 |
| `agent-messaging` (an MCP server on loopback: `list_tasks`, `send_message`, `wait_for_idle`) | T13 |
| `presets` (teams of agents with roles and prompts, kept in `presets.json`) | T14 |
| `remote-access` (a token-paired JSON API on `127.0.0.1`, reached through the user's tunnel) | T15 |
| `plugins` (WebAssembly plugins that add ACP agents and task actions; see `docs/plugins.md`) | T16 |
| `handoff` (switch a task's agent or model; a fresh session gets the conversation from the transcript) | T23 |
| `fork` (a new task on a new branch from a task's current worktree, linked to its parent) | T24 |
| `compare` (one prompt to several agents or models, each in its own task, grouped in the sidebar) | T26 |
| `sections` (pin, snooze and settle tasks; with `git-publish`, settle a task once its PR merges) | T28 |
| `palette` (Cmd+K: find tasks by title or message text and run the app's actions) | T30 |
| `usage` (token use per agent and model; a usage limit shows when it lifts, with Resume) | T29 |
| `checkpoints` (worktree snapshots on hidden refs after every turn, with revert) | T27 |
| `triggers` (tasks from `config.json` on a cron schedule or from a signed webhook) | T32 |
| `queue` (`maxRunningAgents` per machine; later tasks wait as Queued, with Start now) | T33 |
| `move-task` (move a task and its uncommitted work to another machine; needs `remote-ssh`) | T34 |
| `compact-idle` (compact a large idle conversation just before its prompt cache expires) | T35 |
| `previews` (preview images, notebooks, archives, SQLite and Mermaid files in the diff view) | T36 |
| `guest-share` (a guest watches a task by link and proposes messages; needs `remote-access`) | T37 |
| `notifications` (an OS notification when a task is blocked, fails or finishes a turn, one per burst) | T40 |
| `pty-terminal` | Reserved |

## 7. Core API and orchestration

`OctoCore` is the only surface the UI uses. It is coroutine- and flow-based, so a socket
transport can serve it later for a headless daemon or remote clients (T15).

```kotlin
interface OctoCore {
    val state: StateFlow<OctoState>                 // tasks, statuses, pending requests, layout
    fun transcript(taskId: TaskId): Flow<List<TranscriptEntry>>
    suspend fun createTask(request: NewTask): TaskId // repo, base ref, agent, title, first prompt
    suspend fun sendPrompt(taskId: TaskId, text: String) // idle: send; busy: steer or queue
    suspend fun editQueued(taskId: TaskId, queuedId: Int, text: String)
    suspend fun removeQueued(taskId: TaskId, queuedId: Int)
    suspend fun respond(taskId: TaskId, requestId: String, response: RequestResponse)
    suspend fun switchAgent(taskId: TaskId, agent: AgentId, model: String?) // same worktree and branch
    suspend fun forkTask(taskId: TaskId): TaskId    // new branch from the worktree as it is
    suspend fun setPermissionMode(taskId: TaskId, mode: PermissionMode)
    suspend fun cancel(taskId: TaskId)              // cancel the current turn
    suspend fun stop(taskId: TaskId)
    suspend fun restart(taskId: TaskId)             // resume when the adapter can, else fresh
    suspend fun removeTask(taskId: TaskId, deleteWorktree: Boolean, force: Boolean = false)
    suspend fun diff(taskId: TaskId): TaskDiff
    suspend fun updateLayout(layout: Layout)
    suspend fun markSeen(taskId: TaskId)            // clears the "done" mark
    val processUsage: Flow<Map<TaskId, ProcessUsage>> // CPU and memory of local running agents
}
```

`Orchestrator` implements it. `Orchestrator.open(...)` loads `state.json`; `close()` stops every
agent and writes the state. `close()` belongs to `app`, so it is not on the interface the UI sees.
- **Terms.** Before every start, `TermsGate.requireAcknowledged` runs; `createTask` throws
  `TermsNotAcknowledgedException` before it touches the repository.

- **Tasks and worktrees.** Each task gets a worktree at `<octo home>/worktrees/<repo>/<task-slug>`
  on a new branch `octo/<task-slug>` cut from the chosen base ref. Removing a task never deletes
  the branch, and a dirty worktree needs explicit confirmation (herdr).
- **Worktree setup.** `worktreeSetup` in `config.json` maps a repository's top-level path to a
  shell command. After a task's or a fork's worktree is created, `Workspace.runSetup` runs it with
  `/bin/sh -c` in the worktree through that machine's own launcher, so remote tasks run it over
  ssh. It shows in the transcript as a tool entry with the output's tail. A non-zero exit, an
  error or the 30-minute timeout (which kills the command) marks the task Failed, and the agent
  is not started. Only the user's config names the command, never the repository, so a cloned
  repository cannot run anything on its own.
- **Concurrency.** One `SupervisorJob` scope per task, so a crash in one agent never takes down
  another.
- **Diff.** `git diff <merge-base(base, HEAD)>` in the worktree plus untracked files, parsed into
  files and hunks in common code.
- **Restart.** The persisted `AgentSessionRef` goes into `SessionSpec.resume`.
- **Switching agents.** `switchAgent` keeps the worktree and branch. A new model for the same agent
  goes to the running session through `AgentSession.setModel` when the adapter has
  `ModelSelection` (Claude's `set_model` control request; Codex takes `model` on the next
  `turn/start`). Anything else, or a refused switch, stops the agent and starts a fresh session;
  the task remembers whom it came from, and the next prompt is wrapped in a handoff that
  `Handoff.build` makes from Octo's transcript: the original request, then the newest messages
  and command outcomes (each output's tail) that fit a token budget. The new request is never
  cut. The handoff goes out once.
- **Forks.** `forkTask` cuts a new branch from the parent's HEAD and copies its uncommitted and
  untracked files over through a separate git index, so the parent's index and files stay as they
  are. The fork keeps the parent's agent, options and transcript, and `Task.parentId` puts it under
  the parent in the sidebar. An adapter with `Capability.Fork` copies its own session
  (`SessionSpec.fork`: Claude's `--resume --fork-session` with an Octo-chosen `--session-id`,
  Codex's `thread/fork`); any other agent starts fresh and gets the T23 handoff with the next
  prompt.
- **Comparisons.** `OctoCore.compare` (an extension over `createTask`) starts one task per
  `AgentPick` (an agent and an optional model) with the same prompt, all sharing one
  `Task.group`. A pick that fails is reported in `Comparison.failures` and the others still run.
  The sidebar gathers a group's tasks, forks included, under the group's title.
- **Places.** `Task.place` (`TaskPlace`: Active, Pinned, `Snoozed(untilMs)`, Settled) is
  persisted with the task and changed through `setPlace`. A snooze without a time ends when the
  turn ends; any snooze ends when the agent blocks or fails. A timed one is woken by a coroutine
  that waits for the earliest `untilMs` on an injected `kotlin.time.Clock`. `SettleMerged` asks
  `MergedBranches` (the user's `gh pr list --state=merged`, once per repository every five
  minutes) and settles a local task whose branch merged, once per run, so a task the user moves
  back stays put. A repository `gh` cannot answer is skipped.
- **Usage limits.** An adapter emits `AgentEvent.RateLimited` when the provider refuses work
  (Claude's rejected `rate_limit_event`, Codex's `account/rateLimits/updated` with a reached
  limit). `TaskView.limit` holds it until the next turn starts. Resume is the user's: the UI
  sends "Continue", restarting the agent first if it stopped.
- **Checkpoints.** `Checkpoints` snapshots the worktree before a task's first prompt and at every
  `TurnEnded`, while the task still reads as working, so no new turn changes files under it. The
  snapshot goes through a separate index (`read-tree HEAD`, `add --all`, `commit-tree` with a
  fixed identity) onto `refs/octo/<task>/<n>`; the user's index, branch and stash stay as they are.
  At most 100 are kept on `Task.checkpoints`. `revert` waits for the turn to end and restores the
  files with a two-tree `read-tree -m -u` in that separate index, so files created since go away
  and ignored files stay. Each checkpoint names the agent run that was live; when every later
  turn ran in the live session, `AgentSession.rollback` drops them (Codex: `thread/revert` with
  the first dropped turn's id). Otherwise `Task.revertNote` goes before the next prompt and the
  kept checkpoints lose their run, so no later revert rolls the agent back past turns it still
  remembers. `removeTask` deletes the task's refs.
- **Agent limit.** With the `queue` flag on, `maxRunningAgents` caps the live agent processes per
  machine. `StartQueue` holds the slots in one atomic `AgentSlots` value: a start without a free
  slot leaves the task `Queued` (with a notice) and joins its machine's queue. A slot frees only
  once the process is gone, when a stop finishes or the agent's event stream ends; restart and
  switch keep theirs. The freed slot goes to the oldest waiting start on that machine, which runs
  under its task's lock and only if the task still reads `Queued`. A start that fails passes the
  slot on at once. Prompts to a queued task join `Task.queue` and go out once it is idle.
  `startNow` starts a waiting task over the limit; `close()` empties the queue first, so stopping
  agents on the way out starts none. Nothing waits across a restart of Octo: every task loads
  stopped.
- **Moving a task.** With the `move-task` flag on, `moveTask` stops the agent and `TaskMove`
  snapshots the worktree onto `refs/octo-move/<task>` (the checkpoint snapshot, so untracked files
  count), with the branch tip as its parent. `RefTransfer` pushes or fetches that ref with git's
  own ssh transport: `SshProcessLauncher.at` gives the clone's `ssh://` URL and the same `ssh`
  command, options and shared socket as `GIT_SSH_COMMAND`. Between two remote machines the ref
  goes through a bare `relay.git` on this computer. On the target, `createWorktree` with
  `takeOver` puts the branch at `<ref>^` (taking over a branch an earlier move left there) and
  `restore` brings the files back as uncommitted changes. The hidden refs are deleted on every
  side either way. A failure takes the new worktree away again and restarts the agent where it
  was. Otherwise the old worktree is deleted (a failure there is only a notice), and the task gets
  the new machine, repo and worktree, forgets its checkpoints and starts a fresh session with a
  handoff.
  `MachineConfig.repos` maps this computer's repository paths to the clones on each machine.
- **Idle compaction.** With the `compact-idle` flag on, an adapter that declares `Compact` reports
  `SessionStats.promptCacheTtlMs` as each request starts (Claude reads it from `message_start`'s
  `cache_creation`). `IdleCompaction` sets the task's time two minutes before that lifetime ends
  and wakes at the earliest one. Under the task's lock the orchestrator checks the task again
  (idle, nothing queued or pending, active or pinned, at least `compactIdleMinTokens` in the
  context, 200K by default) and calls
  `AgentSession.compact()`, which Claude takes as `/compact`. A prompt or a stop cancels the time,
  and the compaction's own requests set none. The times live in memory only.
- **File previews.** With the `previews` flag on, the app hands `ViewModels` a `FilePreviewer`
  (`ui/preview`), which picks a renderer by extension. Repository files are untrusted: the real
  path must stay inside the worktree and under 20 MB, pictures over 40 megapixels are refused,
  an archive is listed from its central directory without inflating it, and SQLite is read by
  the `sqlite3` CLI in read-only safe mode. Mermaid goes through `mmdc` to PNG, since Skia's SVG
  renderer drops Mermaid's HTML labels. Remote tasks keep the plain diff.
- **Guest links.** With `guest-share` and `remote-access` on, `Shares` (`core/access`) keeps one
  link per task in `shares.json` as a SHA-256 of its 256-bit token, and the guests' proposals in
  memory: at most 10 waiting and 50 kept per link, 4000 characters each. `RemoteApi` serves
  `/api/v1/guest` to a share token only, never a device token, and the guest reads one task and
  proposes; approving settles the proposal before `sendPrompt`, so it is sent once.
  `RemoteHttpServer` serves the static page at `/share` under a CSP with no outside sources; it
  renders the transcript as text only. `LoopbackHttpServer` still refuses foreign `Origin`s, but
  accepts `remoteAccess.publicUrl`'s, since a browser sends `Origin` on a tunnel page's POST.
- **Prompts while busy.** `sendPrompt` runs under the task's lock. An idle agent gets the prompt
  and reads as working at once, so a second sender never sees a stale idle. A busy agent is
  steered when its adapter declares `Steer` and the steer succeeds; otherwise the text joins
  `Task.queue` (at most 20, persisted with the task). Each time the agent turns idle the head of
  the queue is sent; a send that fails puts it back with a notice.
- **Triggers.** `Triggers` (common) starts `TriggerConfig.task` through `OctoCore.createTask`. A
  schedule is a `Cron` from a `CronParser`; the JVM one, `KrontabCronParser`, expands standard
  cron into a krontab template, because krontab has no macOS arm64 build and counts days and
  months from 0. Each schedule is one coroutine that naps at most a minute at a time against the
  wall clock, so a computer that slept catches up; `lastRun` in `triggers.json` (a
  `JsonListFile`) turns every missed run into one. A webhook is `Webhooks.deliver`, reached
  through `RemoteApi`'s `hooks/{id}` route before the device-token check: the HMAC-SHA256 of the
  body or a bearer secret, both compared through SHA-256 so timing says nothing, then the event
  and filters, then the delivery id (`X-GitHub-Delivery`, else the body's hash), of which the last
  100 are kept. The answer (202) goes out before the task starts; a start that fails releases the
  delivery id and becomes a warning through `OctoCore.warn`.

## 8. Persistence

- **Octo home.** `OCTO_HOME` when set. Otherwise the platform default: macOS
  `~/Library/Application Support/Octo`, Linux `$XDG_STATE_HOME/octo` (falling back to
  `~/.local/state/octo`), Windows `%APPDATA%\Octo`. Paths are resolved once in `app` and passed
  down as `OctoPaths`; the core never reads the environment itself.
- **`state.json`.** Tasks (id, title, repo, worktree, branch, base ref, agent id, the agent's
  session ref, and the last status), the transcript tail for each task (bounded), and the layout.
  Writes go to a temp file followed by an atomic move. The last 3 good files are kept as
  `state.json.1..3`, and a corrupt file is moved aside and logged. A load failure never blocks
  start-up (fail open).
- **Cold start.** Every task is restored as `Stopped`. Restart resumes the agent session through
  its ref (AionUi's "cold start = idle + resume anchor").
- **`config.json`.** Flags and user preferences, read at start-up.

## 9. Security and trust

- Everything an agent, a repository or a plugin writes is untrusted input. Protocol parsers
  tolerate unknown fields and unknown event types: they log them and skip them.
- Octo passes the user's environment to agents unchanged, apart from documented additions. It
  never injects or reads tokens.
- Permission requests are never auto-approved by default: a task runs Supervised unless the user
  or `defaultPermissionMode` in the config picks a looser `PermissionMode`. Each adapter maps the
  mode to its own setting (Claude's `--permission-mode`, Codex's approval policy and sandbox);
  an agent without the mode runs Supervised.
- Process handling: Octo kills only the processes it spawned, by handle and never by pattern, and
  kills the whole process tree on stop.
- Plugins run in a WebAssembly interpreter with no imports: they can reach nothing but the JSON
  they are given. Each call is fuel-metered and its output is size- and bounds-checked. An agent a
  plugin declares is a bare command on `PATH` with fixed arguments, and it cannot take a built-in
  agent's id.

## 10. Testing

- **Core.** The reducer, flags, persistence round-trip (temp dir), the diff parser, and worktree
  lifecycle against a temp git repo with a temp `HOME` and `GIT_CONFIG_GLOBAL`.
- **Adapters.** A `FakeProcessLauncher` replays fixture NDJSON or JSON-RPC lines and records what
  the adapter wrote to stdin. No test spawns a real agent or touches a real account. Fixtures live
  in `src/commonTest/resources`, are tagged with the CLI version they mirror, and start from the
  documented schemas (research §6).
- **Isolation.** Tests get their Octo home from a temp dir. Any test that runs `git` sets `HOME`,
  `XDG_CONFIG_HOME` and `GIT_CONFIG_GLOBAL` to temp paths.
- **UI.** View models are plain classes over a fake `OctoCore`, tested without a window.
- **Gates.** `./gradlew check` runs the tests on JVM and on the native host targets, plus ktlint
  and detekt. CI runs it on Linux (JVM, `linuxX64`, `mingwX64` compile).

## 11. Mapping the brief

| Brief item | Where |
| --- | --- |
| Base adapter: spawn, I/O, parsing hooks, status, prompt, resume, cancel, auth check, capabilities | `adapters:base` (§3, §4); PTY deferred (§1) |
| One adapter per agent | `adapters:<agent>` |
| Registry, no core or UI change | `AdapterRegistry` + `app` list (§4) |
| Feature flags with capability check | §6 |
| Headless-ready core | `OctoCore` (§7), KMP native targets (§3) |
| Third-party libraries wrapped | No runtime third-party libraries; process, git and files sit behind core interfaces |
