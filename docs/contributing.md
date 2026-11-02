# Contributing

Code, docs, comments, commits and PR text are in English. See [AGENTS.md](../AGENTS.md) for the project rules and [building.md](building.md) for the toolchain.

## Checks

`./gradlew check` builds, tests and runs ktlint and detekt. `./gradlew ktlintFormat` fixes formatting. CI also builds every Kotlin/Native target with
`-Pocto.allNativeTargets=true`; locally only the host's native target is built.

## CI workflows

The CI workflows are written in Kotlin with
[github-workflows-kt](https://github.com/typesafegithub/github-workflows-kt): each
`.github/workflows/<name>.main.kts` generates the YAML next to it, which GitHub runs. Edit the
script, never the YAML, then regenerate and commit both; CI fails when they differ. It needs the
Kotlin command-line compiler (`kotlin`) and a JDK:

```bash
.github/workflows/ci.main.kts
```
