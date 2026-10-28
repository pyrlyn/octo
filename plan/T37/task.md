---
status: in progress
priority: P3
complexity: 5
readiness: 100%
agent: Claude Code / claude-opus-5-5
---

# T37. Share a task with a guest

A link lets someone else watch a task live and send messages that the owner approves before they
reach the agent, as T3 Code does ([fb76594](https://github.com/pingdotgg/t3code/commit/fb76594)).
Octo has no hosted relay, so the first step is to decide with the creator how a guest reaches the
user's computer.

Done means: a guest with the link sees the task's transcript and status live, can propose a
message, and the owner approves or rejects it; the link can be revoked; tests cover the flow.

## Decisions

- Creator: a guest reaches Octo through the same tunnel as `remote-access`, with a token of
  their own that opens one task and nothing else.
- The link is `<base>/share#<token>`. The token sits in the fragment, so it never reaches the
  server's or the tunnel's request logs. The base is `remoteAccess.publicUrl` (the tunnel's
  address, e.g. `https://box.tailnet.ts.net`), or the loopback URL for an `ssh -L` guest.
- The loopback server refuses pages from other origins (DNS rebinding). `publicUrl`'s origin is
  the one more it accepts, so the guest page, served from the tunnel, can post.

## Execution plan

1. `CoreFlags.GUEST_SHARE` (`guest-share`, needs `remote-access`); `RemoteAccessConfig.publicUrl`
   with validation; `OctoPaths.shares` (`shares.json`).
2. `core/access/Shares.kt`: one share per task, keeping only a SHA-256 of its token. Revoking
   locks the guest out at once. Proposals live in memory, with limits on length, name and
   pending count. Approving sends the text as a prompt.
3. `RemoteApi`: `GET /api/v1/guest` (task title, agent, status, transcript and the guest's own
   proposals) and `POST /api/v1/guest/proposals`, both with the share token. A device token is
   not a share token, and a share token is not a device token.
4. `RemoteHttpServer` serves the guest page at `GET /share`. The page is static, renders text
   with `textContent` only, and is sent under a strict CSP. `LoopbackHttpServer` accepts the
   `publicUrl` origin and lets a response set its own content type.
5. UI: a Share button on the task opens a dialog to create, replace or revoke the link. Pending
   proposals show above the prompt box with Approve and Reject.
6. App wiring, README, architecture.md and `toolchain.md` if anything changes.

Check: `./gradlew check` is green. There are tests for `Shares`, the guest API, the HTTP page
and origin, and the view model.
