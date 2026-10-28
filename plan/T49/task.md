---
status: todo
priority: P2
complexity: 2
readiness: 0%
agent:
---

# T49. Mention another task in a prompt

When one task needs what another learned, the user copies text between them. T3 Code's composer has
an `@` picker that inserts a thread reference ([4ab7084](https://github.com/pingdotgg/t3code/commit/4ab7084aa7), [0affd28](https://github.com/pingdotgg/t3code/commit/0affd28db2)).

Done means:

- Typing `@` in the composer lists tasks by title; picking one inserts a reference chip.
- On send, the chip expands to a block with that task's title, branch, status and a summary of its
  latest messages, built from the transcript the way handoff (T23) builds it.
- The agent can fetch more through the messaging MCP server when `agent-messaging` is on.
- Core and the view model have tests.
