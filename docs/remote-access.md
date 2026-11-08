# Remote access API and the `octo` CLI

## The API

`"remote-access": true` serves a JSON API for a phone or another computer at
`http://127.0.0.1:47470/api/v1`; `"remoteAccess": { "port": 47470 }` changes the port. It listens
on this computer only. Reach it through your own tunnel, such as
`ssh -L 47470:127.0.0.1:47470 <this computer>` or `tailscale serve 47470`, which also encrypts
the traffic. Pair a device from Devices in the sidebar: Octo shows a one-time code that works for
five minutes and five tries. `POST /api/v1/pair` with `{"code", "name"}` returns the device's
token; every other call sends it as `Authorization: Bearer <token>`. The calls:

- `GET /tasks`, `POST /tasks` with `{repo, baseRef, agent, title, prompt?, options?, machine?, permissionMode?, attachments?}`;
- `GET /tasks/{id}/transcript`;
- `POST /tasks/{id}/prompt` with `{text, attachments?}`, and `POST /tasks/{id}/respond` with `{requestId}` plus
  one of `optionId`, `answers` or `cancelled: true`;

`attachments` is a list of `{name, mimeType?, data}` with each file's bytes in base64, at most 20 MB
a file; a message may be files alone.
- `POST /tasks/{id}/cancel`, `/stop` and `/restart`.
- with `handoff` on, `POST /tasks/{id}/switch` with `{agent, model?, summary?}`, which answers with
  the task on its new agent. A second switch while one is under way answers 409.

Vendor terms are accepted only in the Octo window; until then creating a task answers 409.
`devices.json` keeps only a SHA-256 of each token, and Revoke locks a device out at once.

## Guest links

`"guest-share": true` (with `remote-access`) adds Share to a task: it makes a link a guest opens
in a browser to watch the task and propose messages. A proposal reaches the agent only when you
press Send under it. The link is shown once; a new link or Revoke ends the old one, and only a hash
of its token is kept in `shares.json`. Set `"remoteAccess": { "publicUrl": "https://box.tailnet.ts.net" }`
to your tunnel's address so links point there and its pages may call the API; without it links
point at `127.0.0.1`, for a guest on `ssh -L`. The token sits after `#`, so the browser never
sends it in the address and no log along the tunnel holds it.

## The `octo` command

The `octo` command drives the same API from a terminal or a script. Build it with
`./gradlew :cli:installDist`; it lands in `cli/build/install/octo/bin/octo`. Turn on
`remote-access`, then run `octo pair <code>` with a code from Devices. The URL and token go to
`cli.json` in the Octo home, readable only by you; `OCTO_URL` and `OCTO_TOKEN` override them, and
the token is sent only to the Octo it was paired with.

- `octo task list`;
- `octo task start --base main --agent claude --title "Fix the build" [--repo .] [--prompt …] [--mode auto] [--machine …] [--option key=value] [--attach file]`, which prints the new id;
- `octo task send <id> [message] [--attach file]`; repeat `--attach` for more files, read from
  your current directory;
- `octo task read <id>`, which prints the transcript one entry per line;
- `octo task switch <id> <agent> [--model …] [--summary]`, which hands the task to another agent (needs
  `handoff`) and prints its id, agent and status;
- `octo task wait <id> [--timeout seconds]`, which returns when the turn ends: exit 0 when the task
  is idle, 1 when it failed or stopped, 2 when it waits for an answer, 124 on timeout.

`--json` on `list`, `start`, `read`, `switch` and `wait` prints Octo's JSON instead of text.
