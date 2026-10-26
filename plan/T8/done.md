# T8. Done

## What was done

- `core`:
  - `AgentAdapter.terms: VendorTerms?` (version, summary, url); a new terms version shows the
    notice again.
  - `TermsGate` (`octo.core.terms`): `pending`, `acknowledge`, `requireAcknowledged` (throws
    `TermsNotAcknowledgedException`). Acknowledgements live in `OctoPaths.acknowledgements`
    (`acknowledgements.json`, entries `<agent id>@<terms version>`). An unreadable file means the
    notices show again; it never blocks start-up.
  - `AtomicFile` (`octo.core.io`): whole-file writes through a temp file and an atomic move. T9
    reuses it for `state.json`.
- `adapters:base`: `BaseAgentAdapter.locate()`, so an adapter can try more than one binary name.
- `adapters/antigravity`: `AntigravityAdapter` on `AcpAgentAdapter`.
  - Off by default behind the `antigravity` flag; `AdapterRegistry` drops it while the flag is off.
  - Looks for `agy_acp_server.par` (macOS, Linux), then `agy_acp_server` (Windows `.exe` through
    `PATHEXT`).
  - Passes `--uid=` on Linux only, as the registry entry does.
  - Terms notice (version `2026-10-26`) linking https://antigravity.google/terms.
- `app`: registered with the host OS from `currentHostOs()`.

## Sources

- ACP registry, `antigravity-acp/agent.json`: version 1.4.3, proprietary licence, commands
  `./agy_acp_server.par` (darwin, linux; linux adds `--uid=`) and `./agy_acp_server.exe` (windows).
  https://github.com/agentclientprotocol/registry, file last changed in commit 06229c6, checked
  at registry `main` 1a52bbd on 2026-10-26.
- Terms: the Antigravity FAQ cited in `docs/research.md` (third-party wrapping not supported on
  consumer plans; Gemini Enterprise or AI Studio keys recommended).

## Not verified

- Antigravity is not installed here and has no published protocol documentation. The fixture is
  synthetic and follows plain ACP v1 only; extensions, auth methods and the version output are
  unknown. `verifiedVersion` 1.4.3 comes from the registry, not from a run.
- The meaning of the empty `--uid=` argument is not documented.

## Follow-up for T9 and T10

- T9: `OctoCore` must call `TermsGate.requireAcknowledged(adapter)` before `adapter.start`.
- T10: the UI shows `TermsGate.pending(adapter)` and calls `acknowledge` on the user's OK.

## How it was checked

- `./gradlew check -Pocto.allNativeTargets=true`: JVM and native tests, ktlint, detekt, all green.
  New tests: `TermsGateTest` (3), `AntigravityAdapterTest` (3), `AdaptersTest` (2).
- CI on `pyrlyn/octo` is blocked by GitHub billing, so the PR has no CI run.
