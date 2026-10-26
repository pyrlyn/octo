---
status: in progress
priority: P2
complexity: 4
readiness: 100%
agent: Claude Code / claude-opus-5-5
---

# T13. Agent-to-agent messaging

Optional, behind a flag: agents message and wait on each other through an Octo-provided channel.

Check: Tests with fake agents; the flag hides the feature when off.

## Execution plan

The channel is an MCP server Octo runs on `127.0.0.1` (research §8 item 21: Octo exposes an MCP
server). Each task gets its own bearer token, so a call always knows which task sent it.

1. Core: `McpServer` (name, url, headers) in `SessionSpec` and `Capability.Mcp`. The orchestrator
   adds Octo's server only for local tasks whose adapter has the capability and only when the
   `agent-messaging` flag gave it a provider.
2. Core `messaging`:
   - `AgentMessaging`: tokens, tools, and a delivery loop that hands a queued message to the
     target as a prompt once it is idle.
   - `McpHandler`: JSON-RPC `initialize`, `ping`, `tools/list`, `tools/call`.
   - Tools: `list_tasks`, `send_message`, `wait_for_idle`. The last returns the target's last
     reply, capped below the client's 5-minute HTTP idle timeout.
   - JVM `HttpMcpServer` on the JDK's built-in `com.sun.net.httpserver`, so no new dependency. It
     checks the token and the Origin header, rejects other methods and caps the body.
3. Adapters:
   - ACP sends it in `session/new` only when the agent reports `mcpCapabilities.http`. The token
     travels over stdin.
   - Claude gets `--mcp-config` with `Authorization: Bearer ${OCTO_MCP_TOKEN}` and the token in
     the environment, so it never appears in argv.
   - Codex is left out until its HTTP MCP config is verified.
4. App: the flag starts the server and wires the provider; with the flag off nothing listens.
5. Tests:
   - the handler and the delivery loop with a fake core;
   - the HTTP server with a real HTTP client standing in for an agent, covering a good token, a
     bad token, a bad Origin and a missing route;
   - adapter args and `session/new`;
   - the app's flag gate.
6. Docs, full check, done.md, PR.
