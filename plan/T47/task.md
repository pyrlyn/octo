---
status: todo
priority: P3
complexity: 2
readiness: 0%
agent:
---

# T47. Prompt links

Users want to share a ready prompt, for example a one-click fix in a PR comment. T3 Code opens a
draft from a link and never runs it ([prompt-links.md](https://github.com/pingdotgg/t3code/blob/125b61954d/docs/user/prompt-links.md)).

Done means:

- The desktop app registers an `octo://new?prompt=…&repo=…&agent=…&model=…` link handler.
- A link opens the new-task dialog filled in; it never sends, never changes the permission mode
  and never picks a repository the user has not added.
- The palette has "Copy prompt link" for the current draft and for a sent message.
- Link parsing is common code with tests, including hostile input.
