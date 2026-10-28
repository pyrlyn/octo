---
status: in progress
priority: P2
complexity: 2
readiness: 0%
agent: Claude Code / claude-opus-5-5
---

# T38. A setting for the idle compaction threshold

T35 compacts an idle conversation only from 200K tokens in its context, T3 Code's threshold. With
a 200K context window Claude compacts on its own before a conversation gets there, so the
compaction mostly helps 1M-window models. The creator asked for the threshold to be a setting.

Done means: `compactIdleMinTokens` in `config.json` sets the threshold, 200K when it is left out;
0 compacts every idle conversation. Tests cover the setting and its default; the README shows it.

## Execution plan

1. `OctoConfig.compactIdleMinTokens`, defaulting to 200K.
2. `Orchestrator.open` takes the threshold in place of the flag's boolean (null when the flag is
   off), and `IdleCompaction` takes it in its constructor.
3. Tests: a lower threshold compacts a smaller context; the config parses the key and its default.
4. README and the architecture note.

Check: `./gradlew check` passes.
