---
status: todo
priority: P2
complexity: 3
readiness: 0%
agent:
---

# T44. Plan mode

Users often want an agent to propose a plan before it touches files. Claude Code, Codex and
several ACP agents have a plan mode; T3 Code exposes it as `/plan` in the composer and shows a
"Plan Ready" status ([composer.md](https://github.com/pingdotgg/t3code/blob/125b61954d/docs/user/composer.md), `docs/research.md` §2.1 status ladder).
Octo's four permission modes have no plan mode.

Done means:

- Adapters declare a plan capability; the composer offers Plan for agents that have it.
- A plan the agent produces shows as a plan card in the transcript with Approve (switches to the
  chosen permission mode and continues) and Revise (sends feedback).
- A task with a plan awaiting review shows a "Plan ready" status in the sidebar ladder, below
  Blocked and above Working.
- The Claude adapter and one ACP adapter implement it with replay-fixture tests; the reducer and
  view model have tests.
