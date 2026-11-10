---
status: in progress
priority: P2
complexity: 3
readiness: 90%
agent: Claude Code / claude-opus-5-5
---

# T71. Message history rail in the transcript

A long transcript has no quick way back to an earlier turn: the user scrolls and reads. The
Figma file [Octo macOS](https://www.figma.com/design/h9sViI6qnym6iDdXDZqxcq/Octo-macOS?node-id=97-4819)
now has a message history rail. Each of the user's turns gets a short tick in the left gutter
of the transcript. Hovering a tick opens a card with the turn's age, the user's message (up
to three lines) and the start of the agent's reply (up to two lines). A click scrolls the
transcript to that turn.

Design: the `History tick` component (`Default`, `Near`, `Current`), the `History preview`
component (`Age`, `Message`, `Reply` text properties), the `History rail` on the main
window, and the hover state on the "Octo — Message history (macOS)" screen.

Done means:

- `feature/task` (`TaskScreen.kt`, `MacTask.kt`) draws the rail beside the transcript, one
  tick per user turn. The tick for the turn in view is `Current`, and its neighbours are
  `Near`.
- Hover shows the preview card; a click scrolls to the turn; the rail hides when the
  transcript has fewer than two user turns.
- Colours, spacing and radii come from the existing `MacColors` and `MacType` tokens, with no
  new hex values.
- Tests cover the tick list built from a recorded transcript, the current-turn pick while
  scrolling, and the hide rule.

Check: `./gradlew check` is green; the macOS app shows the rail and the card as in the Figma
screen.

## Execution plan

1. `core`: `TranscriptEntry.Text.atMs`, the send time of a prompt, set from the orchestrator's clock
   (null for replies and older history); `CoreFlags.HISTORY_RAIL` (`history-rail`).
2. `app`/`ui`: `Octo.historyRail` → `ViewModels(historyRail = …)` → `showsHistoryRail` →
   `TaskScreen(historyRail = …)`.
3. `feature/task/HistoryRail.kt`: pure `historyTurns`, `currentTurn`, `tick`, `showsRail`,
   `turnHeading`; the `HistoryRail` composable (ticks in the left gutter, `TooltipArea` with the
   `History preview` card on `elevatedSurface`, a click scrolls the shared `LazyListState`).
   `TaskScreen.Conversation` overlays it on the transcript pane, one layout for every OS.
4. Tests: `HistoryRailTest`; the send time in `OrchestratorTest`; the flag in `ForkViewModelTest`.
5. Docs: `features.md`, `configuration.md`, the flag table in `architecture.md`.
6. `./gradlew check`; renders of the macOS shell with the rail and the card.
