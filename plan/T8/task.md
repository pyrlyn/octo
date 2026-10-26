---
status: in progress
priority: P1
complexity: 3
readiness: 100%
agent: Claude Code / claude-opus-5-5
---

# T8. Antigravity adapter

Adapter for Antigravity over ACP (`agy_acp_server` / `agy`), behind a feature flag that shows the vendor's terms before first use. Tests on fixtures only.

Check: Adapter tests pass on fixtures; the flag gates it; the terms notice is shown once.

## Execution plan

1. Core: `VendorTerms` on `AgentAdapter`, a `TermsGate` that remembers acknowledged terms per
   version in `acknowledgements.json` (written through a new `AtomicFile`), with tests.
2. `adapters:base`: a `locate()` hook so an adapter can look for more than one binary name.
3. `adapters/antigravity`: `AntigravityAdapter` on `AcpAgentAdapter`, binary and arguments from
   the ACP registry entry `antigravity-acp`, flag `antigravity`, terms notice.
4. A synthetic ACP fixture; tests for a turn, the Linux argument, the Windows name, the flag and
   the terms.
5. Register it in `app`; extend `AdaptersTest`.
6. `./gradlew ktlintFormat` and `./gradlew check -Pocto.allNativeTargets=true`.
