# AGENTS.md — Octo

If an `AGENTS.md` or `CLAUDE.md` exists higher in the tree, follow it too; on conflict, ask the creator.

## What this project is

Octo is an open, extensible desktop orchestrator for CLI coding agents (Claude Code, Codex CLI,
Cursor, Grok CLI, Antigravity and more), written in Kotlin. It runs several agents in parallel,
each in its own git worktree, on the user's own subscriptions.

## Where things are

- `plan/` — tasks, one folder per task; `plan/README.md` holds decisions and reference.
- `roadmap.md` — approved work not yet in `plan/`.
- `ideas.md` — unapproved ideas.
- `docs/research.md` — Phase 1 research; `docs/architecture.md` — Phase 2 architecture.
- `toolchain.md` — programs and packages the project uses.

## Rules

- Code, docs, comments, commits and PR text in English.
- Every change goes through a PR. Do not push `main` directly and never force-push.
- Core and every adapter have tests. Adapters are tested against recorded or fake agent output;
  no real accounts or credentials in tests.
- Tests never touch the real `HOME`: point every agent's config directory at a temp dir.
- ktlint and detekt must be clean.
- Optional features sit behind a feature flag; the core works with every flag off.
- Third-party libraries are wrapped behind our own interfaces. A new third-party dependency
  needs the creator's approval.
- Adding an agent means adding an adapter module and registering it; no changes to core or UI.
