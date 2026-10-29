# T61. Done

`.github/workflows/ci.main.kts` (github-workflows-kt 4.0.0) generates `.github/workflows/ci.yml`.
The YAML keeps the push-to-`main` trigger, `contents: read`, `ubuntu-latest`, the pinned actions
and the Gradle command, and gains the library's `check_yaml_consistency` job, which `check` needs.
`toolchain.md` lists the Kotlin CLI, the library and the action bindings; the README says how to
regenerate.

PR: https://github.com/pyrlyn/octo/pull/63

Checked:
- The script ran with Kotlin 2.4.21 and JDK 25.
- The consistency job was simulated locally: the YAML was deleted and regenerated, and the
  result matched byte for byte.
- ktlint 1.8.0 is clean on the script, and `./gradlew ktlintCheck detekt` is clean.

The consistency job first runs on GitHub on the push to `main`.
