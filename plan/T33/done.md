# T33. Done

The `queue` flag adds a per-machine limit on live agents.

- **Config.** `maxRunningAgents` in `config.json` sets the limit for this computer, and the same key on a `machines` entry sets it for that machine. A machine without the key has no limit. A value below 1 is ignored, with a warning at startup.
- **Waiting.** A task started over its machine's limit waits as `Queued`, with a notice in its transcript. Prompts sent meanwhile join its message queue.
- **Order.** Waiting tasks start in FIFO order per machine. A slot frees once a stop has finished or the agent exits; restart and agent switch keep theirs. A start that fails passes its slot on at once.
- **Start now.** The task screen's "Start now" (`OctoCore.startNow`) starts a queued task over the limit.
- **Shutdown.** Closing Octo starts no waiting task.

Two other pieces moved out of `Orchestrator`: the setup command, to `WorktreeSetup`, and one shared view update, to `OctoState.change`. That keeps `Orchestrator` under detekt's class size limit.

PR: https://github.com/pyrlyn/octo/pull/42 (commit 8f9c5fa).

Checked: `./gradlew check` passed locally (ktlint, detekt, and tests on JVM and macosArm64), including the new tests:

- `AgentSlotsTest` and `QueueTest`.
- The limit test in `MachineConfigTest`.
- The flag test in `OctoTest`.
- The Start now test in `ViewModelTest`.
