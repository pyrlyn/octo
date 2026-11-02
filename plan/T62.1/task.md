---
status: in progress
priority: P2
complexity: 2
readiness: 100%
agent: Claude Code / claude-opus-5-5
---

# T62.1. One live agent per task

Today a task never runs two agents because every path that starts one stops the old one first,
under the task's lock. Nothing checks it, so a future path could start a second agent in the
same worktree and leak the first process. A second handoff while one is still under way waits
for the lock and then needlessly restarts the agent the first one just started.

Done: the core refuses to start an agent while the task still has a live one, and a handoff
asked for while another is under way for the same task fails at once instead of queueing. Tests
show that no agent ever starts while another of the same task is open, across concurrent
switches and restarts. Works with every flag off.

## Execution plan

1. `Orchestrator.launchAgent`: throw `AgentAlreadyRunningException` when the task still has a
   live runtime, the one place every start goes through.
2. `Orchestrator.switchAgent`: an atomic set of tasks with a handoff under way; a second
   `switchAgent` for the same task throws `HandoffInProgressException` before it takes the lock.
3. Both exceptions in `OctoCore.kt` next to the others.
4. Tests in `core/src/commonTest/.../orchestrator/SingleAgentTest.kt`: a `FakeAdapter` hook
   that checks at every start that no session of the task is open; concurrent switches and
   restarts; a second concurrent switch is refused and changes nothing.
5. `docs/architecture.md` (switching agents); `./gradlew check` (tests, ktlint, detekt); a PR.
