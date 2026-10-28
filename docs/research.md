# Octo — Phase 1 research

Research for Octo, a Kotlin desktop orchestrator for CLI coding agents. All facts were checked on
**2026-10-26** unless a different date or version is given. Nothing was cloned, installed or run;
sources are repository files read through the GitHub API, vendor docs and package registries.

**Source convention.** Each section lists its primary sources. A file path such as
`docs/internals/overview.md` refers to the repository named in the section heading, at the commit
pinned in [§10 Sources](#10-sources). Facts backed only by secondary sources (blogs, forums,
competitors' claims) are marked **unverified**. Statements labelled *Assessment* are our
interpretation, not facts from a source.

## Contents

1. [Key findings](#1-key-findings)
2. [T3 Code, herdr, AionUi in depth](#2-t3-code-herdr-aionui-in-depth)
3. [Other orchestrators](#3-other-orchestrators)
4. [ACP — Agent Client Protocol](#4-acp--agent-client-protocol)
5. [Comparison table](#5-comparison-table)
6. [Driving the target agents](#6-driving-the-target-agents)
7. [Kotlin platform facts for the UI-target decision](#7-kotlin-platform-facts-for-the-ui-target-decision)
8. [Features to adopt, ranked](#8-features-to-adopt-ranked)
9. [Open questions and unverified items](#9-open-questions-and-unverified-items)
10. [Sources](#10-sources)

---

## 1. Key findings

1. **ACP covers all five target agents.** The official ACP registry lists Claude (adapter
   `claude-agent-acp`), Codex (adapter `codex-acp`), Cursor (native `agent acp`), Grok Build
   (native `grok agent stdio`) and Antigravity (native binary `agy_acp_server`). A single ACP client
   in the base adapter can drive all of them; per-agent adapters add launch details and
   extensions. ([§4](#4-acp--agent-client-protocol), [§6](#6-driving-the-target-agents))
2. **There is an official Kotlin ACP SDK** (`com.agentclientprotocol:acp` 0.34.0, 2026-10-21), but
   it is **JVM-only** today; JS/Native/Wasm are "on the roadmap". Its license is unclear (GitHub
   metadata says Apache-2.0, the README badge says MIT).
3. **Compose Multiplatform desktop runs on the JVM only.** JetBrains closed the Kotlin/Native
   desktop request as *Not Planned* (CMP-1923, 2026-03-20). No maintained toolkit offers a pure
   Kotlin/Native GUI on macOS, Linux and Windows. The core can be Kotlin/Native on all three:
   coroutines, serialization, Okio, kotlinx-io, SQLDelight, Ktor and Koin publish `macosArm64`,
   `linuxX64` and `mingwX64`. ([§7](#7-kotlin-platform-facts-for-the-ui-target-decision))
4. **Status must come from structured events, not from screen scraping.** The tools that rely on
   structured events (T3 Code, AionUi, Emdash, Conductor's API) get reliable
   working/blocked/idle states. The scraping tools (herdr, Claude Squad, ccmanager) document
   false-idle cases and need constant regex upkeep. The ACP v2 draft adds
   `running | requires_action | idle`, which matches Octo's model directly. Claude Code
   (`session_state_changed`) and Codex app-server (`thread/status/changed` with
   `waitingOnApproval`) already emit equivalent signals.
5. **Terms-of-service risk on two of five agents.** Anthropic does not permit third parties to offer
   claude.ai login or route requests through a subscription "on behalf of their users".
   Antigravity's FAQ says wrapping `agy` in third-party apps "is not supported under consumer
   plans" and calls third-party access a ToS violation. *Assessment:* Octo should only spawn the
   user's own installed, unmodified CLI under the user's own login, never handle tokens, and show
   the vendor's terms for Antigravity before first use. This needs the creator's decision
   ([§9](#9-open-questions-and-unverified-items)).
6. **Every target agent can be isolated for tests**: `CLAUDE_CONFIG_DIR`, `CODEX_HOME`,
   `CURSOR_CONFIG_DIR` and `GROK_HOME` relocate config and state. Antigravity has no such
   variable, so the only way is to override `HOME`, and its OS-keyring tokens do not follow a fake
   `HOME`.
7. **The field is churning.** Vibe Kanban is sunsetting, Crystal is deprecated, Terragon shut down,
   OpenHands pivoted to "Agent Canvas" (ACP-based), and Superset became Paradev under ELv2. The
   most active designs that lasted (T3 Code, Emdash, Zed) are converging on ACP plus a few native
   protocols.

---

## 2. T3 Code, herdr, AionUi in depth

### 2.1 T3 Code — `pingdotgg/t3code` (https://t3.codes)

**What it is.** "A minimal GUI for coding agents. A Node WebSocket server wraps provider CLIs and
agents (Codex, Claude Code, Cursor, Grok, OpenCode, Antigravity) and serves web, desktop, and mobile
clients" (`AGENTS.md`). MIT. 30.4k stars; latest stable v0.1.2 (2026-10-22), with nightly builds
several times a day. The README says "very very early … expect bugs" and that contributions are
mostly not accepted.

**Stack.** Server: Node + TypeScript + Effect, SQLite (`~/.t3/userdata/statev2.sqlite`), `node-pty`
for the integrated terminal only. Web: React, TanStack Router, zustand, `@pierre/diffs`. Desktop:
Electron 44. Mobile: Expo / React Native. One package per provider (`packages/provider-*`) and per
source-control host (`packages/source-control-*`).

**Features** (`docs/user/*`, README):
- Threads per project; switch models mid-thread; switch providers between runs through a
  "portable context handoff".
- "New worktree" per thread with a branch prefix (default `t3/`); setup scripts in `t3.json`
  (`runOnWorktreeCreate`).
- Shift-click several models to send one prompt to N threads, each in its own worktree.
- Permission modes: Supervised, Auto-accept edits, Auto, Full access.
- Approvals and structured questions in the timeline, plan mode, steering and queued messages,
  Stop, and a "Limited" state (usage limits) with resume.
- One-button commit/push/PR, generated PR text, stacked and draft PRs, PR review. Threads are
  settled automatically when their PR merges.
- Checkpoints on **hidden git refs**. Diffs are derived from checkpoints, not from provider events.
  Revert is coordinated with the provider conversation.
- Integrated terminal, usage dashboard, several accounts per provider, scheduled tasks,
  notifications.
- "Outside agents": T3 exposes its own MCP server, so other agents can create threads and manage
  worktrees (`docs/user/outside-agents.md`).

**UX.** A sidebar of threads in Pinned / Active / Snoozed / Settled sections, a chat timeline with
grouped tool-call activity, a diff panel and a command palette. Status pills follow a priority
ladder (`apps/web/src/components/Sidebar.logic.ts`): **Pending Approval > Awaiting Input >
Working/Connecting > Plan Ready > Completed**, plus failed, limited and settled.

**Architecture** (`docs/internals/overview.md`, `docs/orchestration-v2/README.md`):
- The server owns every piece of state: provider processes, terminals, git, files. Clients are
  thin RPC clients over an authenticated WebSocket (`packages/contracts/src/rpc.ts`) and negotiate
  capabilities.
- Orchestration is event-sourced: `Orchestrator.ts` turns commands into events; `EventSink.ts`
  commits events, projections, the command receipt and outbox effects in one transaction;
  `EffectWorker.ts` runs side effects after the commit. `RunFinalizationService.ts` settles a run
  separately from the provider's turn end. Data model: AppThread → Run → ExecutionNode tree →
  ProviderThread. "App ids are primary. Provider ids are refs."

**Agent integration.** No PTY scraping. Each driver implements `ProviderAdapterV2`
(`packages/provider-core/src/server/ProviderAdapter.ts`) and emits normalized events
(`provider_turn.updated`, `node.updated`, `message.updated`, `runtime_request.updated`,
`turn.terminal{completed|interrupted|cancelled|failed}` …). Capability flags gate fork, rollback,
steering and subagents; when a capability is missing, a declared fallback takes over. A *driver
kind* is an integration and a *provider instance* is one account or config.

| Agent | Transport | Key files |
| --- | --- | --- |
| Claude Code | Claude Agent SDK `query()` (`resume`, `forkSession`, `canUseTool`, `pathToClaudeCodeExecutable`) | `apps/server/src/orchestration-v2/Adapters/ClaudeAdapterV2.ts` |
| Codex | `codex app-server` JSON-RPC over stdio, generated client | `provider/codexLaunchArgs.ts`, `packages/effect-codex-app-server` |
| Cursor | `@cursor/sdk` (explicitly not ACP) | `packages/provider-cursor/src/server/*` |
| Grok Build | ACP, shared adapter plus `xaiAcpExtension.ts` | `packages/provider-grok`, `packages/provider-acp` |
| Antigravity | ACP (`antigravity-acp`), per-instance profile isolation, managed installer | `Adapters/AntigravityAdapterV2.ts`, `provider/AntigravityInstallation.ts` |
| Any ACP registry agent | Generic ACP adapter | `packages/provider-acp-registry` |

The rule in `docs/internals/adding-a-provider.md`: "Never add agent-id checks to the generic
registry adapter". An agent with extra behaviour gets a small driver on top of the shared ACP
adapter. Adapters are tested with replay fixtures recorded from real providers
(`record-*-replay-fixture.ts`; scenarios `simple`, `multi_turn`, `turn_interrupt`,
`provider_thread_resume` …). The same document lists traps: Stop sent before the turn started,
background work that outlives the turn, a failed turn that must settle open approvals, and the
macOS `/tmp` symlink.

**Sessions.** Durable app threads; resume via native session ids
(`ProviderRuntimeRecoveryService.ts`); forks keep app-level lineage, use a native fork when the
provider supports it and a context handoff otherwise (`ThreadForkService.ts`). Approvals persist as
`runtime_request` rows and survive a restart. Existing agent sessions can be imported
(`AgentSessionScanner.ts`).

**Remote.** T3 Connect (a hosted relay with sign-in), LAN/tailnet pairing (`t3 pair`, QR code),
Tailscale, SSH, mobile apps, a web app, and a background service (`t3 service install`)
(`docs/user/remote-access.md`).

**Extensions.** No generic plugin API. The extension points are provider packages (source-level),
ACP registry agents (runtime), MCP in both directions, and per-project `t3.json` scripts.

**Weaknesses.** Very early: 3.5k open issues, rapid churn, and about 11k "low-signal" tests deleted
in v0.1.2. Heavy architecture: Effect, event sourcing, 69 migrations, a V1→V2 importer. Depends on
vendor SDK and protocol stability. No live terminal view of an agent run. The remote path relies on
hosted components.

### 2.2 herdr — `herdrdev/herdr` (https://herdr.dev)

**What it is.** "The runtime your coding agents live on" — a terminal (TUI) workspace manager and
multiplexer: "one rust binary, no electron". Apache-2.0. 46.0k stars; v0.9.5 (2026-10-14). Windows
support is beta. No unsolicited PRs.

**Stack.** Rust, `ratatui` + `crossterm`, vendored `portable-pty`, `libghostty-vt` for terminal
emulation, `tokio`, `interprocess` (Unix sockets / named pipes), bincode wire protocol, and a JSON
socket API with a published JSON Schema (`docs/next/api/herdr-api.schema.json`).

**Features.**
- Detachable background server with named sessions.
- Several machines in one window (local plus saved SSH machines).
- Per-pane agent status `working | blocked | done | idle | unknown`, rolled up to tab, workspace
  and sidebar; sound and OS notifications.
- CLI and socket automation (`agent start/prompt --wait/wait/read/send-keys`), so agents can drive
  other agents.
- `herdr worktree create|open|list|remove`.
- Plugins and a marketplace.
- Snapshot restore and native agent resume; live handoff on update (`--handoff`).
- No diff or review UI found.

**Architecture** (`AGENTS.md`). The server owns PTYs and state; TUI clients attach.
`AppState` is pure data and can be tested without PTYs. Platform code lives in
`src/platform/<os>.rs`. "Detection is decoupled": the detector reads a screen snapshot and never
touches the parser. The client endpoint contract is frozen, with immutable codecs. Session
persistence: `session.json` written atomically, 3 backups of failed loads, up to 48 layout
snapshots; optional screen history, off by default because it may contain secrets.

**Agent integration.** Agents are plain processes in PTYs; 24 agents are recognised, Grok and
Antigravity among them. Optional **integrations** install hooks into the agent's own config (for
example Claude `settings.json` `SessionStart`, Codex `hooks.json`). Most of them only report the
session id and the resume command; a few (Pi, OpenCode, Kilo …) also report state. Every pane gets
`HERDR_ENV`, `HERDR_PANE_ID`, `HERDR_BIN_PATH` and `HERDR_SOCKET_PATH`. Any agent can call the
public reporting contract:

```
herdr pane report-agent <pane> --source S --agent A --state idle|working|blocked --seq N \
  [--agent-session-id ID] -- <resume argv>
```

`--seq` must be strictly increasing, so out-of-order reports are dropped. The resume argv has
safety rules: the first word is a plain PATH command, at most 64 args and 8 KiB.

**Status detection — the most instructive part** (`src/detect/mod.rs`,
`src/detect/manifests/*.toml`). The order of authority:
1. Find the agent's foreground process in the PTY.
2. Read the **live bottom of the buffer**, not the scrolled viewport.
3. Evaluate a per-agent **TOML manifest** (regions such as `osc_title` or
   `bottom_non_empty_lines(12)`; `regex`, `contains`, `not` and `priority` fields).
4. If an integration reports state, it wins.

PTY output activity is the normal working signal; screen evidence is the fallback. "Done" means
idle but not yet seen, tracked per client. Manifests auto-update from herdr.dev, can be overridden
locally, and `herdr agent explain` shows which rule matched. The docs list the known limits:
unknown prompts can show as `idle` (so automation could type into an approval dialog), and
Antigravity and Grok look idle while background tasks run.

**Worktrees** (`src/worktree.rs`). `worktree create --branch NAME [--base REF]` runs
`git worktree add` and opens the result as a workspace grouped under its repo. `worktree remove`
never deletes the branch (`--force` handles a dirty tree). There is a `--trust-repository` guard
and a `worktree.created` plugin event.

**Remote.** SSH-native: `herdr --remote host` or saved machines over a shared OpenSSH ControlMaster;
an installer that can set up herdr on the remote host. The phone story is "use an SSH client" —
there is no web or mobile app.

**Plugins.** A `herdr-plugin.toml` manifest (`[[startup]]`, `[[actions]]`,
`[[events]] on = "worktree.created"`, `[[panes]]`); commands are plain argv and "the entire Herdr
CLI is the plugin API". Install from GitHub; the marketplace indexes the `herdr-plugin` topic.
Plugins are **not sandboxed or reviewed**.

**Weaknesses.** Scraping is brittle: manifests need patching on every agent UI change (for example
the 2026-10 Claude spinner glyph). No structured transcript, cost or diff model. Processes do not
survive a server restart. Terminal-only UI. Integrations modify the user's agent configs.

### 2.3 AionUi — `iOfficeAI/AionUi` + backend `iOfficeAI/AionCore`

**What it is.** "Open-source 24/7 Cowork app for OpenClaw, Hermes, Claude Code, Codex, OpenCode and
20+ more CLI Agent". A general assistant platform (office documents, assistants, chat-channel
bots) that also hosts CLI agents and ships its own agent (`aionrs`). Apache-2.0. 33.8k stars;
latest stable v2.2.2 (2026-09-09), no newer stable release despite daily pushes. The AionCore
backend has only 123 stars (v0.2.2).

**Stack.** Electron + React 19 + Arco Design (`packages/desktop`), web host and CLI packages, and an
Expo mobile app. The Electron main process launches **AionCore**, a Rust (Axum + Tokio + sqlx
SQLite) workspace of 24 crates, and talks to it over HTTP plus one WebSocket (`/ws`). AionCore is
layered: Foundation → Capability → Domain → Composition. Each domain has `routes.rs`, `service.rs`
and `state.rs`; repositories are traits; WebSocket events are named `domain.actionName`.

**Features.** Parallel conversations with many agents (auto-detected CLIs); unified MCP
management. **Team Mode**: a Leader agent decomposes the work and delegates to Teammates through a
built-in Team MCP server — async mailbox, shared task board, per-agent permission badges, silent
agents escalated to failed (`crates/aionui-team/*`). Scheduled tasks (cron, keep-awake, missed
triggers). Preview panel, skills, i18n. Remote access through WebUI/PWA, a mobile app and chat
channels (Telegram, Lark, DingTalk, WeChat, WeCom). **No git worktree isolation**: in Team Mode
"all agents share the same folder" (README).

**Agent integration** (AionCore `crates/aionui-session`, `crates/aionui-ai-agent`):
1. **Direct CLI** for Claude and Codex ("claude/codex always run through `SessionAgentTask`").
   Claude: the `claude` CLI with stream-json in and out, `--session-id` / `--resume`,
   `--permission-mode`, `--mcp-config --strict-mcp-config`, and version constants
   (`VERIFIED_CLAUDE_VERSION`, `classify_cli_version`) that warn about CLI drift. Codex:
   `codex app-server` JSON-RPC with reverse-RPC approvals. Antigravity: `agy`, one process per turn
   with `--conversation <id>`; approvals go through a **PreToolUse hook that calls back to AionUi's
   HTTP endpoint** with a per-conversation token.
2. **ACP** for most other agents (`manager/acp/*`, `acp_launch_policy.rs`), plus ACP adapters
   contributed by extensions.
3. The built-in agent and remote agents.

A trait seam (`SessionBackend` / `BackendConnection`) feeds a **shared pure reducer**
`step(state, SessionEvent)`; adding a backend does not touch the reducer. Tool output is sanitized
(inline base64 stripped) before it reaches SQLite or the WebSocket.

**Status.** A server-authoritative FSM (`aionui-session/src/state.rs`):
`Starting | Running{…, requires_action} | Error | Idle`. "Requires action" is made of ref-counted
`waiting_on_approval`, `waiting_on_auth` and `waiting_on_question` counters inside `Running`.
Background workflows and subagents are tracked separately (`workflow_roster`). No scraping.

**Sessions.** Only `backend_session_id` and the turn epoch are persisted: a cold start always
rebuilds to `Idle` and resumes the backend (`backend/rehydrate.rs`). Idle processes can suspend and
wake on the next message (`backend/suspend.rs`). An idle scanner reaps ACP agents (10 min solo,
30 min team). A supervised-process crate provides containment, a process tree, startup orphan
cleanup and an instance lock (`aionui-process`).

**Extensions.** An `aion-extension.json` manifest with declared `permissions`
(storage, network, shell, filesystem, events) and `contributes`
(`acpAdapters`, `mcpServers`, `assistants`, `skills`, `themes`, `settingsTabs`), plus an extension
hub.

**Weaknesses.** No worktrees or diff/commit workflow. The focus is office work and China-market
channels, not parallel coding. A two-repo split with duplicated legacy layers and stale docs (the
architecture overview it references is missing). Mixed protocol families. Slow stable releases.

---

## 3. Other orchestrators

All entries checked 2026-10-26; stars and versions come from GitHub metadata.

| Tool | What it does | How it talks to agents | Strengths | Weaknesses |
| --- | --- | --- | --- | --- |
| **Claude Squad** (`smtg-ai/claude-squad`, Go, AGPL-3.0, 8.7k★, v1.0.20) | TUI; one tmux session and worktree per agent (`~/.claude-squad/worktrees`) | tmux + `send-keys`; status from a SHA-256 of the pane plus hard-coded prompt strings (`session/tmux/tmux.go`) | Tiny; works with any CLI | Brittle English-string matching; auto-presses Enter on trust prompts; tmux dependency; AGPL |
| **Conductor** (conductor.build, closed, macOS, v0.92.0) | Parallel Claude Code / Codex / Cursor / OpenCode in worktrees; review, PRs, checks | Undocumented (bundled CLIs; Cursor "through the Cursor API") **unverified** | Polished; REST API with `idle | working | errored` session status; cloud workspaces; iOS app | Closed source, macOS only, paid tiers; cloud stores transcripts |
| **Vibe Kanban** (`BloopAI/vibe-kanban`, Rust + TS, Apache-2.0, 28.5k★) | Kanban board plus workspaces with diff comments and PRs | Claude `-p --output-format=stream-json --input-format=stream-json --permission-prompt-tool=stdio` (control protocol); `codex app-server`; Cursor stream-json; an ACP crate. Pinned CLI versions | Structured protocols across ~10 agents | **Sunsetting** (announced 2026-04-10); version pinning burden |
| **Crystal** (`stravu/crystal`, Electron, MIT) | Claude/Codex in parallel worktrees | PTY + `--output-format stream-json`, `--resume`; **in-app MCP permission server** via `--permission-prompt-tool`; `codex exec --json` | Clean stream-json reference; per-execution diffs | **Deprecated** (Feb 2026, now Nimbalyst) |
| **OpenHands Agent Canvas** (`OpenHands/OpenHands`, MIT, 91.9k★, v1.33.0) | Self-hosted control center for agents and automations | Python Agent Server spawns agents through **ACP adapters** (`claude-agent-acp`, `codex-acp`, `gemini --acp`) | Multi-backend (local, Docker, VM, cloud); automations | Sandboxes instead of worktrees; Python server; fast churn |
| **Zed agent panel** (`zed-industries/zed`, 92.2k★) | Editor with external agents | **ACP** (registry or a custom command) | The reference ACP client; thread import | No worktree orchestration |
| **Emdash** (`generalaction/emdash`, Electron, Apache-2.0, 6.1k★, v1.2.9) | Parallel agents, worktree per task, issue intake, review, PRs, **SSH remote** | Two runtimes: **PTY with hook-reported status** and **ACP chat sessions**. 37 providers, 23 ACP-capable | Explicit policy: "does not infer agent status from terminal output"; cross-platform | Electron; hooks mutate user agent configs; churn |
| **Sculptor** (`imbue-ai/sculptor`, Python + Electron, MIT, v0.49.0) | Parallel agents in worktrees | Claude Code stream-json with the control protocol; other agents as plain terminals | "An interrupted turn survives a quit, or a crash of Sculptor" | Research preview; 240★ |
| **Paradev** (formerly Superset, Electron, **ELv2**) | Worktree per task, terminals, diffs | PTY + "Paradev hooks" (mechanics **unverified**) | 100+ agents | Not OSI open source; Windows unavailable |
| **ccmanager** (`kbwo/ccmanager`, TS/Ink, MIT, v4.4.9) | TUI over worktrees and sessions | PTY + headless xterm; per-agent regex detectors with a **1.5 s idle debounce** ("Claude Code sometimes appears idle … while still actively processing") | No tmux | Evidence of the scraping maintenance cost |
| **agent-deck** (Go, MIT, v1.16.34) | TUI "mission control", web UI, chat bridges, remote decks | tmux + per-agent hook commands | Groups, forks, web UI | Mechanism detail **unverified** |
| **Jean** (`coollabsio/jean`, Tauri + Rust, Apache-2.0, v1.0.12) | Worktree per task, many agents, headless `jean-server` | Stream-JSON + Codex app-server (no ACP found) | Desktop/headless split; remote UI over HTTP/WS | Small (1.4k★) |
| **opcode** (formerly Claudia, Tauri, AGPL) | GUI for Claude Code only | Claude CLI | Checkpoints, usage | Last release 2025-08 |
| **tmux managers**: uzi (abandoned 2025-06), cmux (Ghostty-based terminal, GPL/BUSL) | Worktree + tmux; terminal with agent notification rings | tmux / terminal | Simple | Not orchestrators |
| Also seen: Maestro (AGPL, playbooks), Xum/Mux (its own agent loop), Happy (encrypted mobile), Terragon (shut down) | | | | |

### Integration surfaces besides ACP

- **Codex app-server** (`openai/codex` `codex-rs/app-server/`; https://learn.chatgpt.com/docs/app-server).
  `codex app-server` speaks JSON-RPC over stdio JSONL; WebSocket and Unix socket are
  experimental. Thread status is `notLoaded | idle | systemError | active`, where `active` carries
  `activeFlags` such as `waitingOnApproval`. Approvals are server→client requests. The schema is
  generated per Codex version (`generate-ts`, `generate-json-schema`). The docs call it
  "experimental and not supported for production workloads". Used by Vibe Kanban, `codex-acp`,
  Jean and AionUi.
- **Claude Agent SDK** (https://code.claude.com/docs/en/agent-sdk/overview): TypeScript and Python
  only; no Kotlin or Java SDK (`anthropics/claude-agent-sdk-kotlin|java` return 404). For other
  languages the docs recommend the CLI with `-p` and JSON output. Orchestrators use
  `--input-format stream-json` with the `control_request` / `control_response` protocol (Vibe
  Kanban, Sculptor, AionUi).

---

## 4. ACP — Agent Client Protocol

Spec: https://agentclientprotocol.com; repository `agentclientprotocol/agent-client-protocol`
(formerly `zed-industries/…`), Apache-2.0, `schema-v1.26.0` / `v1.12.0` (2026-10-16). The stable
wire protocol version is integer `1`; **v2 is alpha** (`schema-v2.0.0-alpha.9`).

**Transport.** JSON-RPC 2.0 over **stdio**: the client starts the agent as a subprocess and they
exchange newline-delimited UTF-8 messages. stdout carries only ACP; stderr is for logs. Streamable
HTTP is a draft proposal (`docs/protocol/v1/transports.mdx`).

**Lifecycle** (`docs/protocol/v1/{initialization,session-setup,prompt-turn}.mdx`):
1. `initialize {protocolVersion, clientCapabilities, clientInfo}` → the chosen version,
   `agentCapabilities`, `authMethods`.
2. `authenticate` when needed; optional `logout`.
3. `session/new {cwd, mcpServers[]}` → `sessionId`.
   - `session/load` (capability `loadSession`) **replays the whole history** as `session/update`
     notifications.
   - `session/resume` restores without replay.
   - `session/list` and `session/delete` are optional capabilities.
4. `session/prompt` with content blocks. The agent streams `session/update` and answers with a
   `stopReason`: `end_turn | max_tokens | max_turn_requests | refusal | cancelled`.
5. `session/cancel` is a notification. The client must answer every pending permission request
   with `cancelled`.

**`session/update` variants (v1):** `user_message_chunk`, `agent_message_chunk`,
`agent_thought_chunk`, `tool_call`, `tool_call_update`, `plan`, `available_commands_update`,
`current_mode_update`, `config_option_update`, `session_info_update`, `usage_update`, `notice`,
`compaction_*`. A tool call has a `kind` and a status `pending | in_progress | completed | failed`;
its content can carry diffs and terminals.

**Agent → client requests:**
- `session/request_permission` — the only mandatory client method. Option kinds are
  `allow_once | allow_always | reject_once | reject_always`.
- `fs/read_text_file`, `fs/write_text_file`.
- `terminal/create | output | wait_for_exit | kill | release`.
- `elicitation/create` (structured questions).

Client → agent extras: `session/set_mode`, `session/set_config_option`. MCP servers are passed
through in `session/new`. Extensibility goes through `_meta` fields and custom methods prefixed
with `_`. Every capability is optional, and omitted means unsupported.

**v2 draft** (`docs/protocol/v2/draft/prompt-lifecycle.mdx`). Adds
`state_update: running | requires_action | idle` with a stop reason, and `subagent_update`.
`session/prompt` returns when the message is accepted — "a successful prompt response is not an
`idle` signal".

**Mapping to Octo status** (*Assessment*, derived from the spec):

| Octo | ACP v1 | ACP v2 draft |
| --- | --- | --- |
| working | `session/prompt` in flight, nothing pending | `running` |
| blocked | `session/request_permission` / `elicitation/create` (or an agent extension such as `cursor/ask_question`) pending | `requires_action` |
| idle | prompt returned `end_turn` (or `max_*`) | `idle` |
| failed | JSON-RPC error, process exit, `refusal` | `idle` + error stop reason |

**SDKs** (all under `github.com/agentclientprotocol`):

| SDK | Version | Notes |
| --- | --- | --- |
| Kotlin `com.agentclientprotocol:acp` | 0.34.0 (2026-10-21) | **JVM only**; JDK 21, Kotlin 2.2.20+; stdio client and agent; optional Ktor transports; `model/v2` package; 97★ |
| Rust `agent-client-protocol` | 3.5.0 | Used by Zed |
| TypeScript `@agentclientprotocol/sdk` | 2.0.2 | |
| Python | 0.12.1 | |
| Java | 0.80.0 | |

There is also an ACP test compatibility kit (`docs/libraries/testing.mdx`).

**Gaps for Octo.** ACP has no worktree or session-group concept (the client owns `cwd`), no status
field in v1, a costly `session/load` replay, and agent-specific permission semantics.

---

## 5. Comparison table

| | T3 Code | herdr | AionUi | Emdash | Claude Squad | Conductor | Vibe Kanban | OpenHands Canvas |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Form | Node server + Electron/web/mobile | Rust TUI, server/client | Electron + Rust backend | Electron | Go TUI | Native macOS app | Rust + web | Web/Electron + Python server |
| License | MIT | Apache-2.0 | Apache-2.0 | Apache-2.0 | AGPL-3.0 | Proprietary | Apache-2.0 | MIT |
| Stars / latest | 30.4k / v0.1.2 | 46.0k / v0.9.5 | 33.8k / v2.2.2 | 6.1k / v1.2.9 | 8.7k / v1.0.20 | — / v0.92.0 | 28.5k / v0.1.44 (sunsetting) | 91.9k / v1.33.0 |
| Agent transport | SDKs, app-server, ACP | PTY + hooks | stream-json, app-server, ACP | PTY + hooks, ACP | tmux | Undocumented | stream-json, app-server, ACP | ACP adapters |
| Status source | Structured events | Screen manifests + hooks | Pure-reducer FSM | Hooks / ACP | Pane hash + strings | API `idle/working/errored` | Process lifecycle | Server state |
| Worktrees | Per thread | `worktree` commands | None | Per task | Per instance | Per workspace | Per workspace | Sandboxes |
| Diff review | Checkpoints on hidden refs, PR review | None | Preview only | Yes, PRs, CI | git diff tab | Yes, PRs, checks | Inline comments | — |
| Live terminal of agent | No (separate terminal) | Yes (the core) | No | Yes (PTY mode) | Yes | — | Terminal per workspace | — |
| Remote | Relay, LAN, Tailscale, SSH, mobile | SSH | WebUI, mobile, chat | SSH/SFTP | — | Cloud, iOS, API | Relay, SSH | Multi-backend |
| Extensions | Provider packages, ACP registry, MCP | TOML plugins | JSON extension manifest | Plugin registry, MCP | Profiles | Scripts, API | MCP | Automations |
| Agent-to-agent | MCP server | CLI/socket | Team Mode (MCP) | — | — | — | MCP | — |

---

## 6. Driving the target agents

Versions are from the vendors' release pages and registries on 2026-10-26. No binary was run
locally, so flag spellings come from docs. They must be re-checked with `--help` before each
adapter is implemented.

### 6.1 Claude Code — `claude` v2.1.310

Sources: https://code.claude.com/docs/en/{cli-reference,headless,hooks,sessions,authentication,env-vars,terminal-config,legal-and-compliance}.md;
https://github.com/anthropics/claude-code/releases/tag/v2.1.310.

- **Headless.** `-p`; `--output-format stream-json --verbose`; `--input-format stream-json`;
  `--include-partial-messages`; `--model`.
  - Permissions: `--permission-mode default|acceptEdits|plan|auto|dontAsk|bypassPermissions`,
    `--allowedTools` / `--disallowedTools`, `--permission-prompt-tool mcp__<server>__<tool>`.
  - Limits and config: `--max-turns`, `--max-budget-usd`, `--mcp-config`, `--settings`.
  - Since v2.1.302, `-p` starts in Manual mode, and a call that needs approval is denied unless a
    hook or the permission-prompt tool approves it.
- **Stream** (NDJSON). Types `system` (subtypes include `init`, `status`, `api_retry`,
  `session_state_changed` …), `assistant`, `user`, `stream_event`, `result`, `rate_limit_event`.
  `result.subtype` is `success | error_max_turns | error_during_execution | error_max_budget_usd …`,
  with `session_id`, `total_cost_usd` and `usage`.
- **Status signal.** `system/session_state_changed` with `state` = `idle | running |
  requires_action`, emitted only when `CLAUDE_CODE_EMIT_SESSION_STATE_EVENTS=1`.
- **Bidirectional.**
  - SDK: `canUseTool` (also delivers `AskUserQuestion`).
  - Raw CLI: `control_request` / `control_response` envelopes over stdin. The full raw schema is
    **partly unverified**.
  - Alternatives: an Octo-served MCP permission tool (the Crystal pattern) or a `PermissionRequest`
    hook.
- **SDK.** TypeScript `@anthropic-ai/claude-agent-sdk` 0.3.310 and Python `claude-agent-sdk`
  0.2.174 only.
- **ACP.** Adapter `@agentclientprotocol/claude-agent-acp` 0.97.0, built on the TypeScript SDK;
  needs Node.
- **Hooks.** 33 events (`SessionStart`, `PreToolUse`, `PermissionRequest`, `Notification`
  (`permission_prompt`, `idle_prompt`, `agent_needs_input` …), `Stop`, `StopFailure`,
  `SessionEnd` …). Without `--bare`, `-p` runs **project hooks and `.mcp.json` even in untrusted
  folders**.
- **TUI fallback.** OSC 7501 session status (working / waiting / done) since v2.1.295, not under
  tmux; BEL notifications.
- **Auth.** Subscription OAuth (macOS Keychain, else `~/.claude/.credentials.json`) or
  `ANTHROPIC_API_KEY` (always used in `-p` when set). **`CLAUDE_CONFIG_DIR`** relocates
  everything; on macOS the Keychain entry is keyed to the config dir.
- **Sessions.** `~/.claude/projects/<encoded cwd>/<session-id>.jsonl` (internal format, 30-day
  retention). Flags: `--resume <id>`, `--continue`, `--session-id <uuid>`, `--fork-session`. On
  resume the permission mode resets.
- **ToS.** OAuth is for ordinary individual use. Developers building products "should use API key
  authentication". Anthropic "does not permit third-party developers to offer Claude.ai login … or
  to route requests through Free, Pro, or Max plan credentials on behalf of their users". "Claude
  Code" must not appear in a product name.

### 6.2 Codex CLI — `codex` 0.164.0

Sources: https://github.com/openai/codex (release `rust-v0.164.0`, `codex-rs/exec/src/exec_events.rs`,
`codex-rs/app-server/README.md`); https://learn.chatgpt.com/docs/{non-interactive-mode,app-server,hooks,auth,codex-sdk}.

- **Headless.** `codex exec` with `-C/--cd`, `-m`, `-s read-only|workspace-write|danger-full-access`,
  `-a/--ask-for-approval`, `--json`, `-o`, `--output-schema`, `--ephemeral`,
  `--skip-git-repo-check`, `--ignore-user-config`. `--full-auto` is gone.
- **exec JSONL.** `thread.started`, `turn.started|completed|failed`, `item.started|updated|completed`
  (items: `agent_message`, `reasoning`, `command_execution`, `file_change`, `mcp_tool_call`,
  `todo_list` …), `error`. exec **cannot answer approvals**.
- **Bidirectional: `codex app-server`.** See [§3](#integration-surfaces-besides-acp). Requests
  `thread/start|resume|fork`, `turn/start|steer|interrupt`. Approval requests
  `item/commandExecution/requestApproval`, `item/fileChange/requestApproval`,
  `item/tool/requestUserInput`, answered with `accept | acceptForSession | decline | cancel`. The
  protocol is experimental and pinned to the Codex version.
- **SDK.** TypeScript `@openai/codex-sdk` 0.164.0, Python `openai-codex`.
- **ACP.** Adapter `@agentclientprotocol/codex-acp` 2.3.0, which wraps app-server and bundles a
  Codex build (`CODEX_PATH` overrides it).
- **Hooks.** `SessionStart`, `PreToolUse`, `PermissionRequest`, `Stop`, `Interrupt` … in
  `hooks.json` / `config.toml`, behind hash-based trust. Legacy `notify` sends
  `agent-turn-complete`.
- **TUI.** OSC 9 / BEL notifications, `--no-alt-screen`.
- **Auth.** ChatGPT sign-in or API key; `$CODEX_HOME/auth.json` or the keyring. **`CODEX_HOME`**
  relocates all state. The docs recommend API keys for automation. No explicit clause on
  third-party driving was found (**unconfirmed either way**).
- **Sessions.** `$CODEX_HOME/sessions/YYYY/MM/DD/rollout-*.jsonl`; `codex exec resume
  [--last|<id>]`, `codex fork`.

### 6.3 Cursor — `agent` (alias `cursor-agent`) 2026.10.22

Sources: https://cursor.com/docs/cli/{overview,headless,acp,reference/parameters,reference/output-format,reference/configuration,reference/permissions};
https://cursor.com/docs/sdk/typescript; https://cursor.com/docs/hooks.

- **Headless.** `-p`; `--output-format text|json|stream-json`; `--stream-partial-output`; `--force`
  (without it edits are only proposed); `--model`; `--resume [chatId]`; `--continue`;
  `--workspace <path>`; `--mode plan|ask`; `--sandbox`; `--trust`; `-w/--worktree`.
- **Stream.** `system/init`, `user`, `assistant`, `tool_call` (`started|completed`),
  `result/success`. No error event and no usage; failure shows only as the exit code and stderr.
- **Bidirectional: native ACP** (`agent acp`). `authenticate` with `cursor_login`;
  `session/new|load`; `session/request_permission` (an unanswered request blocks tools). Modes
  `agent | plan | ask`. Extensions `cursor/ask_question`, `cursor/create_plan` (blocking),
  `cursor/update_todos`, `cursor/task`. Team MCP servers are not supported over ACP.
- **SDK.** `@cursor/sdk` 1.0.40 (Node 22.13+): local and cloud agents, `Agent.resume(id)`. Local
  agents run tools **without approval prompts**; gating is through hooks only. There is also a
  Cloud Agents REST API (beta, cloud only).
- **Hooks.** `sessionStart`, `preToolUse`, `beforeShellExecution`, `stop` …; the docs confirm only
  `workspaceOpen` in the CLI (the rest **unverified**). No idle notification.
- **Auth.** `agent login` (browser) or `CURSOR_API_KEY` (recommended for automation).
  **`CURSOR_CONFIG_DIR`** relocates config. Credential and chat storage paths are undocumented.
- **Watch item.** The install script mentions a "switch to Grok agent host" (`grok-switch.json`);
  meaning **unverified**.

### 6.4 Grok — official **Grok Build** `grok` 1.0.70 (xAI)

Sources: https://docs.x.ai/build/overview, https://docs.x.ai/build/cli/headless-scripting,
https://docs.x.ai/build/features/hooks; repo `xai-org/grok-build` (Apache-2.0) docs
`crates/codegen/xai-grok-pager/docs/user-guide/*`; npm `@xai-official/grok` 1.0.70.

- **Official vs community.** The official CLI is xAI's Grok Build. The community
  `superagent-ai/grok-cli` (npm `grok-dev` 1.1.7, API key only) installs a binary with the same name
  and uses the same `~/.grok` directory: a **collision risk**. Octo should target the official CLI.
- **Headless.** `-p/--single`; `--cwd`; `-m`; `-s/--session-id`; `-r/--resume`; `-c`;
  `--fork-session`; `--output-format plain|json|streaming-json|streaming-messages-json`;
  `--permission-mode`; `--allow` / `--deny` rules; `--max-turns`; `--sandbox`; `--no-auto-update`.
  It also accepts Claude Code flag aliases. Headless mode does not read the prompt from stdin.
- **Streams are read-only.** `streaming-json` (`text`, `thought`, `tool_call`, `usage`, `plan`,
  `end`, `error`) or the Claude-like `streaming-messages-json`. The docs say: "Tool approvals and
  other bidirectional flows use the ACP interface (`grok agent`)".
- **ACP (native).** `grok agent stdio`; `authenticate` with `xai.api_key` or `cached_token`.
  `session/request_permission` is not explicitly documented (**unverified**).
- **Hooks.** Include `Notification` (`idle_prompt`, `permission_prompt`, `task_complete`), `Stop`,
  `StopFailure`. It also reads `.claude/settings.json` and `.cursor/hooks.json`. Project hooks need
  trust.
- **Auth.** `grok login` (OAuth, device code available) or `XAI_API_KEY`; tokens in
  `~/.grok/auth.json`. **`GROK_HOME`** relocates everything. Whether a consumer subscription works
  is **unverified**.
- **Sessions.** `~/.grok/sessions/<encoded-cwd>/<id>/` (`updates.jsonl` holds the ACP updates);
  `grok sessions list|search|delete`, `grok export`.

### 6.5 Antigravity — `agy` ~1.4.x (Google)

Sources: https://antigravity.google/docs/{getting-started,cli/headless,cli/reference,permissions,hooks,cli/conversations,cli/gcli-migration,sdk/overview,faq};
ACP registry `antigravity-acp/agent.json`.

- **Surfaces.** A desktop App, the CLI `agy`, IDE extensions, and a Python SDK
  (`google-antigravity`). It keeps compatibility with Gemini CLI configuration; Gemini CLI itself
  remains ACP-capable (`--acp`).
- **Headless.** `-p`; `--output-format text|json|stream-json`; `--input-format stream-json`;
  `--model`; `--effort`; `-c`; `--conversation <id>`; `--dangerously-skip-permissions`;
  `--print-timeout` (default 5 min); `--sandbox`; `--mode`.
- **Output.** The JSON `status` field is `SUCCESS | ERROR | CANCELED | INTERRUPTED | INVALID |
  WAITING | RUNNING`. `stream-json` emits `init`, `step_update` (`state ACTIVE|DONE`, `step_type`)
  and `result`.
- **No approvals over stdin.** `control_request` input exits with code 2. A tool denied for lack of
  approval still exits 0 with a stderr notice.
- **Bidirectional.** The Python SDK has approval flows. The ACP server (`agy_acp_server`, a
  proprietary binary, registry v1.4.3, macOS arm64/x64 and Linux x86_64; **Windows not listed**)
  has no protocol documentation (**unverified**). AionUi's approach: one process per turn plus a
  `PreToolUse` hook that calls back to the host.
- **Hooks.** `SessionStart`, `PreToolUse` (decisions `allow`, `auto_approve`, `deny`, `ask` …),
  `PostToolUse`, `Stop` (with `fullyIdle`). No idle or waiting notification.
- **Auth.** Cached interactive login in the OS keyring, or a Gemini API key / Vertex. **No
  config-dir variable**; settings live in `~/.gemini/antigravity-cli/settings.json`.
- **Sessions.** Scoped to the working directory; `-c`, `--conversation <id>`, `/fork`. The storage
  path is undocumented.
- **ToS** (FAQ): "Wrapping `agy` inside third-party coding agents or applications … is not
  supported under consumer plans", and "Using third-party software, tools, or services to access
  Antigravity is a violation of our Terms of Service". It recommends Gemini Enterprise or AI Studio
  API keys.

### 6.6 Summary

| Agent | Best control channel | Fallback | Status signals | Resume | Test isolation | ToS note |
| --- | --- | --- | --- | --- | --- | --- |
| Claude Code | ACP adapter (`claude-agent-acp`, needs Node) **or** `-p` stream-json + control protocol | PTY + hooks / OSC 7501 | `session_state_changed`, `Notification` hook, `result` | `--resume <id>` / `session/load` | `CLAUDE_CONFIG_DIR` | No brokering of subscription credentials |
| Codex | `codex app-server` **or** ACP adapter `codex-acp` | `exec --json` (no approvals) | `thread/status/changed` + `waitingOnApproval`, `turn/completed` | `thread/resume`, `exec resume` | `CODEX_HOME` | API key recommended for automation |
| Cursor | Native ACP `agent acp` | `-p` stream-json | ACP permission requests, `cursor/ask_question` | `session/load`, `--resume` | `CURSOR_CONFIG_DIR` | API key recommended |
| Grok Build | Native ACP `grok agent stdio` | `-p` streaming-json (read-only) | ACP; `Notification` hook | `-r`, ACP | `GROK_HOME` | Subscription use unverified |
| Antigravity | ACP binary (undocumented) **or** per-turn `agy -p` + hook callback | stream-json | `step_update`, JSON `status`, `Stop.fullyIdle` | `--conversation <id>` | `HOME` only (keyring caveat) | Consumer-plan wrapping not supported |
| Kimi Code | Native ACP `kimi acp` | `-p` stream-json (read-only) | ACP permission requests | `session/load`, `session/resume`, `-S <id>` | `KIMI_CODE_HOME` | Third-party use documented; keep the User-Agent |
| MiMo Code | Native ACP `mimo acp` | `mimo run --format json` | ACP permission requests | `session/load`, `session/resume`, `--session <id>` | `XDG_*` dirs or `MIMOCODE_HOME` | Use Restrictions; MiMo Terms of Service for hosted models |
| ZCode | Per-turn `zcode --prompt … --output-format stream-json` | `app-server` (desktop-internal, undocumented) | `turn.completed` / `turn.failed`, closing `result` line | `--resume <sessionId>`, `--continue` | `HOME`, `ZCODE_DATA_BASE_DIR` | Apache-2.0 client; no third-party-use note found |

*Assessment.* The base adapter should implement an ACP client plus a stream-JSON process driver.
The Claude and Codex adapters choose between ACP adapters (which add Node as a runtime dependency)
and their native protocols; that choice belongs to the architecture review. The status reducer must
accept "blocked" from three kinds of source: ACP pending requests, native protocol flags, and hook
callbacks.

### 6.7 Kimi Code — `kimi` 2.3.3 (Moonshot AI)

Sources (read 2026-10-27): repo `MoonshotAI/kimi-code` (MIT, release `@moonshot-ai/kimi-code@2.3.3`)
files `docs/en/reference/kimi-acp.md`, `docs/en/reference/kimi-command.md`,
`apps/kimi-code/src/cli/sub/acp.ts`, `apps/kimi-code/src/cli/commands.ts`,
`apps/kimi-code/src/constant/app.ts`, `packages/acp-server/src/{server,approval}.ts`;
https://www.kimi.com/code/docs/en/kimi-code-cli/reference/kimi-command; a live `kimi acp`
`initialize` from `kimi` 0.39.1 with `KIMI_CODE_HOME` pointed at a temp dir.

- **Two CLIs, one binary name.** The Python `MoonshotAI/kimi-cli` (1.x, last release 1.52.0) is
  archived and points to Kimi Code. Both install `kimi`. Kimi Code prints a bare version
  (`kimi -V` → `2.3.3`, commander `.version()`); the legacy CLI prints `kimi, version 1.52.0`
  (typer callback). Kimi Code versions went 0.43.1 → 2.0.0, so 0.x is Kimi Code too.
- **Install.** Official install script (binary in `~/.kimi-code/bin/kimi`), Homebrew, npm
  `@moonshot-ai/kimi-code`.
- **Headless.** `-p/--prompt`; `--output-format text|stream-json` (only with `--prompt`);
  `-S/--session [id]`, `-c/--continue`; `-m/--model`; `--yolo`, `--auto`, `--plan` (none of them
  allowed with `--prompt`); `--add-dir`; `--agent`, `--agent-file`. `stream-json` writes assistant
  and tool messages as JSONL; thinking is not written. Read-only: no approvals.
- **ACP (native).** `kimi acp`, JSON-RPC over stdio, no banner, logs on stderr. Capabilities:
  `loadSession`, image and embedded-context prompts, `session/{list,resume,close,delete,fork}`,
  `additionalDirectories`, HTTP and SSE MCP forwarding (stdio MCP servers are forwarded too),
  `auth.logout`. Methods: all of core and `session/*`, `session/request_permission` (options
  `allow_once`, `allow_always`, `reject_once`), `fs/*` and `terminal/*` reverse calls only when the
  client advertises them, `elicitation/create` with a fallback to `request_permission`,
  `session/set_mode`, `session/set_config_option`, `session/set_model`.
- **Auth.** `kimi login` (device code; `kimi acp --login [--region mainland-cn|global]` is the ACP
  terminal-auth entry). `initialize` advertises one method, `login` (`type: terminal`).
  `authenticate("login")` only re-checks the stored token; gated calls return `auth_required`
  (-32000) while it is missing. Octo can retry with `login` safely: it never starts a sign-in.
- **Config dir.** `KIMI_CODE_HOME` (default `~/.kimi-code`) holds config, tokens, sessions and
  logs.
- **Terms.** Kimi's help pages document using a Kimi Code membership with third-party tools and
  warn against altering the client's User-Agent (https://www.kimi.ai/help/kimi-code/third-party-agents,
  read 2026-10-27). Octo runs the user's own `kimi`, so its User-Agent is untouched. Claims that
  membership keys may not be used for automation come only from an unofficial blog
  (**unverified**).

### 6.8 MiMo Code — `mimo` 0.1.16 (Xiaomi)

Sources (read 2026-10-28): repo https://github.com/XiaomiMiMo/MiMo-Code (MIT, release `v0.1.16`)
files `README.md`, `USE_RESTRICTIONS.md`, `packages/cli/package.json`,
`packages/cli/src/cli/cmd/{acp,run}.ts`, `packages/cli/src/acp/agent.ts`; npm `@mimo-ai/cli`
0.1.16 (`bin: { mimo }`, `engines.node >=20.10.0`); vendor page
https://mimo.mi.com/docs/en-US/tokenplan/integration/mimo-code (updated 2026-10-27).

- **What it is.** A terminal coding agent forked from OpenCode; the ACP server still reports
  `agentInfo.name: "OpenCode"` with MiMo's own version.
- **Install.** `curl -fsSL https://mimo.xiaomi.com/install | bash` (macOS, Linux), a PowerShell
  script, or `npm install -g @mimo-ai/cli`. The binary is `mimo`; `mimo --version` prints a bare
  version. The unrelated npm package `mimocode` installs `mimocode`, not `mimo`.
- **Headless.** `mimo run [message..]` with `--format default|json` (raw JSON events),
  `--session`, `--continue`, `--fork`, `--model provider/model`, `--agent`, `--file`,
  `--dangerously-skip-permissions`; `mimo serve` runs an HTTP server.
- **ACP (native).** `mimo acp [--cwd]`, NDJSON JSON-RPC over stdio. `initialize` advertises
  `loadSession`, image and embedded-context prompts, HTTP and SSE MCP, and
  `sessionCapabilities.{fork,list,resume}`. `session/new` returns `configOptions` with a `model`
  select and `modes`. Updates used: `agent_message_chunk`, `agent_thought_chunk`, `tool_call`,
  `tool_call_update`, `usage_update`, `available_commands_update`; turns end with `end_turn`.
- **Auth.** OAuth to the Xiaomi MiMo platform (`mimo auth login`, or `/connect` and `/login` in the
  TUI), Codex (ChatGPT) OAuth, an import from Claude Code, catalog providers by API key, or a custom
  OpenAI-compatible provider. The ACP `authMethods` entry `opencode-login` is a terminal sign-in,
  and `authenticate` throws "Authentication not implemented", so Octo sends no `authenticate`.
- **Config dir.** `~/.config/mimocode/mimocode.jsonc` and `.mimocode/` per project; data under the
  XDG data dir (`~/Library/Application Support/mimocode/` on macOS) with `auth.json`, or under
  `MIMOCODE_HOME`.
- **Terms.** MIT client plus `USE_RESTRICTIONS.md`, which forbids, among others, using it to
  autonomously execute high-risk actions without human oversight. Hosted models fall under the MiMo
  Terms of Service (https://platform.xiaomimimo.com/docs/terms/user-agreement). The vendor page
  states no rule on which clients may use a Token Plan.

### 6.9 ZCode — `zcode` 3.16.2 (Z.ai)

Sources (read 2026-10-28): repo https://github.com/zai-org/ZCode (Apache-2.0, commit `3c5b227`,
README "Updated to ZCode v3.16.2", root `package.json` 3.16.2) files `README.en.md`,
`apps/zcode-cli/docs/usage/{01-quickstart,02-cli-reference,03-config,04-tools,05-permissions}.md`,
`apps/zcode-cli/packages/cli/src/{prompt-command,run}.ts`,
`apps/zcode-cli/packages/cli/scripts/build.mjs`,
`apps/zcode-cli/packages/bootstrap/src/zcode-protocol/session-mapper.ts`,
`apps/zcode-cli/packages/contracts/src/events/session.events.ts`; GitHub releases (latest
`v3.14.3`: desktop `.dmg` and `.exe` only).

- **Install.** No official registry package: build from source (`pnpm build:zcode` for the
  distribution with one `zcode` command, or `pnpm --filter @zcode/cli... build` for the bare
  `zcode.cjs`, optional single-file SEA builds). The npm packages `zcode` and `zcode-cli` link no
  repository and Z.ai's docs never mention them, so Octo treats them as unofficial. `zcode --version` prints the root package version.
- **Headless.** `zcode --prompt <text>` (also `-p`; needs non-empty text) with `--cwd`,
  `--mode plan|build|edit|yolo|guarded` (defaults to `yolo` with `--prompt`), `--attach <path>`,
  `--resume <sessionId>` or `--continue`, `--disallowed-tools`, `--output-format
  text|json|stream-json`, `--no-color`. Options parse with `node:util` `parseArgs`, so
  `--name=value` works. `stream-json` is NDJSON: one session event per line
  (`{eventId, type, sessionId, turnId, payload, seq, timestamp, traceId}`), then a closing line
  `{type: "result", sessionId, response, usage, projection}`. Event types used: `model.streaming`
  (`payload.kind` `text_delta` or `reasoning_delta` with `delta`), `tool.updated` (`payload.kind`
  `scheduled`, `started`, `progress`, `result` with `result.success` and `result.content`, `error`),
  `turn.completed` (`resultType`), `turn.failed` (`error.message`). The docs ask consumers to keep
  unknown event types and not to rely on event counts.
- **Approvals.** A `--prompt` run has no approval client: a tool that would ask fails and the
  model is told. `plan` blocks writes, `build` needs approval for writes and side effects,
  `edit` allows workspace edits, `guarded` allows routine calls and still asks for dangerous ones,
  `yolo` skips prompts. The docs suggest removing `AskUserQuestion`, `EnterPlanMode` and
  `ExitPlanMode` from headless runs.
- **Protocol server.** `zcode app-server` speaks the ZCode Protocol (NDJSON frames) to the desktop
  host; the CLI docs call it a protocol entry, not a batch interface, and it has no public
  reference. No ACP server.
- **Auth.** `zcode login` (`--no-browser` prints the URL), a coding-plan API key, or a personal
  provider in `~/.zcode/v2/provider_config.json`.
- **Config dir.** `~/.zcode/v2/provider_config.json` (models), `~/.zcode/cli/config.json`
  (runtime, permissions, MCP, hooks, plugins); `ZCODE_DATA_BASE_DIR` moves the data base. Isolated
  runs set `HOME`, `USERPROFILE` and `ZCODE_DATA_BASE_DIR` together.
- **Terms.** The client is Apache-2.0; the repository has no note on third-party front ends. Use
  of Z.ai models falls under Z.ai's own terms (not checked here, **unverified**).

---

## 7. Kotlin platform facts for the UI-target decision

Sources: https://kotlinlang.org/docs/multiplatform/{supported-platforms,faq,compose-compatibility-and-versioning,compose-native-distribution,multiplatform-hierarchy,multiplatform-compatibility-guide}.html;
https://kotlinlang.org/docs/native-target-support.html; YouTrack CMP-1923, CMP-2825, CMP-9572,
CMP-5100, SKIKO-580; Maven Central `.module` files; GitHub release APIs.

**Versions (stable).** Kotlin 2.4.21 (2026-10-08), Compose Multiplatform 1.12.2 (2026-10-15;
1.13.0-beta01 is out), Gradle 9.8.2. KGP 2.4.21 documents Gradle support only **up to 9.7.0**.

**Compose Multiplatform.**
- Desktop (JVM) is Stable: "Compose Multiplatform targets the JVM" for desktop. Requirements: macOS
  13 arm64, Windows 10, Ubuntu 20.04; JDK 17+ for packaging.
- Native desktop is not planned: CMP-1923 *Not Planned* (2026-03-20), the native research CMP-2825
  *Obsolete*.
- Maven has `macosArm64` klibs for `ui` / `foundation`, but no documented desktop target. There are
  no Linux or Windows native UI variants, and Skiko has no `mingwX64` artifact.
- The unofficial `brahmkshatriya/compose-native` fork (Linux/Windows native) is alpha: 18★, 2.5
  months old.
- Packaging uses jpackage/jlink with a self-contained runtime (`.dmg`, `.pkg`, `.msi`, `.exe`,
  `.deb`, `.rpm`). **No cross-compilation**: each OS builds its own package.
- AppCDS/AOT startup work landed only in 1.13 pre-releases. GraalVM native-image is not supported
  (SKIKO-580 is open). Memory use is an open ticket (CMP-5100) with no official numbers.

**Kotlin/Native desktop tiers.** `macosArm64` Tier 1, `linuxX64` Tier 2, `mingwX64` **Tier 3**
("not in active development and may come with breaking issues").

**Pure Kotlin/Native GUI alternatives.**

| Option | Status |
| --- | --- |
| AppKit via cinterop | macOS only |
| gtk-kn | Linux only; GitHub mirror archived, no releases |
| kotlin-libui | Dormant since 2020 |
| imgui-kmp | 2★, 2 months old |
| `JetBrains/kotlin-desktop-toolkit` | JVM today; "internal JetBrains project" |

No maintained cross-platform option exists.

**KMP core libraries** covering `jvm` + `macosArm64` + `linuxX64` + `mingwX64`:

| Library | Version |
| --- | --- |
| kotlinx.coroutines | 1.11.0 |
| kotlinx.serialization | 1.12.0 |
| kotlinx-io | 0.9.1 |
| Okio | 3.18.2 |
| SQLDelight runtime and native driver | 2.4.1 |
| Ktor client/server core | 3.6.0 |
| Koin | 4.2.2 |

Room KMP 2.8.5 has **no `mingwX64`**. SQLDelight is SQL-first (`.sq` files); whether it satisfies
the workspace "no raw SQL" rule is an architecture question.

**PTY and terminal.**

| Library | Platform | Status |
| --- | --- | --- |
| pty4j (EPL-1.0) | JVM | Mature; 0.14.0; ConPTY on Windows |
| JediTerm (LGPLv3 or Apache-2.0) | JVM, Swing UI | Mature; 3.76; not on Maven Central |
| BossTerm (LGPL-3.0) | JVM, Compose terminal | Single author |
| kmp-terminal-emulator | KMP, Compose UI | 4★; no Windows native |
| KJPty | Kotlin/Native PTY | 0★, no license |

On Kotlin/Native, `forkpty` needs a custom `.def` (`pty.h` / `util.h` are not in the platform
libs), and ConPTY likely needs custom cinterop (inferred from `mingw/windows.def`, not compiled).
Process libraries:

| Library | Platforms | Status |
| --- | --- | --- |
| kmp-process | No Windows native | 54★ |
| ksubprocess | Linux, Windows, macOS; pipes only | 14★ |

**Git.** JGit 7.8.0 lists "support for multiple working trees (git-worktree)" under **Missing
Features**. libgit2 1.9.7 has worktree support but no KMP wrapper. Shelling out to `git` works the
same on every target.

**Lint.**
- ktlint: stable 1.8.0 predates Kotlin 2.4. The 2.0 alpha moved to `io.github.ktlint` (Pinterest no
  longer maintains it).
- detekt: stable 1.23.8 predates Kotlin 2.4. 2.0.0-alpha.6 (`dev.detekt`) is built against
  Kotlin 2.4.10 and handles KMP.

**Options for the architecture review** (facts above; no recommendation yet):

| Option | Shape | Main trade-off |
| --- | --- | --- |
| A | Compose Desktop (JVM) UI + KMP core (`jvm` + 3 native targets) + native headless daemon | Only stable UI path; the app still ships a JRE; the native daemon side lacks a mature PTY library |
| B | Everything on JVM | Simplest, mature PTY/terminal stack; drops "Kotlin Native" entirely |
| C | Pure Kotlin/Native with an alpha UI (compose-native fork, ImGui, or per-OS bindings) | True native binaries; no stable toolkit, Windows Tier 3, terminal widget built from scratch |
| D | Native UI per OS (SwiftUI, GTK, WinUI) over a Kotlin/Native core library | Native look; three UIs, not Compose |
| E | A now, keep the core native-ready for a future native Compose | Depends on JetBrains plans that are currently *Not Planned* |

---

## 8. Features to adopt, ranked

Ranked by value to Octo's MVP divided by implementation cost (*Assessment*). **core** = in the MVP,
works with every feature flag off. **optional** = behind a feature flag.

| # | Feature | Tag | Taken from |
| --- | --- | --- | --- |
| 1 | Adapter contract that emits **normalized events** and declares **capabilities**; the core never branches on the agent id; a missing capability uses a declared fallback | core | T3 Code, AionUi |
| 2 | Server-authoritative **status reducer** `step(state, event)` with ref-counted "requires action" (approval / question / auth) and states working / blocked / idle / failed | core | AionUi, T3 Code, ACP v2 |
| 3 | **ACP client in the base adapter** as the default transport; per-agent adapters add launch args, auth checks and extensions (`cursor/*`, xAI) | core | T3 Code, Emdash, Zed, OpenHands |
| 4 | Stream-JSON / JSON-RPC **process driver** in the base adapter for native protocols (Claude stream-json control, Codex app-server, `agy -p`) | core | Vibe Kanban, AionUi, Crystal |
| 5 | **Git worktree per task** with branch-name policy, base ref, setup script, `remove` never deletes the branch, dirty-tree guard | core | T3 Code, herdr, Conductor, Emdash |
| 6 | Central **permission and question broker**: a pending request makes the agent blocked, is persisted, survives restart, and is cancelled correctly on Stop | core | ACP, T3 Code |
| 7 | **App-owned session ids**; the agent session id is a reference; cold start = idle plus resume anchor; resume through capabilities (`session/load` / `resume` / `--resume`) | core | T3 Code, AionUi |
| 8 | **Status priority ladder and rollup** (Pending approval > Awaiting input > Working > Completed; blocked dominates; "done" = idle but unseen) with OS notifications | core | T3 Code, herdr |
| 9 | **Live output per agent**: a structured timeline from events plus a raw terminal view when the agent runs in a PTY | core | herdr, Emdash |
| 10 | **Diff review per task**: worktree vs base branch, file list, per-file diff | core | Conductor, Emdash, T3 Code |
| 11 | Send prompt, answer question, **stop / restart** with clean settlement of open tool calls and requests | core | T3 Code (documented traps) |
| 12 | **Supervised processes**: process-tree kill, orphan cleanup on start, single-instance lock, atomic state writes with rotated backups | core | AionUi, herdr |
| 13 | **Typed internal core API** (commands and an event stream) so the UI is a thin client and the core can run headless | core | T3 Code, herdr, Jean |
| 14 | **Replay-fixture adapter tests** recorded from real agents; config-dir isolation (`CLAUDE_CONFIG_DIR`, `CODEX_HOME`, `CURSOR_CONFIG_DIR`, `GROK_HOME`, `HOME`) | core | T3 Code |
| 15 | Persisted **layout and sessions**, restored on restart | core | herdr |
| 16 | **CLI version drift check**: record the verified agent version per adapter and warn on mismatch | core | AionUi |
| 17 | One-action **commit / push / open PR** | optional | T3 Code, Conductor, Emdash |
| 18 | **Screen-detection manifests** (data-driven regex rules with an `explain` tool) for agents without structured status | optional | herdr |
| 19 | **Hook-based status** for agents run as TUIs (installing hooks changes the user's agent config, so it is opt-in) | optional | Emdash, herdr |
| 20 | **Multi-model fan-out**: one prompt → N agents, each in its own worktree | optional | T3 Code |
| 21 | **Agent-to-agent**: Octo exposes an MCP server (spawn task, send message, wait for idle) | optional | T3 Code, herdr, AionUi |
| 22 | **Team presets**: leader / teammates, mailbox, task board, permission badges | optional | AionUi |
| 23 | **Remote machines over SSH** (ControlMaster reuse, remote core install) | optional | herdr, Emdash |
| 24 | **Mobile / remote access** to the core API (pairing, token auth) | optional | T3 Code, AionUi, Jean |
| 25 | **Plugins**: a declarative manifest with declared permissions and event hooks (`worktree.created`); fail open | optional | herdr, AionUi |
| 26 | **ACP registry** browser to add agents without code | optional | T3 Code, Zed |
| 27 | Checkpoints on **hidden git refs** with revert | optional | T3 Code |
| 28 | Thread triage: pin, snooze, settle | optional | T3 Code |
| 29 | Usage and limits view | optional | T3 Code |
| 30 | Idle suspend and wake of agent processes | optional | AionUi |

What **not** to copy: hard-coded English prompt strings (Claude Squad), parsers pinned to one CLI
version without a drift check (Vibe Kanban), event sourcing with dozens of migrations at MVP stage
(T3 Code), unsandboxed and unreviewed plugins (herdr).

---

## 9. Open questions and unverified items

**For the creator:**
1. **ToS.** Claude and Antigravity restrict third-party wrapping of consumer logins. Should Octo
   ship those adapters as "drives your own installed CLI under your own login", require an API key,
   or keep Antigravity behind a flag with the vendor's terms shown first?
2. **UI target.** Options A–E in [§7](#7-kotlin-platform-facts-for-the-ui-target-decision); to be
   decided in the architecture task.
3. **Node as a runtime dependency.** The Claude and Codex ACP adapters run through `npx`; the
   alternative is native protocols (Claude stream-json control, Codex app-server) at a higher
   maintenance cost.
4. **Lint toolchain.** Stable ktlint and detekt predate Kotlin 2.4; the 2.0 alphas support it.
5. **Persistence.** SQLDelight is SQL-first; check it against the "no raw SQL" rule, or choose
   another store.

**Unverified:**
- The full raw Claude stdin control-protocol schema.
- Antigravity ACP server methods and permission handling; Windows availability.
- `session/request_permission` in Grok's ACP mode.
- Which Cursor hooks fire in the CLI, and Cursor's "Grok agent host" switch.
- Conductor's transport.
- Paradev and agent-deck status mechanics.
- The Kotlin ACP SDK license (Apache-2.0 vs MIT).
- Whether consumer subscriptions work for Grok Build.
- All flag spellings until checked with `--help` on installed binaries.

---

## 10. Sources

Pinned commits (GitHub, read 2026-10-26):

- T3 Code — https://github.com/pingdotgg/t3code/tree/8e1bb03
- herdr — https://github.com/herdrdev/herdr/tree/5608de0
- AionUi — https://github.com/iOfficeAI/AionUi/tree/11cd2a9
- AionCore — https://github.com/iOfficeAI/AionCore/tree/503a871
- ACP spec — https://github.com/agentclientprotocol/agent-client-protocol/tree/1c0fe8dd68
- ACP registry — https://github.com/agentclientprotocol/registry (release `v2026.10.26-a14e6a6`)
- Claude Squad — https://github.com/smtg-ai/claude-squad/tree/ce1ffb4392
- Vibe Kanban — https://github.com/BloopAI/vibe-kanban/tree/a019cebf09
- Crystal — https://github.com/stravu/crystal/tree/1e18e0bc98
- OpenHands — https://github.com/OpenHands/OpenHands/tree/0ec412c9bc
- Emdash — https://github.com/generalaction/emdash/tree/85dfa910ac
- Others at their default branch: `zed-industries/zed`, `imbue-ai/sculptor`, `ParadevAI/Paradev`,
  `kbwo/ccmanager`, `asheshgoplani/agent-deck`, `coollabsio/jean`, `winfunc/opcode`,
  `devflowinc/uzi`, `manaflow-ai/cmux`, `RunMaestro/Maestro`, `coder/mux`, `slopus/happy`,
  `terragon-labs/terragon-oss`

Vendor docs (read 2026-10-26):

- Conductor — https://www.conductor.build/docs/ , /changelog , /pricing , /docs/api
- Vibe Kanban shutdown notice — https://www.vibekanban.com/blog/shutdown
- Zed external agents — https://zed.dev/docs/ai/external-agents
- OpenHands ACP agents — https://docs.openhands.dev/openhands/usage/agent-canvas/acp-agents
- Claude Code — https://code.claude.com/docs/en/ (cli-reference, headless, hooks, sessions,
  authentication, env-vars, terminal-config, legal-and-compliance, agent-sdk/overview,
  agent-sdk/typescript)
- Codex — https://learn.chatgpt.com/docs/ (non-interactive-mode, developer-commands, app-server,
  hooks, auth, codex-sdk)
- Cursor — https://cursor.com/docs/cli/ (overview, headless, acp, reference/*),
- Kimi Code (read 2026-10-27) — https://www.kimi.com/code/docs/en/kimi-code-cli/reference/kimi-command ,
  https://www.kimi.ai/help/kimi-code/third-party-agents ; repo https://github.com/MoonshotAI/kimi-code
  at `@moonshot-ai/kimi-code@2.3.3`
  https://cursor.com/docs/sdk/typescript , https://cursor.com/docs/hooks ,
  https://cursor.com/docs/cloud-agent/api/overview
- Grok Build — https://docs.x.ai/build/ (overview, cli/headless-scripting, features/hooks,
  settings/reference, cli/terminal-support)
- Antigravity — https://antigravity.google/docs/ (getting-started, cli/headless, cli/reference,
  permissions, hooks, cli/conversations, cli/gcli-migration, sdk/overview, faq, changelog)
- Kotlin and Compose — https://kotlinlang.org/docs/multiplatform/ and
  https://kotlinlang.org/docs/native-target-support.html ; YouTrack issues CMP-1923, CMP-2825,
  CMP-9572, CMP-5100, SKIKO-580

Added for T58 (read 2026-10-28):

- MiMo Code — https://github.com/XiaomiMiMo/MiMo-Code/tree/v0.1.16 ; npm `@mimo-ai/cli` 0.1.16 ;
  https://mimo.mi.com/docs/en-US/tokenplan/integration/mimo-code
- ZCode — https://github.com/zai-org/ZCode/tree/3c5b227649371507ddc4eb46ac5133afe6db1cab ; npm
  `zcode` 0.0.1 and `zcode-cli` 0.0.1 (look-alikes)

Registries (read 2026-10-26):

- npm — `@anthropic-ai/claude-agent-sdk`, `@agentclientprotocol/claude-agent-acp`,
  `@agentclientprotocol/codex-acp`, `@openai/codex`, `@openai/codex-sdk`, `@cursor/sdk`,
  `@xai-official/grok`, `grok-dev`
- PyPI — `claude-agent-sdk`, `openai-codex`, `google-antigravity`
- Maven Central — `com.agentclientprotocol:acp`, `org.jetbrains.compose.*`, `org.jetbrains.skiko`,
  `org.jetbrains.pty4j:pty4j`, `org.jetbrains.kotlinx:*`, `com.squareup.okio:okio`,
  `app.cash.sqldelight:*`, `io.ktor:*`, `io.insert-koin:koin-core`, `org.eclipse.jgit`,
  `io.github.ktlint.core:ktlint-cli`, `dev.detekt:detekt-cli`
- Google Maven — `androidx.room:room-runtime`
