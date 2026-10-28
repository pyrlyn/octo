# T31. Done

PR: https://github.com/pyrlyn/octo/pull/39

## What was done

- **`octo` command.** A new `:cli` module (Clikt) built with `./gradlew :cli:installDist`. It talks to a running Octo only through the `remote-access` loopback API, as a paired device, so that flag gates it. That was the creator's choice over a new flag.
- **Pairing.** `octo pair <code>` stores the URL and token in `cli.json` in the Octo home, mode 0600. `OCTO_URL` and `OCTO_TOKEN` override it, and a stored token goes only to the URL it was paired with.
- **Commands.** `task list`, `task start`, `task send`, `task read` and `task wait`, with `--json` for Octo's JSON. `task wait` exits 0 when idle, 1 when failed or stopped, 2 when blocked on the user, and 124 on timeout.
- **Shared code.**
  - The API bodies moved from `RemoteApi` into public `access/ApiBodies.kt`, shared by the server and the client.
  - `FakeOctoCore` and `FakeCatalog` moved from the ui tests into `:testing`.

## How it was checked

`./gradlew check` is green: ktlint, detekt, and 448 tests.

`OctoTest` runs every command against the real loopback server over `FakeOctoCore`, with the Octo home in a temp dir. It covers:
- pairing and the file mode;
- a wrong code, and the token staying with its URL;
- text and JSON output;
- every exit of `wait`;
- an Octo that is not listening.

The installed binary was also run by hand.
