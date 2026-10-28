# T35. Done

PR: https://github.com/pyrlyn/octo/pull/44

## What was done

- **Flag.** `compact-idle`.
- **Core.**
  - `IdleCompaction` sets a task's time two minutes before the prompt cache of its last request expires.
  - When the time comes, the orchestrator checks the task under its lock: idle, nothing queued or pending, active or pinned, 200K tokens or more in the context. Then it starts a compaction turn through `AgentSession.compact()`.
  - A prompt or a stop cancels the time, and each turn is compacted at most once. The transcript gets a notice.
  - The times live in memory only.
- **Claude adapter.** It declares `Capability.Compact` and reads the cache lifetime (one hour or five minutes) from `message_start`. `compact()` sends `/compact`.
- **Tasks.** The three ideas in `ideas.md` became T35, T36 and T37.

## How it was checked

`./gradlew check` is green: ktlint, detekt and all tests.

`IdleCompactionTest` uses virtual time and covers:
- the moment of compaction;
- a prompt cancelling it;
- at most one compaction per turn;
- a small context;
- a snoozed task;
- the flag off;
- an agent that cannot compact.

`ClaudeAdapterTest` replays a synthetic `compact.transcript`.

Not run against a real Claude account: the `message_start` shape follows the Messages API streaming format.
