---
status: todo
priority: P3
complexity: 3
readiness: 0%
agent:
---

# T53. Keyboard navigation and custom shortcuts

Octo has a few fixed shortcuts. T3 Code moves focus between the sidebar, conversation, composer
and panels with F6 / Shift+F6 and lets users rebind keys ([keyboard-focus.md](https://github.com/pingdotgg/t3code/blob/125b61954d/docs/user/keyboard-focus.md),
[keybindings.md](https://github.com/pingdotgg/t3code/blob/125b61954d/docs/user/keybindings.md), [2525252](https://github.com/pingdotgg/t3code/commit/2525252c96)).

Done means:

- F6 and Shift+F6 cycle focus between the visible areas and restore the last focused control.
- The sidebar is fully usable from the keyboard.
- Shortcuts are defined in one table; `keybindings.json` in the Octo home overrides them; a bad
  entry is warned about and skipped.
- The palette lists each action with its shortcut.
- The binding table and override loading have tests.
