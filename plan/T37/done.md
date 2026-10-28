# T37. Done

A guest watches a task through a link and proposes messages. Nothing reaches the agent until the owner sends it. It runs over the remote-access tunnel behind the `guest-share` flag, which needs `remote-access`.

- **`Shares` (core).** Keeps one link per task in `shares.json`, stored as a SHA-256 of a 256-bit token. A new link or Revoke ends the old one.
  - Proposals live in memory: at most 10 waiting and 50 kept per link, 4000 characters each.
  - Approving settles a proposal before `sendPrompt`, so it is sent once. A failed send leaves it waiting.
- **`RemoteApi`.** `GET /api/v1/guest` and `POST /api/v1/guest/proposals` take a share token only, never a device token.
- **`RemoteHttpServer`.** Serves the static page at `/share` under a CSP with no outside sources. The page renders text only.
  - The token sits in the URL fragment, so no log along the tunnel holds it.
- **`remoteAccess.publicUrl`.** Links point there, and its origin may call the loopback server. Without it, links point at `127.0.0.1` for a guest on `ssh -L`.
- **UI.** A Share button, a dialog that shows the link once with New link and Revoke, and the waiting proposals with Send and Reject.
- **Docs.** README and `docs/architecture.md`.

PR: https://github.com/pyrlyn/octo/pull/48

Checked with `./gradlew check` (ktlint, detekt, JVM and macOS arm64 tests): `SharesTest`, the guest routes in `RemoteApiTest`, the page and origin in `RemoteHttpServerTest`, `RemoteAccessConfigTest` and `ShareViewModelTest`.
