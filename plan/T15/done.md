# T15. Done

Remote access, behind the `remote-access` flag. With the flag off nothing listens and
`devices.json` is never read.

- `io/JsonListFile`: a versioned JSON list in an `AtomicFile`. A file that cannot be read, or one
  from a newer version, is warned about and never overwritten. `FilePresetLibrary` now uses it,
  with the same `presets.json` format.
- `access/Devices`:
  - Paired devices live in `devices.json`, which keeps only a SHA-256 of each 256-bit token.
  - There is one open pairing code at a time: 8 characters, 5 minutes, 5 wrong tries.
  - A device's name is checked before the code is spent.
  - Revoke locks a device out at once.
  - Randomness and hashing come through `Secrets`; `JvmSecrets` uses `SecureRandom` and
    `MessageDigest`.
- `access/RemoteApi`: a transport-free JSON API under `/api/v1`.
  - `POST /pair` is the only call without a token.
  - The task calls: `GET` and `POST /tasks`, `GET /tasks/{id}/transcript`, and
    `POST /tasks/{id}/prompt|respond|cancel|stop|restart`.
  - Errors map to 400, 401, 404, 405 (with `Allow`), 409 and 500. Pending vendor terms give 409:
    they are accepted only in the window.
- JVM:
  - `http/LoopbackHttpServer` binds `127.0.0.1` only, refuses other web origins (DNS
    rebinding) and bodies over 1 MiB, and runs handlers on a cached pool.
  - `HttpMcpServer` now sits on it, and so does the new `RemoteHttpServer`.
- Config: `remoteAccess.port`, 47470 by default. A bad or taken port, or an unreadable
  `devices.json`, turns remote access off with a warning.
- UI (MVVM): `DevicesViewModel` and the Devices dialog, opened from the sidebar. It shows the
  address, `ssh -L` and `tailscale serve` hints, the code, and the devices with Revoke. Closing the
  dialog ends an open code.
- The shared `bearerToken()` parser is used by agent messaging and the remote API.
- No new dependencies.

Checked with `./gradlew check -Pocto.allNativeTargets=true`: 235 tests, all green; ktlint and
detekt clean. The new tests:

- `DevicesTest`, on the JVM and macOS: one-time codes, case and separators, expiry, the try
  limit, name checks, revoke, hashed storage, broken and newer files;
- `RemoteApiTest`, on the JVM and macOS: auth, a task driven end to end over the API against fake
  agents, error codes, the terms gate;
- `RemoteHttpServerTest`, with the JDK HTTP client as the remote device: JSON over loopback, 401,
  403 for a foreign Origin, 413, a taken port, and the SHA-256 test vector;
- `DevicesViewModelTest`;
- `OctoTest`: the flag gate, the server stopping with Octo, and taken or bad ports.

PR: https://github.com/pyrlyn/octo/pull/19
