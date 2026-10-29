---
status: in progress
priority: P2
complexity: 2
readiness: 90%
agent: Claude Code / claude-opus-5-5
---

# T61. GitHub Actions workflows in Kotlin

The CI workflows are hand-written YAML, so a typo in an input or an action version only shows up
when GitHub runs them. Write them in Kotlin with
[github-workflows-kt](https://github.com/typesafegithub/github-workflows-kt) (approved by the
creator): each workflow is a `.main.kts` script that generates its YAML, and a job the library adds
fails CI when the committed YAML no longer matches the script.

Done: every `.github/workflows/*.yml` has a `.main.kts` source; the generated YAML is committed and
does what the old one did (same triggers, permissions, runner, action versions and commands); the
consistency check job runs before `check`; the script is ktlint clean; `toolchain.md` and the README
say what is used and how to regenerate.

## Choice

Checked 2026-10-29:

- github-workflows-kt: Apache-2.0, latest release 4.0.0 on Maven Central
  (https://repo1.maven.org/maven2/io/github/typesafegithub/github-workflows-kt/maven-metadata.xml),
  last push 2026-10-19, not archived (GitHub API,
  https://api.github.com/repos/typesafegithub/github-workflows-kt). `workflow(targetFileName = …)`
  and the generated `check_yaml_consistency` job are in its v4.0.0 sources
  (`dsl/WorkflowBuilder.kt`, `yaml/ConsistencyCheckJob.kt`); exact action versions in binding
  coordinates are documented in `docs/user-guide/using-actions.md`.
- krzema12/github-actions-kotlin-dsl is the same project under its old name (the GitHub API
  redirects it to typesafegithub/github-workflows-kt).
- No other maintained Kotlin generator for GitHub Actions turned up; the other generators found
  are TypeScript or Pkl (**unverified**, from a blog roundup), and the task asks for Kotlin.

## Plan

1. `.github/workflows/ci.main.kts`: library 4.0.0, action bindings from the bindings server at the
   exact pinned versions (`actions/checkout@v7.0.1`, `actions/setup-java@v6.1.0`,
   `gradle/actions/setup-gradle@v6.4.0`), `targetFileName = "ci.yml"` so the file keeps its name.
   The consistency check job checks out with the same `actions/checkout@v7.0.1`.
2. Triggers stay as they are (push to `main` only), as the creator decided.
3. Regenerate `ci.yml` with the Kotlin CLI; diff it against the old file and confirm the only
   changes are quoting, the header comment, step ids and the added consistency check job.
4. Lint the script with ktlint 1.8.0 (the version shared-settings uses); detekt and the Gradle build
   do not see `.github`, so they are unaffected.
5. `toolchain.md`: Kotlin CLI program row, a table for the script's Maven dependencies. README:
   how to regenerate.
6. PR; the consistency check itself runs on the first push to `main`.
