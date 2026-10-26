# T11. Done

## What was done

- `core`
  - `CoreFlags.GIT_PUBLISH` (`git-publish`, off by default) is the first core flag.
  - `CommandPath` finds a CLI once on the launcher's `PATH`. `Git` now uses it, and the new
    `GitHubCli` does too, so the lookup code exists once.
  - `TaskPublisher` is a separate interface from `OctoCore`, so the core API does not change with
    the flag off. `GitTaskPublisher` looks the task up in `OctoState` and runs these steps in
    its worktree:
    1. When `git status --porcelain` shows changes: `git add -A` and `git commit`.
    2. `git push -u origin <branch>`.
    3. `gh pr list --head <branch> --state open`; an open PR is reused.
    4. Otherwise `gh pr create --base <baseRef> --head <branch>`.
    It returns the commit (or null), the PR URL and whether the PR was created.
  - Guards:
    - A blank title is refused, and so is a blank commit message when there is something to
      commit. Both are refused before git or gh runs.
    - User-typed values are passed as `--flag=value`, so a value starting with `-` cannot become
      an option.
    - Only an `https://` URL from gh's output is accepted, because the UI opens it on click.
  - Octo never handles credentials: `git` and `gh` use the user's own login, hooks and signing.
- `ui`
  - `PublishViewModel`: commit message and PR title, both prefilled with the task title, plus
    the description, running state, result and error.
  - `PublishDialog`: the form, then the result with the commit and a link to the PR.
  - The task header shows Publish only when `ViewModels` has a publisher. The button is
    disabled while a turn is running.
- `app`: `Octo.open` creates the publisher only when the flag is on.
- README: how to turn the flag on.

## How it was checked

- `./gradlew check -Pocto.allNativeTargets=true`: all green.
- New tests:
  - `TaskPublisherTest` (JVM, 2) uses a temp repo, a bare `origin`, and a fake `gh` script first
    on a temp `PATH`. `HOME`, `XDG_CONFIG_HOME` and `GH_CONFIG_DIR` are temp dirs, and git
    config is sealed off. The test checks:
    - the commit is pushed to the remote branch;
    - exactly one `pr create` runs, with the expected arguments;
    - a second publish with a clean worktree reuses the open PR and makes no commit;
    - a missing message or title, or an unknown task, is refused without calling gh.
  - `ViewModelTest`: the publish form, its error and its result, and that it is hidden without
    a publisher.
  - `OctoTest` (app): the publisher exists only with the flag. A broken `config.json` starts
    with defaults and a warning.
- CI on `pyrlyn/octo` is blocked by GitHub billing, so the PR has no CI run.

## Not verified

- Not run against real GitHub with a real `gh` login; the tests use a fake `gh`.
- The dialog was not inspected visually.

PR: https://github.com/pyrlyn/octo/pull/14
