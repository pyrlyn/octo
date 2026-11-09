# T69. Done

- Adapters: Claude, Codex, Antigravity, Kimi Code and Grok ship redrawn logos in the app's tile
  style (24×24, `rx=6`), checked with resvg renders.
- kotlin-macos (pyrlyn/packages#8): `SidebarRow` takes an optional `icon`; with one, the status
  dot becomes a badge on its bottom-right corner, ringed in `bg/chrome`.
- `ui`: both sidebars lead each task row with the agent logo and its status badge; the separate
  dot and T67's trailing glyph are gone.
- Figma: the Agent logo set (88:4382) holds the new marks; the Sidebar row component leads with an
  Agent logo instance (the `Agent` instance-swap property now offers the logo variants) and a
  ringed badge; all 144 rows keep their agent.

Checked: `ControlsTest.aSidebarRowWithAnIconWearsItsStatusAsABadgeOnTheIconsCorner` and the
kotlin-macos `check`; `:ui:test`, `:core:jvmTest`, the five adapters' `check`, ktlint and detekt
clean; `ShellShotsTest` renders of the macOS and Linux sidebars looked over by eye.

PR: branch `t69-agent-logos`, stacked on `t65-sidebar-age`.
