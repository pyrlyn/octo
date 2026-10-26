# T14. Done

## What was done

- `core`, `octo.core.preset`:
  - `Preset(name, members)` and `PresetMember(role, agent, prompt)`.
    - `problem()` refuses a blank name, a team with no members, a blank role or agent, and a
      repeated role.
  - `PresetLibrary` is the UI's only view of presets.
    - `FilePresetLibrary` keeps them in `presets.json` in the Octo home (`OctoPaths.presets`),
      versioned and written atomically through `AtomicFile`.
    - Renaming onto an existing name is refused.
    - A file that cannot be read is warned about and never overwritten: the library opens
      empty and refuses every change until the user fixes or removes the file.
  - `OctoCore.startPreset(preset, repo, baseRef, machine)` starts one task per member, titled
    `<preset>: <role>`.
    - In a team of several, each first prompt starts with the agent's role and its teammates.
    - A lone member gets just its own prompt.
  - The `presets` flag.
- `app`: with the flag on, Octo opens the library and passes it to the UI, and the file's
  warning is shown. With the flag off, the file is not read.
- `ui` (MVVM):
  - The new-task form has a One agent / Team preset choice, shown only with the flag on.
    - Team mode lists the presets and the agents not installed on the chosen machine.
    - Vendor terms are settled for every member's agent before the first task starts.
    - Create starts the whole team and opens the first task.
  - `PresetsViewModel` and `PresetsDialog` (Edit presets…): list, create, edit (name, members
    with role, agent and first prompt) and delete. A new member starts on the first installed
    agent.
- README and the architecture flag table.

## How it was checked

- `./gradlew check -Pocto.allNativeTargets=true`: all green, 208 tests.
- New tests:
  - `PresetTest` (5, on the JVM and native):
    - presets round-trip through the file, including a trimmed name, a rename and a remove;
    - a rename onto another preset's name is refused;
    - a broken file stays byte for byte as it was;
    - every invalid preset is refused;
    - `startPreset` creates one task per member with the right titles and team prompts;
    - a lone member gets its own prompt only.
  - `PresetViewModelTest` (4):
    - teams are hidden without a library;
    - a team waits for the terms and then starts every member;
    - a missing agent blocks Create;
    - the editor creates, validates, edits, renames and deletes.
  - `OctoTest`: with the flag off the library is null and a broken `presets.json` is not even
    read; with it on there is one warning.

## Not checked

- The dialogs by eye: no visual check of the window this time.
- CI on GitHub: blocked by the account's billing, as for the previous tasks.
