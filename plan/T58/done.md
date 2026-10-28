# T58. Done

Added `adapters:mimo` and `adapters:zcode`, registered in `settings.gradle.kts`,
`app/build.gradle.kts` and `Adapters.kt`, in PR https://github.com/pyrlyn/octo/pull/58.

- `MimoAdapter` runs Xiaomi MiMo Code through `mimo acp` on the shared ACP client. It sends no
  `authenticate`, because MiMo's only auth method is an interactive sign-in.
- `ZCodeAdapter` runs Z.ai ZCode one `zcode --prompt=… --output-format stream-json` process per
  turn and resumes the session with `--resume`. It maps Octo's four permission modes to `--mode`;
  cancel kills the turn's process.
- `CliAgentAdapter` in `adapters:base` now holds the binary lookup, version probe and binary check.
  `BaseAgentAdapter` builds on it with no behaviour change.
- Research for both CLIs is in `docs/research.md` §6.8 and §6.9, with rows in §6.6. The README,
  `docs/architecture.md` and `plan/README.md` list both agents. `toolchain.md` lists no agent CLIs,
  so it is unchanged.

Checked: `./gradlew check` passes locally (`MimoAdapterTest`, `ZCodeAdapterTest`, `AdaptersTest`,
ktlint, detekt). The adapters were not run against a real `mimo` or `zcode`: tests replay synthetic
transcripts built from the CLIs' source.
