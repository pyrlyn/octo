# T21. Done

- A message sent while the agent works is steered into the running turn when the agent has
  `Capability.Steer` (Codex, through `turn/steer`), otherwise queued on the task.
- The queue is part of `Task`, saved in `state.json`, sent one item at a time when the agent turns
  idle; a failed send puts the item back with a notice. Queued items can be edited and removed
  from the task screen.

PR: https://github.com/pyrlyn/octo/pull/29

Checked with `./gradlew check` (ktlint, detekt, core JVM and macOS, adapters, UI). New tests cover
queueing until idle, edit and remove, steer, a refused steer falling back to the queue, the queue
surviving a restart, and the Codex `steer.transcript` fixture. GitHub CI did not run: the account's
billing blocked every job.
