# T2. Done

`docs/architecture.md` defines the toolchain, the modules and their dependency direction, the
adapter SPI and registry, the status reducer, feature flags, the `OctoCore` API, persistence,
the trust rules and the test strategy. All runtime libraries are first-party JetBrains libraries;
ACP and JSON-RPC are implemented in `adapters:base` because the official Kotlin ACP SDK is
JVM-only.

Checked: the creator authorised merging once green; this is a docs-only change.
