---
status: in progress
priority: P2
complexity: 2
readiness: 90%
agent: Claude Code / claude-opus-5-5
---

# T68. Message map beside the transcript

A long task's transcript is hard to move around in: finding an earlier prompt means scrolling
through tool calls and replies. A thin map beside the transcript gives every message a mark, so the
conversation's shape reads at a glance and any message is one click away.

Done means:

- Behind a new `message-map` flag, the task screen shows a column of marks beside the transcript,
  one per message: the user's prompts and the agent's replies. Thoughts, tools, plans, requests,
  checkpoints and notices get no mark.
- A prompt's mark is longer than a reply's; the marks of the messages on screen stand out.
- Hovering a mark shows the start of that message in a tooltip; a click scrolls the transcript to it.
- With the flag off, or with fewer than two messages, the transcript looks as it does today.
- Which entries become marks and what their preview says is plain code with unit tests.

## Execution plan

1. `core`: `CoreFlags.MESSAGE_MAP` (`message-map`), listed in `CoreFlags.all`.
2. `app`: `Octo.messageMap`, passed from `Main.kt` into `ViewModels(messageMap = …)`.
3. `ui`: `ViewModels.canMapMessages`; `OctoApp.TaskPane` passes it to `TaskScreen`.
4. `feature/task`: `MessageMark.kt` with the pure `messageMarks(entries)` (index, role, one-line
   preview capped in length; a files-only message previews its file names) and the `MessageMap`
   composable (marks, `TooltipArea` preview, click scrolls the shared `LazyListState`).
   `TaskScreen.Transcript` puts it beside the `LazyColumn` when the flag is on.
5. Tests: `MessageMarksTest` in `feature/task`; `canMapMessages` on and off in `ui`.
6. README/architecture flag list if it lists flags; `./gradlew check` (ktlint, detekt, tests).
