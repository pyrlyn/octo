---
status: in progress
priority: P1
complexity: 2
readiness: 100%
agent: Claude Code / claude-opus-5-5
---

# T17. Inherit build settings from ci-diffuser

Octo takes its lint and settings conventions from the shared `apps/ci-diffuser` build instead of
keeping its own copy, so every Kotlin project lints and resolves the same way.

Done means: `ci.diffuser.settings` (repositories, optional S3/R2 remote cache), `ci.diffuser.ktlint`
and `ci.diffuser.detekt` replace Octo's own `octo.lint` and repository blocks, Octo's detekt
overrides stay in `config/detekt/detekt.yml`, and the full check is green. The creator chose these
three plugins only (`ci.diffuser.kmp` and `ci.diffuser.native` do not fit Octo's JVM/Compose
modules and its mingwX64 target), a local `../ci-diffuser` checkout with no CI wiring, and to do it
before T13.

Check: `./gradlew check -Pocto.allNativeTargets=true` is green with the ci-diffuser plugins applied.

## Execution plan

1. `settings.gradle.kts`: `includeBuild` ci-diffuser and its settings plugin from `../ci-diffuser`
   (`CI_DIFFUSER_DIR` overrides it, as in rtok-kotlin); apply `com.github.burrunan.s3-build-cache`
   1.9.10 and `ci.diffuser.settings`; drop the repository block it now provides.
2. A root `build.gradle.kts` applies `ci.diffuser.ktlint` and `ci.diffuser.detekt` to every
   Kotlin module; remove `octo.lint` and the ktlint/detekt plugins from `buildSrc`.
3. `gradle.properties`: add the template's generic defaults (daemon, GC, Kotlin daemon, native
   incremental).
4. Update `toolchain.md`, README; full check; done.md; PR.
