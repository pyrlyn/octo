---
status: todo
priority: P3
complexity: 2
readiness: 0%
agent:
---

# T31. Command-line client

Scripts and other tools cannot drive Octo without the UI. T3 Code has `t3 thread` to start,
message and read threads from a terminal
([955b4cc](https://github.com/pingdotgg/t3code/commit/955b4cc)).

Behind a feature flag. Done: an `octo` CLI over the core API (the loopback API from T15) with
`task list`, `task start`, `task send`, `task read` and `task wait`, JSON output on request,
token auth as for paired devices. Tests run it against a fake core; `./gradlew check` passes.
