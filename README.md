# Octo

An open, extensible desktop orchestrator for CLI coding agents. One window to run, watch and
coordinate several agents in parallel, each in its own git worktree, using your own agent
subscriptions.

Status: early development.

Agents: Claude Code, Codex, Cursor, Grok Build, Kimi Code, MiMo Code, ZCode and, behind a flag,
Antigravity, each the CLI you installed and signed in to yourself. Plugins can add more.

## Quick start

Needs JDK 25 and the shared builds in the workspace's `packages/` (see [building](docs/building.md)).

```bash
./gradlew check        # build, tests, ktlint, detekt
./gradlew :app:run     # open the desktop app
```

To try Octo without installing an agent, turn on the demo agent in `config.json` in the Octo home
(`OCTO_HOME`, or the platform's app data folder):

```json
{ "flags": { "demo-agent": true } }
```

## Documentation

- [Building and running](docs/building.md): JDK, shared builds, Gradle tasks, Hot Reload, IDE setup.
- [Using Octo](docs/using-octo.md): agents, queued messages, permission modes, worktree setup.
- [Configuration](docs/configuration.md): every `config.json` key and feature flag.
- [Optional features](docs/features.md): publishing, messaging, presets, handoff, fork, compare,
  checkpoints, triggers, queue, notifications, plugins and more.
- [Remote machines](docs/remote-machines.md): tasks over SSH and moving tasks between machines.
- [Remote access and the `octo` CLI](docs/remote-access.md): the JSON API, guest links, the command line.
- [Plugins](docs/plugins.md): the WebAssembly plugin ABI.
- [Architecture](docs/architecture.md) and [research](docs/research.md); tasks live in `plan/`.
- [Contributing](docs/contributing.md): checks, formatting and CI workflows.
