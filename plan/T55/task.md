---
status: todo
priority: P3
complexity: 2
readiness: 0%
agent:
---

# T55. First-run setup

A new user has to find out which agents are installed and signed in, accept vendor terms and add a
repository before Octo is useful. T3 Code runs a setup flow on a new installation
([welcome-wizard.md](https://github.com/pingdotgg/t3code/blob/125b61954d/docs/user/welcome-wizard.md)).

Done means:

- On a fresh Octo home, a setup flow lists the supported agents with installed / version / signed-in
  state from the adapters, links install docs, shows vendor terms where needed and asks for a first
  repository.
- It can be closed at any time and reopened from the palette.
- The view model has tests.
