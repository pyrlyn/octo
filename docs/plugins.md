# Plugins

With `"plugins": true` in `config.json`, Octo loads every `*.wasm` file in the `plugins` folder
of the Octo home when it starts. With the flag off, Octo does not read that folder.

A plugin is a WebAssembly module. Octo runs it in [Chasm](https://github.com/CharlieTap/chasm),
an interpreter, through the `wasm` interface it shares with rtok
([kotlin-wasm](https://github.com/pyrlyn/kotlin-wasm)). A plugin can add:

- **agents**: ACP agents Octo starts from a program on `PATH`;
- **actions**: entries in a task's Plugins menu, which turn the task into a prompt for its agent
  or a message to show.

Octo skips a broken plugin and shows a warning at start-up. A plugin is broken when it is:

- not a WebAssembly module, or over 16 MiB;
- missing an export, or trapping;
- returning a manifest that is not JSON, needs another ABI, or has a bad field;
- reusing the id of a plugin that already loaded.

An agent whose id is already taken, by a built-in agent or another plugin, is skipped the same
way.

## The sandbox

The module may import nothing. It therefore has no way to reach files, processes, the network,
the clock or Octo itself. It only turns the JSON Octo hands it into JSON. Its limits:

- Every call has a fuel budget of about 100 million guest calls and loop turns. A plugin that
  never returns is stopped.
- An answer may be at most 1 MiB; a manifest, 64 KiB.
- Octo checks every pointer and length the plugin returns against the plugin's own memory.
- Calls on one plugin take turns.

A plugin agent is a different matter: it is a real program, and it runs with the user's rights,
like the built-in agents. A plugin can only name it:

- the command is a bare program name looked up on `PATH`, with no paths and no shell;
- the arguments are a fixed list from the manifest;
- the agent list shows each one as `<name> (plugin <plugin name>)`.

Plugin agents run on this computer only, not on remote machines.

## ABI 1

All strings are UTF-8 JSON. A *packed* result is an `i64` that holds a pointer in its low 32 bits
and a length in its high 32 bits.

| Export | Signature | Does |
| --- | --- | --- |
| `memory` | memory | The linear memory Octo reads from and writes to |
| `octo_manifest` | `() -> i64` | Returns the packed manifest |
| `octo_alloc` | `(len: i32) -> i32` | Returns a pointer to `len` free bytes, or 0 |
| `octo_run` | `(ptr: i32, len: i32) -> i64` | Runs an action on the input at `ptr`; returns the packed result, or a negative number to refuse |

The manifest:

```json
{
  "abi": 1,
  "id": "demo",
  "name": "Demo",
  "version": "1.0.0",
  "agents": [
    { "id": "demo-acp", "displayName": "Demo ACP", "command": "demo-acp", "args": ["--acp"], "verifiedVersion": "1.0.0" }
  ],
  "actions": [{ "id": "review", "title": "Ask for a review" }]
}
```

The rules for each field:

- Ids are lowercase letters, digits and dashes, at most 40 characters.
- Names, titles and versions are 1 to 60 printable characters.
- `command` matches `[A-Za-z0-9_][A-Za-z0-9._-]*`.
- There are at most 32 agents, 32 actions and 32 arguments.

Octo reads `abi` before anything else. A plugin built for a newer ABI is therefore skipped with
a message saying so, even when the rest of its manifest has a different shape.

The input of `octo_run`:

```json
{
  "action": "review",
  "task": {
    "title": "Fix login",
    "repo": "/src/app",
    "branch": "octo/fix-login",
    "baseRef": "main",
    "agent": "claude",
    "status": "Idle"
  }
}
```

The result is `{"prompt": "..."}`, `{"message": "..."}` or both:

- `prompt` (at most 20 000 characters) is sent to the task's agent, as if the user had typed it.
- `message` (at most 2 000 characters) is shown in a dialog.

## Building one

The demo plugin in `core/src/commonTest/fixtures/plugins/demo-plugin` is a `no_std` Rust crate.
It has no dependencies, so it shows the whole ABI in under a hundred lines. It builds with
`rustup target add wasm32-unknown-unknown` and then `build.sh` in that folder. Any language that
compiles to WebAssembly works: the ABI is only the four exports above.
