---
status: in progress
priority: P2
complexity: 2
readiness: 90%
agent: Claude / claude-opus-5-5
---

# T69. Agent logos with a status badge in the sidebar

T67 put each agent's glyph at the end of its sidebar row, next to the status dot at the start.
Two marks per row compete for the eye, and the glyphs were placeholders (a circle for Claude,
`</>` for Codex). The row should lead with the agent's logo, drawn in the app's tile style, and
carry the status as a small dot badge on the logo's bottom-right corner.

Done: the Claude, Codex, Antigravity, Kimi Code and Grok adapters ship redrawn logos; both
sidebars lead each task row with the logo and its status badge, and no longer show a separate
dot or a trailing glyph; the Figma Sidebar row and Agent logo components match.

## Execution plan

1. Adapters: replace the `AgentIcon` SVGs of claude, codex, antigravity, kimi and grok
   (24×24 tile, `rx=6`, one mark each, rendered with resvg to check).
2. kotlin-macos (pyrlyn/packages, on the `macos-sidebar-meta` branch of #8): `SidebarRow` takes an
   optional `icon`; when set, the status dot becomes a ringed badge on its bottom-right corner.
   Test: the badge sits at the icon's corner.
3. `ui`: `MacSidebar` passes the `Glyph` as `icon` instead of `trailing`; the classic
   `TaskRowItem` draws the same logo-with-badge in place of its `StatusDot` and drops the trailing
   glyph.
4. Figma: the Agent logo set holds the new marks; the Sidebar row leads with an Agent logo
   instance and a dot badge, with an instance-swap property, set per row from its agent.
5. Verify: `:ui:test`, ktlint, detekt, the kotlin-macos `check`.
