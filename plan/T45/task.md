---
status: todo
priority: P2
complexity: 3
readiness: 0%
agent:
---

# T45. Several accounts per agent

A user with a work and a personal subscription of the same agent cannot use both in Octo. T3 Code
separates a driver from a provider instance, each with its own config directory, and warns before
a second Claude or Codex account ([providers-claude.md](https://github.com/pingdotgg/t3code/blob/125b61954d/docs/user/providers-claude.md), [560ddb1](https://github.com/pingdotgg/t3code/commit/560ddb14d9)).

Done means:

- `config.json` declares agent instances: an adapter id, a display name and the config directory
  the agent is started with (`CLAUDE_CONFIG_DIR`, `CODEX_HOME`, …, from the adapter).
- Instances show in the agent picker, the agents screen, usage (T29) and limits separately.
- Adding a second instance of an agent whose vendor terms restrict it shows those terms first.
- Octo never reads or copies credentials; it only points the agent at its directory.
- Core and two adapters have tests with temp config dirs.
