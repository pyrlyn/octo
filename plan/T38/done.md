# T38. Done

PR: https://github.com/pyrlyn/octo/pull/45

## What was done

- **Setting.** `compactIdleMinTokens` in `config.json` sets the idle compaction threshold. It defaults to 200K, and 0 compacts every idle conversation.
- **Wiring.** `Orchestrator.open` takes the threshold, which is null while the `compact-idle` flag is off, and `IdleCompaction` checks the context against it.
- **Docs.** The README and the architecture note describe the setting.

## How it was checked

`./gradlew check` is green: ktlint, detekt and all tests.
- `IdleCompactionTest` compacts a 100K context at a 50K threshold.
- `MachineConfigTest` parses the key and its default.
