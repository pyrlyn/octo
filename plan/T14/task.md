---
status: in progress
priority: P3
complexity: 3
readiness: 100%
agent: Claude Code / claude-opus-5-5
---

# T14. Team and assistant presets

Optional, behind a flag: saved presets that start a group of agents with roles and prompts.

Check: Preset round-trip tests; the flag hides the feature when off.

## Execution plan

1. Core, `octo.core.preset`:
   - `Preset(name, members)` and `PresetMember(role, agent, prompt)`, serializable, with
     `problem()` validation: a non-blank name, at least one member, a non-blank role, unique
     roles.
   - `PresetLibrary`: a `StateFlow` of presets plus `put(preset, replacing)` and `remove(name)`.
     `FilePresetLibrary` keeps them in `presets.json` in the Octo home (`OctoPaths.presets`) and
     writes it through `AtomicFile`. A broken file is warned about and left untouched: the
     library opens empty and refuses to write, so the user's file is never overwritten.
   - `OctoCore.startPreset(preset, repo, baseRef, machine)`: one task per member, titled
     `<preset>: <role>`. When a preset has several members, each first prompt starts with a short
     note on the agent's role and its teammates.
   - The `presets` flag.
2. App: with the flag on, open the library and pass it to the UI. With the flag off, nothing is
   read.
3. UI, MVVM:
   - `NewTaskViewModel`: a team mode, shown only with a library. Pick a preset and Create starts
     every member. Vendor terms are asked first for each member's agent.
   - `PresetsViewModel` and `PresetsDialog`: list, create, edit and delete presets. Each member
     has a role, an agent and a prompt.
4. Tests:
   - preset round trip through the file;
   - a broken file stays untouched;
   - validation;
   - `startPreset` titles and prompts;
   - the view models with fakes;
   - the app's flag gate.
5. Docs: README, the architecture flag table, done.md, PR.
