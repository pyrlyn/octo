# T62.1. Done

- `Orchestrator.launchAgent`, the one place every start goes through, throws
  `AgentAlreadyRunningException` while the task still has a live agent.
- `switchAgent` marks the task as handing over in an atomic set; a second `switchAgent` for the
  same task throws `HandoffInProgressException` at once instead of waiting for the lock and
  restarting the agent the first one started. The mark clears however the switch ends.
- The Switch agent dialog already shows a refused switch as its error; no UI change.
- `docs/architecture.md`: "One agent per task".

Tests: `SingleAgentTest` (a start hook on `FakeAdapter` records any start while a session of the
task is open, across a switch and two restarts launched together; a second switch during a slow
stop is refused and starts nothing; a failed switch leaves the task open to the next one). With
the refusal removed, the second switch waited on the lock and the test hung; the test now bounds
that wait so it fails instead.

Checked with `./gradlew check`: 694 tests, 0 failures, ktlint and detekt clean.
