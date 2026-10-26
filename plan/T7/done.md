# T7. Done

- `adapters:grok`: `GrokAdapter` on the shared ACP client from T6. It runs `grok agent stdio`
  and, when a session needs auth, logs in with `cached_token`, which reuses Grok Build's own
  cached login (Octo never reads `~/.grok/auth.json`). Grok's `_x.ai/*` notifications are
  ignored. Grok offers `session/resume`, so resume restores without a replay.
- Community CLI: the npm `grok-dev` package also installs `grok`. Grok Build prints
  `grok <version> (<commit>) [<channel>]`; `grok-dev` prints a bare version through commander.
  The probe reads the latter as an unknown version, and `start` refuses it before launch with
  the new `WrongAgentBinaryException` (core), raised from a `checkBinary` hook in
  `BaseAgentAdapter`.
- `app`: `builtInAdapters` registers Grok.

Sources:
- The local `grok` 1.0.67 (`verifiedVersion`): its `--version` output, its `initialize` reply
  (capabilities with `sessionCapabilities.resume`, auth methods `cached_token` and `grok.com`)
  and a `session/new` that succeeded without `authenticate` (checked 2026-10-26). No prompt
  was sent.
- `superagent-ai/grok-cli` at commit fb97af8: `package.json` (name `grok-dev` 1.1.7, bin `grok`)
  and `src/index.ts` (`program.version(packageJson.version)`).
- ACP v1 schema as in T6.

Not verified: no auth-status command was found in `grok --help`, so the probe reports
`Unknown` auth. Fixtures are synthetic.

Checked: `./gradlew check -Pocto.allNativeTargets=true` green locally (3 Grok tests).
GitHub Actions is still blocked by account billing.

PR: https://github.com/pyrlyn/octo/pull/10
