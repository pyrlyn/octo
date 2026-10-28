---
status: todo
priority: P3
complexity: 3
readiness: 0%
agent:
---

# T57. Per-repository settings

Every setting in `config.json` is global except the worktree setup script. Users want a default
agent, model, permission mode and base ref per repository. T3 Code layers project overrides over
environment defaults and shows where a value comes from ([project-settings.md](https://github.com/pingdotgg/t3code/blob/125b61954d/docs/user/project-settings.md)).

Done means:

- `config.json` holds per-repository overrides for the default agent, model, permission mode,
  base ref and setup script; a missing key inherits the global value.
- The new-task dialog uses them; settings show which layer a value comes from and can reset an
  override.
- Only the user's config sets them, never a file in the repository.
- Config resolution has tests.
