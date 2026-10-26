# Roadmap

Approved work not yet in `plan/`, in order.

- **Architecture** — `docs/architecture.md`: Kotlin Multiplatform modules (`core`, `adapters:base`,
  `adapters:<agent>`, `ui`, `app`), internal core API so the core can run headless, base adapter,
  adapter registry, feature flags. Decide the UI target (Compose Multiplatform desktop runs on the
  JVM, not Kotlin/Native). Stop for review.
- **Project skeleton** — Gradle (Kotlin DSL) modules, base adapter, registry, feature-flag system,
  empty Compose UI, ktlint, detekt, CI.
- **Claude Code adapter** — with tests on recorded output.
- **Codex CLI adapter** — with tests on recorded output.
- **Cursor adapter** (CLI / SDK) — with tests on recorded output.
- **Grok CLI adapter** — with tests on recorded output.
- **Antigravity adapter** — with tests on recorded output.
- **MVP UI wired to core** — parallel agents in worktrees, live output view, status
  (working / blocked / idle / failed), prompts and answers, stop / restart, persisted sessions and
  layout, diff review per task.
- **Optional features behind flags** — commit / push / open PR in one action; remote machines over
  SSH; agent-to-agent messaging; team and assistant presets; mobile / remote access; plugins.
