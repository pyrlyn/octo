# T20. Done

The Agents screen, opened from the sidebar, lists every running agent. Each row shows:

- the adapter's SVG icon, or a monogram when there is none;
- the probe's sign-in state as a mask;
- CPU % and resident memory of the agent's own process tree, or `remote` for tasks on another
  machine;
- model, context use and session token totals;
- an icon stop button with a tooltip.

Session figures come from the new `AgentEvent.StatsUpdated`, reported by the ACP, Claude and
Codex adapters. Process figures come from `ProcessMonitor` / `JvmProcessMonitor` through
`OctoCore.processUsage`.

PR: https://github.com/pyrlyn/octo/pull/25

Checked:

- `./gradlew check` passes locally: build, all tests, ktlint and detekt.
- New tests cover:
  - the stats reducer and process sampling (local agents only, resampling, no monitor);
  - the JVM monitor (parsers, CPU %, a live process tree, a gone process, a missing `ps`);
  - the Claude, Codex and Cursor fixtures;
  - the view model's labels, credential masking, stop and number formatting.
- GitHub CI did not start because of an account billing block.
- The screen was not opened in the running app.
