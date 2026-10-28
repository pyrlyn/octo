---
status: in progress
priority: P2
complexity: 3
readiness: 100%
agent: Claude Code / claude-opus-5-5
---

# T35. Compact idle Claude sessions before the prompt cache expires

A long Claude conversation is cheap to continue while its prompt cache is warm: the next request
reads the context from the cache. Once the cache expires (5 minutes on an API key, 1 hour on a
subscription), the next prompt pays to write the whole context again. Compacting an idle session
just before its cache expires reads the context while it is still cached and leaves a short
summary, so the next turn after a long pause is cheap. T3 Code does this as a beta
([5d3875d](https://github.com/pingdotgg/t3code/commit/5d3875d)).

Done means:

- Behind a `compact-idle` flag, an idle task whose agent can compact and whose context is large
  gets compacted shortly before its prompt cache expires.
- A prompt, a stop or any other turn cancels the pending compaction; each finished turn compacts
  at most once.
- The transcript says the session was compacted.
- Core and the Claude adapter have tests.

## Decisions

Taken from T3 Code's beta, since the creator approved the idea as it stands:

- **When.** Two minutes before the prompt cache of the turn's last request expires. The adapter
  reports the cache's lifetime from the request's `cache_creation` usage (1 hour or 5 minutes);
  a request served wholly from the cache keeps the last known lifetime. The core starts the clock
  when the request starts.
- **Which tasks.** Idle, with an empty queue and nothing pending, active or pinned (not snoozed
  or settled), with at least 200K tokens in the context, as in T3 Code.
- **How.** Claude takes `/compact` as a prompt. The transcript gets a notice instead of a user
  message.
- **Restarts.** Timers live in memory; a restarted Octo or agent waits for the next request.

## Execution plan

1. **Core.** Add `Capability.Compact`, `AgentSession.compact()`, `SessionStats.promptCacheTtlMs`
   and the `compact-idle` flag. A new `IdleCompaction` keeps each task's due time and wakes when
   the earliest is due. The orchestrator feeds it the cache lifetimes, cancels on a prompt or
   stop, and runs the compaction under the task's lock after checking the task again. The turn
   start that `prompt` uses is shared with it.
2. **Claude adapter.** Read the lifetime from `message_start`; `compact()` sends `/compact`.
3. **App.** Pass the flag to `Orchestrator.open`.
4. **Tests.** `IdleCompactionTest` on virtual time: due, cancelled by a prompt, at most once per
   turn, skipped below the threshold and for a snoozed task, flag off. Claude adapter: the
   lifetime from recorded `message_start` lines and the `/compact` prompt.
5. **Docs.** README flag paragraph and the architecture flag table.

Check: `./gradlew check` passes.
