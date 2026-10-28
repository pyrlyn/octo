---
status: todo
priority: P2
complexity: 2
readiness: 0%
agent:
---

# T46. Start a task from a GitHub issue or pull request

Most work starts from an issue or from a PR that needs a fix, and today the user copies its text
into the new-task dialog by hand. T3 Code opens a draft from a PR reference and triages issues from
the command palette ([prompt-links.md](https://github.com/pingdotgg/t3code/blob/125b61954d/docs/user/prompt-links.md), [7e884b2](https://github.com/pingdotgg/t3code/commit/7e884b2df6)).

Done means:

- The new-task dialog and the command palette take an issue or PR reference (`#123`, a URL).
- Octo reads its title, body and, for a PR, the branch and failing checks through `gh`
  (`GitHubCli`); a PR task's worktree starts from the PR's branch.
- The first prompt is a draft the user edits before sending; nothing runs on its own.
- `git-publish` links the resulting PR back to the issue.
- Tests use a fake `gh`.
