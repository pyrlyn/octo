---
status: in progress
priority: P3
complexity: 4
readiness: 100%
agent: Claude Code / claude-opus-5-5
---

# T16. Plugins

Optional, behind a flag: a plugin manifest and loader that adds adapters or actions; a broken plugin is warned about and skipped.

Check: Loader tests with a good and a broken plugin; the flag disables loading.

## Execution plan

The creator's decisions: plugins are WebAssembly modules run by Chasm, through the `wasm`
interface shared with rtok in [kotlin-wasm](https://github.com/pyrlyn/kotlin-wasm), which Octo
includes as a Gradle build.

1. Build: include `../../packages/kotlin-wasm` (or `KOTLIN_WASM_DIR`); core depends on
   `pyrlyn.wasm:wasm`, the app and core tests on `pyrlyn.wasm:wasm-chasm`.
2. Core, `octo.core.plugins`:
   - Octo's plugin ABI 1: the module exports `memory`, `octo_alloc(len) -> ptr`,
     `octo_manifest() -> packed` and `octo_run(ptr, len) -> packed`, where packed is
     `len << 32 | ptr`. It imports nothing, so a plugin cannot touch files, the network or
     processes.
   - The manifest: `abi`, `id`, `name`, `version`, `agents` (ACP agents: id, display name, a bare
     command on PATH, arguments, verified version) and `actions` (id, title). Every field is
     checked before use.
   - `PluginLoader.load(flags, dir, runtime)` reads every `*.wasm` in `<home>/plugins` with the
     `plugins` flag on and nothing at all with it off. A broken plugin (not Wasm, a trap, missing
     exports, bad JSON, an unknown ABI, a bad field, a duplicate id, too big) is a warning.
   - `Plugin.run(action, task)` passes the task as JSON and returns a prompt to send and/or a
     message to show. Every call is fuel-metered, its output size capped and checked against
     guest memory, and calls on one instance are serialized.
3. Adapter module `adapters/plugin`: a generic ACP adapter built from a manifest agent. The app
   registers plugin agents on this computer only, skipping an id a built-in agent already has.
4. UI, MVVM: a Plugins menu on the task screen lists the actions; a prompt goes to the agent,
   a message is shown.
5. Tests: the loader with the demo plugin, a non-Wasm file, a future ABI, a duplicate, a
   spinning plugin stopped by fuel, and the flag gate; the adapter's arguments; the view model.
   The fixtures are built from `core/src/commonTest/fixtures/plugins/demo-plugin` by `build.sh`.
6. Docs (README, architecture, toolchain), done.md, PR.
