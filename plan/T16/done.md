# T16. Plugins — done

WebAssembly plugins behind the `plugins` flag. The engine is Chasm, through the `wasm`
interface shared with rtok.

- **Shared package.** `libs/wasm` and `libs/wasm-chasm` moved out of rtok-kotlin into a new
  repo, [pyrlyn/kotlin-wasm](https://github.com/pyrlyn/kotlin-wasm) (package `wasm`). Octo
  includes it as a Gradle build from `../../packages/kotlin-wasm`, or `KOTLIN_WASM_DIR`.
  - `readPacked` and `unpackI64` moved there as well, in
    [pyrlyn/kotlin-wasm#1](https://github.com/pyrlyn/kotlin-wasm/pull/1).
  - rtok's switch to the package is [pyrlyn/rtok#78](https://github.com/pyrlyn/rtok/pull/78),
    left open for the creator.
- **Core** (`octo.core.plugins`):
  - ABI 1: the exports `memory`, `octo_manifest`, `octo_alloc` and `octo_run`, JSON in and out,
    no imports. It is documented in `docs/plugins.md`.
  - `PluginManifest` checks every field. A command must be a bare program name, and its
    arguments are bounded.
  - `PluginLoader.load(flags, dir, runtime)` reads nothing with the flag off. A broken file is
    a warning: not Wasm, too big, a trap, a missing export, bad JSON, another ABI, a bad field,
    a duplicate id.
  - `Plugin.run` gives every call a fuel budget, caps the output, checks it against guest
    memory, and runs one call at a time.
- **Agents.** The new `adapters:plugin` module holds `PluginAgentAdapter`, a generic ACP
  adapter. The app registers plugin agents on this computer only. An id that a built-in agent
  or another plugin already has is skipped with a warning.
- **UI** (MVVM): `PluginActionsViewModel` and a Plugins menu on the task screen. A prompt is
  sent to the agent; a message is shown in a dialog.
- **Fixtures.** A `no_std` Rust demo plugin in `core/src/commonTest/fixtures/plugins` is built
  by `build.sh` into `demo.wasm`, `future-abi.wasm` and `spin.wasm`.
- **Docs.** `docs/plugins.md`, README, architecture and toolchain.

The included build follows ci-diffuser's local-only decision. CI does not check out
kotlin-wasm, just as it does not check out ci-diffuser.

## Check

`./gradlew check -Pocto.allNativeTargets=true` is green: 258 tests, ktlint and detekt clean.
The plugin tests run on the JVM and on macosArm64. They cover:

- the loader: the demo plugin's manifest and both actions, an unknown action, broken files
  (a duplicate, a future ABI, a non-Wasm file, a non-`.wasm` file ignored), fuel stopping a
  spinning plugin, a missing folder, and the manifest rules;
- the flag gate: with the flag off, a runtime that fails on use is never called;
- `PluginAgentAdapter`: the launched command and arguments, an ACP turn, and the version probe;
- the app: the flag gate with a real plugins folder, and that plugin agents never take an id
  already in use;
- the view model: the actions listed, the prompt sent, the message shown, a removed task.

kotlin-wasm `./gradlew check` is green (14 tests). rtok's
`./gradlew check macosArm64Test cliTest e2eTest` is green on its PR branch.

PR: https://github.com/pyrlyn/octo/pull/20
