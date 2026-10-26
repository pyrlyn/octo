# T13. Done

## What was done

- `core`
  - `agent`: `Capability.Mcp`, `McpServer(name, url, headers)` and `SessionSpec.mcpServers`.
  - `messaging/AgentMessaging`:
    - gives each local task its own random token (two `Uuid.random()`) and the server URL;
    - names a caller only by that token, never by what the agent says about itself;
    - queues a message for its target and hands it over as one prompt once the target is idle and
      not already handed a prompt, so a busy agent is never interrupted;
    - refuses messages to itself, to an unknown or ended task, empty ones, ones over 20,000
      characters and more than 20 waiting for one task;
    - `wait_for_idle` waits until the task is idle with nothing left to deliver, at most 240 s,
      and returns its last finished assistant reply.
  - `messaging/McpHandler`: JSON-RPC `initialize` (protocol 2025-11-25, 2025-06-18 or 2025-03-26),
    `ping`, `tools/list` and `tools/call` for `list_tasks`, `send_message` and `wait_for_idle`.
    A notification gets no answer and an unknown method gets -32601. A bad argument or refused
    message is a tool result with `isError`, so the agent can read it and retry.
  - `messaging/HttpMcpServer` (JVM, the JDK's `com.sun.net.httpserver`): MCP's Streamable HTTP
    transport reduced to a POST with one JSON answer.
    - Listens on `127.0.0.1` on a free port, path `/mcp` only.
    - 401 without a known token. 403 for an `Origin` that is not localhost, against DNS
      rebinding. 405 for anything but POST. 413 for a body over 1 MiB. 202 for notifications.
    - A cached thread pool, since each `wait_for_idle` holds a thread for minutes.
  - `Orchestrator.open(mcp = …)`: a starting agent gets the servers only when its adapter has
    `Capability.Mcp` and the task runs on this computer; the server listens on loopback, so a
    remote agent cannot reach it.
  - The `agent-messaging` flag.
- `adapters`
  - ACP (Cursor, Grok, Antigravity): the servers go into `session/new`, `session/resume` and
    `session/load` as `{type: "http", name, url, headers: [{name, value}]}`. They are sent only
    when the agent reports `mcpCapabilities.http`.
  - Claude: `--mcp-config=<json>`, added next to the user's own servers (no
    `--strict-mcp-config`). Header values are written as `${OCTO_MCP_<i>_<j>}` and the real
    values go into the child's environment, so the token never shows up in the process list.
  - Codex is left out: its HTTP MCP setup has not been checked yet.
- `app`: with the flag on, Octo starts the server, binds its URL and hands the messaging to the
  orchestrator. `Octo.close` stops it. With the flag off nothing listens. If the server cannot
  start, Octo runs without messaging and shows a warning. The packaged app adds the
  `jdk.httpserver` module.
- Test fakes for the orchestrator moved into `core/.../orchestrator/Fakes.kt`, so the
  orchestrator and messaging tests share them.
- README and the architecture flag table.

## Sources (checked 2026-10-26)

- Claude Code MCP config: `--mcp-config` takes JSON files or strings, and an HTTP server is
  `{"type":"http","url":…,"headers":{…}}`. Sources: https://code.claude.com/docs/en/mcp and
  https://code.claude.com/docs/en/cli-reference.
- `${VAR}` expansion is documented only for `.mcp.json`. For `--mcp-config` it was checked by
  hand against Claude Code 2.1.304, offline:
  - `--bare`, a temp `HOME`, a dummy API key and `ANTHROPIC_BASE_URL` pointing at a closed port;
  - a local HTTP listener received `Authorization: Bearer <value>` from the environment variable
    on both `server/discover` and `initialize`.
- ACP HTTP MCP servers: `{type: "http", name, url, headers: [{name, value}]}`, allowed only when
  the agent reports `mcpCapabilities.http`. Source:
  https://agentclientprotocol.com/protocol/session-setup.

## How it was checked

- `./gradlew check -Pocto.allNativeTargets=true`: all green.
- New tests:
  - `AgentMessagingTest` (6):
    - tokens and the caller lookup;
    - no servers for agents without `Capability.Mcp`;
    - delivery only once the target is idle;
    - refused messages;
    - `wait_for_idle` with a timeout and with a reply;
    - the JSON-RPC handler, including Claude's `server/discover` probe.
  - `HttpMcpServerTest` (3): the JDK HTTP client plays the agent against the real server.
    Covers a good token, a missing or wrong token, a foreign `Origin`, a wrong path, an oversized
    body, GET and a notification.
  - `CursorAdapterTest`: `session/new` carries the server when the agent takes HTTP and an empty
    list when it does not.
  - `ClaudeAdapterTest`: the exact `--mcp-config` argument, no token in argv, the token in the
    environment, no `--strict-mcp-config`.
  - `OctoTest`: the server exists only with the flag on and stops with Octo.

## Not checked

- A real two-agent conversation through the running app.
- CI on GitHub: blocked by the account's billing, as for the previous tasks.
