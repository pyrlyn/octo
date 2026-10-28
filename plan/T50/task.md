---
status: todo
priority: P2
complexity: 3
readiness: 0%
agent:
---

# T50. Grouped tool activity and per-call diffs

Long turns flood the transcript with tool calls. T3 Code collapses consecutive tool calls into one
summary row ("Ran 2 commands, edited 3 files"), streams a running command's output and opens a
file change in the diff panel ([activity-log.md](https://github.com/pingdotgg/t3code/blob/125b61954d/docs/user/activity-log.md), [441a4eb](https://github.com/pingdotgg/t3code/commit/441a4ebd65), [1f8530e](https://github.com/pingdotgg/t3code/commit/1f8530ec3c)).

Done means:

- Consecutive tool entries render as one expandable summary that names the main action kinds.
- A running command's output updates live and shows only its tail.
- A file-change entry has Open diff, which opens the diff view on that file and hunk.
- A pending approval shows on its own row in the timeline as well as in the request panel.
- The grouping rule is common code with tests; the view model has tests.
