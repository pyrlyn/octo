# T25. Done

- `worktreeSetup` in `config.json` maps a repository's top-level path to a shell command. The
  creator chose Octo's config over a file in the repository, so a cloned repository cannot run
  anything on its own.
- Octo runs the command with `/bin/sh -c` in each new worktree, forks included, before the agent
  starts, through the machine's own launcher (over ssh for a remote task). The transcript shows it
  as a tool entry with the output's tail. A non-zero exit, an error or the 30-minute timeout marks
  the task Failed and the agent does not start.
- `ProcessLauncher.run` kills the command when it is cancelled.

PR: https://github.com/pyrlyn/octo/pull/33

Checked with `./gradlew check`: 375 tests, 0 failures, ktlint and detekt clean. New tests cover
success, failure, timeout, a fork and no command in the orchestrator, a real `/bin/sh` run in
`GitWorkspaceTest`, and config parsing. GitHub CI did not run: the account's billing blocked every
job.
