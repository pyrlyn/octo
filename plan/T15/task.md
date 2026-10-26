---
status: in progress
priority: P3
complexity: 5
readiness: 100%
agent: Claude Code / claude-opus-5-5
---

# T15. Mobile and remote access

Optional, behind a flag: expose the core API to a remote client with pairing and token auth.

Check: API tests with a fake client; the flag keeps the server off.

## Execution plan

The creator's decision: the server listens on `127.0.0.1` only. A phone or another computer
reaches it through the user's own tunnel (`ssh -L`, `tailscale serve`), which brings the
encryption, so Octo has no TLS code.

1. Core, common:
   - `io/JsonListFile`: a versioned JSON list in an `AtomicFile`. A file that cannot be read is
     warned about and never overwritten. `FilePresetLibrary` moves onto it.
   - `access/Devices`: paired devices in `devices.json`, holding only a SHA-256 of each token.
     - `startPairing()` gives a one-time 8-character code that expires after 5 minutes and dies
       after 5 wrong tries.
     - `pair(code, name)` returns a fresh 256-bit token once.
     - `device(token)` looks a token up; `revoke(id)` removes a device.
   - `access/RemoteApi`: the transport-free JSON API over `OctoCore`, under `/api/v1`.
     - `POST /pair` is the only call without a token.
     - `GET /tasks` and `GET /tasks/{id}/transcript`.
     - `POST /tasks`, plus `prompt`, `respond`, `cancel`, `stop` and `restart` on a task.
     - Errors map to 400, 401, 404, 405 and 409. Pending vendor terms give 409: terms are
       accepted only in the window.
   - The `remote-access` flag and `remoteAccess.port` in `config.json` (default 47470).
2. Core, JVM: one `LoopbackHttpServer` with the Origin check, body cap and thread pool, shared
   by the MCP server and the new `RemoteHttpServer`.
3. App: with the flag on, open the devices and start the server; a port in use is a warning.
   With the flag off, nothing listens and nothing is read.
4. UI, MVVM: a Devices dialog from the sidebar. It shows the address, the tunnel hint, a
   pairing code with its expiry, and the paired devices with Revoke.
5. Tests:
   - pairing: code expiry, the try limit, a token works only once paired, revoke, hashed storage;
   - the API routes and errors with a fake core;
   - the HTTP server with the JDK HTTP client as the fake remote client;
   - the view model;
   - the app's flag gate.
6. Docs, done.md, PR.
