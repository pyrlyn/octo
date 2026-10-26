---
status: in progress
priority: P2
complexity: 3
readiness: 100%
agent: Claude Code / claude-opus-5-5
---

# T11. Commit, push and open a PR in one action

Optional, behind a flag: commit the task's worktree, push its branch and open a PR through `gh`.

Check: Tests against a temp repo with a fake `gh`; the flag hides the action when off.

## Execution plan

1. Core: `CoreFlags.GIT_PUBLISH` (`git-publish`, off). A shared `CommandPath` resolves a CLI
   once through the launcher; `Git` and the new `GitHubCli` both use it.
2. Core: `TaskPublisher` looks the task up in `OctoState` and runs, in its worktree:
   `git add -A` and `git commit` when there are changes, `git push -u origin <branch>`, then
   `gh pr view <branch>` (an open PR is reused) or `gh pr create --base <baseRef> --head <branch>`.
   It returns the commit (if any) and the PR URL. It is a separate interface, so `OctoCore`
   stays the same with the flag off.
3. UI: `PublishViewModel` (commit message, PR title and body, running, result URL, error) and a
   dialog opened from the task header. `ViewModels` gets an optional publisher; without one the
   button is not shown.
4. App: create the publisher only when the flag is on.
5. Tests: `TaskPublisherTest` against a temp repo with a bare remote and a fake `gh` script on a
   temp `PATH` (temp `HOME`, git config sealed off); view model test; app test that the flag
   gates the publisher.
6. `./gradlew ktlintFormat`, `./gradlew check -Pocto.allNativeTargets=true`.
