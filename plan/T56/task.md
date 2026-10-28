---
status: todo
priority: P3
complexity: 2
readiness: 0%
agent:
---

# T56. Pair a device by QR code

Pairing a phone with remote access (T15) means typing a code. T3 Code shows a QR code with the
pairing URL and opens `t3code://pair` links ([remote-access.md](https://github.com/pingdotgg/t3code/blob/125b61954d/docs/user/remote-access.md),
[dde3fdb](https://github.com/pingdotgg/t3code/commit/dde3fdb5a6)).

Done means:

- The devices dialog shows the pairing URL as a QR code next to the code.
- A QR library is a new third-party dependency: pick a maintained one, wrap it, and get the
  creator's approval before adding it.
- The pairing URL builder has tests.
