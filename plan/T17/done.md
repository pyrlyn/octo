# T17. Done

## What was done

- `settings.gradle.kts` includes `../ci-diffuser` and its settings plugin. `CI_DIFFUSER_DIR`
  overrides the path, as in rtok-kotlin. It applies `com.github.burrunan.s3-build-cache` 1.9.10
  and `ci.diffuser.settings`:
  - repositories: mavenCentral and google;
  - an S3/R2 remote cache that stays off unless both `S3_BUILD_CACHE_*` keys are set.

  Octo's own repository block is gone.
- A new root `build.gradle.kts` applies `ci.diffuser.ktlint` and `ci.diffuser.detekt` to every
  Kotlin module (multiplatform or JVM).
  - `buildSrc`'s `octo.lint` and its ktlint/detekt plugin dependencies are removed.
  - The unused ktlint and detekt entries in `gradle/libs.versions.toml` are removed too.
  - No version changed: ci-diffuser pins the same ktlint-gradle 14.2.0, ktlint 1.8.0 and
    detekt 2.0.0-alpha.6.
- Octo's detekt overrides stay in `config/detekt/detekt.yml`. ci-diffuser merges that file after
  its shared one, which turns off detekt's MaxLineLength and WildcardImport (ktlint owns them).
- `gradle.properties` gets the template's generic defaults:
  - ParallelGC;
  - the Gradle daemon;
  - Kotlin daemon heap;
  - native incremental compilation.
- Octo's stricter `.editorconfig` is kept. The template turns off rules only for code that has
  not been cleaned up yet.
- `ci.diffuser.kmp` and `ci.diffuser.native` were not used, by the creator's choice. They apply
  multiplatform, Kotest, KSP and Dokka to every module and know only macosArm64 and Linux, while
  Octo has JVM/Compose modules and a mingwX64 target. Targets and Compose stay in `buildSrc`.
- `toolchain.md` and README: the sibling checkout and the new plugin.

## How it was checked

- `./gradlew check -Pocto.allNativeTargets=true`: green. `detekt` and `ktlintCheck` ran in all 11
  modules.
- Octo's override is applied: `Orchestrator` has 25 functions and passes, where detekt's default
  limit per class is 11.

## Not verified

- CI: by the creator's choice ci-diffuser is wired only locally. A CI checkout of `pyrlyn/octo`
  alone has no `../ci-diffuser`, so CI cannot configure the build until ci-diffuser gets a
  remote. CI is also still blocked by the account's billing.
- The remote build cache, since no keys were set.

PR: https://github.com/pyrlyn/octo/pull/16
