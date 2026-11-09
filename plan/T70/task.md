---
status: in progress
priority: P2
complexity: 2
readiness: 90%
agent: Claude Code / claude-opus-5-5
---

# T70. The agent's plan in a panel beside the transcript

The creator asked for the task list on the right, as in their reference: a "Progress n/m" block whose done steps
fold under "N completed" and whose open steps stay listed. It must use Octo's own style, not the reference's, and
the Figma file must show it.

The plan already reaches the transcript (`AgentEvent.PlanUpdated` → `TranscriptEntry.Plan`), so only the view is new.

Done:

- A selected task whose agent has posted a plan shows the latest plan in a panel at the right of the transcript, on
  macOS, Windows and Linux.
- The header counts done steps against all steps.
- Done steps fold under "N completed", which opens on a click.
- A task without a plan, or a window too narrow for both, shows no panel.
- The Figma file has the panel.
- `./gradlew check` is green.

## Plan

1. `TaskUiState.plan`: the entries of the last `TranscriptEntry.Plan`, null without one; unit test.
2. `PlanPanel` (`feature/task`): a floating glass card built from `octo.design` tokens and kit icons.
3. `TaskScreen`: the panel sits beside the transcript and hides below a minimum width.
4. Figma: the panel on the Octo macOS screen next to the transcript, as a component.
5. Check: unit test, a `ShellShotsTest` render with a plan, and `./gradlew check`.
