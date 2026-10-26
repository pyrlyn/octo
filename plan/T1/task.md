---
status: in progress
priority: P0
complexity: 3
readiness: 5%
agent: Claude Code / claude-opus-5-5
---

# T1. Research agent orchestrators and target agents

Octo's architecture needs a fact base: what existing orchestrators do well, how they talk to
agents, and how each target agent can be driven programmatically. Done means `docs/research.md`
is merged after the creator's review, with:

1. An in-depth study of T3 Code (pingdotgg/t3code), herdr (herdrdev/herdr) and AionUi
   (iOfficeAI/AionUi): features, UX, architecture, agent integration, session model, remote
   access, plugin model.
2. A survey of other orchestrators (Claude Squad, Conductor, Vibe Kanban, Crystal, OpenHands,
   tmux-based managers, ACP and similar): what each does, how it talks to agents, strengths,
   weaknesses.
3. A comparison table and a ranked list of features to adopt, each tagged core or optional.
4. For Claude Code, Codex CLI, Cursor, Grok CLI and Antigravity: CLI flags, JSON / stream
   output, SDK, ACP, PTY, auth model, session resume, status detection (working / blocked / idle).
5. Facts for the UI-target decision: Compose Multiplatform desktop targets, Kotlin/Native UI
   options, PTY and process libraries for Kotlin/Native.

Every fact cites its primary source with the URL and the version or the date checked;
facts backed only by secondary sources are marked unverified.

## Execution plan

1. Read the three primary projects' repositories (README, docs, source layout, agent
   integration code) and record findings with sources.
2. Survey the other tools from their repositories and docs; read the ACP spec.
3. Read each target agent's official docs and CLI help for headless / streaming / resume modes.
4. Check Compose Multiplatform and Kotlin/Native docs for desktop targets and PTY options.
5. Write `docs/research.md`; open a PR on branch `t1-research`; stop for review.

Check: the PR adds `docs/research.md` covering items 1–5 with a source on every fact.
