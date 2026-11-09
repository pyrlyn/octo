---
status: in progress
priority: P2
complexity: 2
readiness: 80%
agent: Claude / claude-opus-5-5
---

# T67. Agent icon in every sidebar task row

A sidebar row names its agent only in the subtitle, so telling a Claude task from a Codex task
means reading every row. Each task row shows the glyph of the agent running it at the row's end,
the way the Agents pane already does.

Done: every task row of the macOS sidebar and the classic sidebar (Windows, Linux) shows its
agent's icon, falling back to the agent's monogram when the adapter ships none; the icon comes
from the adapter (`AgentAdapter.icon`), with no new icon source; the Figma row
(file `h9sViI6qnym6iDdXDZqxcq`) shows it too.

## Execution plan

1. `core`: `AgentCatalog.icon(id)` reads the adapter's icon without probing the binary, so the
   sidebar does not run `--version` for every agent on each state change.
2. `ui`: `TaskRow.icon`, filled by `MainViewModel` from the catalog. `MacSidebar` puts a `Glyph`
   in `SidebarRow`'s `trailing` slot (kotlin-macos 0.0.4, no package change); the classic
   `TaskRowItem` adds the same `Glyph` after the title column.
3. Tests: the catalog's icon lookup, the row mapping, `FakeCatalog` icons; `ShellShotsTest`
   gives its sample tasks the Claude and Codex adapters' icons.
4. Figma: the sidebar rows of the macOS screens get the agent glyph.
5. Verify: `./gradlew check` (ktlint, detekt, tests) and the `OCTO_SHOTS` renders.
