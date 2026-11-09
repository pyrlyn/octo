# T67. Done

Every sidebar task row shows its agent's icon, on macOS and in the classic sidebar (Windows,
Linux). PR: https://github.com/pyrlyn/octo/pull/83.

- `AgentCatalog.icon(id)` reads `AgentAdapter.icon` without a probe; `MainViewModel` sets
  `TaskRow.icon`; `MacSidebar` draws `Glyph` in `SidebarRow.trailing`, `TaskRowItem` after the
  title. No new icon source and no kotlin-macos change.
- Figma `h9sViI6qnym6iDdXDZqxcq`: `Sidebar row` gained the `Agent` instance-swap property over
  six `Agent glyph/*` components drawn from the adapters' SVGs; all 144 rows are set.

Checked: `./gradlew check` green (ktlint, detekt, `AgentCatalogTest`,
`ViewModelTest.everyTaskRowCarriesItsAgentsIcon`); `ShellShotsTest` renders for macOS and
Windows show the Claude and Codex glyphs and the demo agent's monogram.
