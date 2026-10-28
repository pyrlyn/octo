---
status: in progress
priority: P2
complexity: 2
readiness: 90%
agent: Claude Code / claude-opus-5-5
---

# T39. Context panel on the task screen

The agents already report how full their context window is (`SessionStats.contextUsed` and
`contextWindow`), but the task screen does not show it, so a user cannot tell when a long
conversation is close to the limit. Claude Code shows this as a "Context" popup: percent used,
used / window, a bar and a breakdown. Octo gets the same on the task screen.

Done means:

- Behind the existing `usage` flag, the task screen has a "Context ▾" button next to the
  permissions menu. It shows the percent used, when the agent reports it.
- The popup shows percent used, used / window, a stacked bar, and one row per part of the
  latest request's context the agent reports (cache read, cache write, input, output) plus free
  space, each with tokens and percent of the window.
- Agents that report only used and window (ACP agents) show the bar and free space alone.
- Core, the Claude and Codex adapters and the view model have tests.

## Decisions

- Category breakdowns like Claude Code's `/context` (messages, tools, MCP, skills) are not in the
  agent's stream; getting them means running `/context` inside the session, which only Claude
  has and which is fragile. The creator chose the split the stream already carries.
- The parts of one request add up to `contextUsed`: Claude counts input, cache read and cache
  write (its output joins the context on the next request); Codex counts cached input, the rest
  of the input, and output, as its `last.totalTokens` does.

## Plan

1. **Core.** `ContextParts` (cache read, cache write, input, output; null when not reported) on
   `SessionStats`, replaced whole by `merge`.
2. **Adapters.** Claude fills it from the top-level assistant message usage next to
   `contextUsed`; Codex from `tokenUsage.last`.
3. **UI.** `ContextSummary` in the task view model, built from `TaskView.stats` when `usage` is
   on; a `ContextMenu` composable in `TaskScreen` next to `PermissionMenu`.
4. **Tests.** `AgentFiguresTest` merge; Claude and Codex adapter tests on recorded lines; the
   summary rows and percents in a view model test.
5. **Docs.** README paragraph on the `usage` flag; the popup in `docs/architecture.md`.

Check: `./gradlew check` passes.
