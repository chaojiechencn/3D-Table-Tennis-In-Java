# IntelliJ MCP bridge

Forwards MCP over standard input/output to the Claude Code plugin's local IntelliJ WebSocket
server. A single-file Java program with no dependencies, independent of the game's build.

## Use

JDK 21 runs the source file directly. With this project open in IntelliJ and the Claude Code
plugin enabled, from the repository root:

```bash
java tools/intellij-mcp-bridge/Bridge.java --workspace "<absolute project path>" --probe
```

For an MCP client, configure `java` as the command with the arguments
`<absolute path>/tools/intellij-mcp-bridge/Bridge.java --workspace <absolute project path>`.
Omit `--probe` for normal forwarding. The workspace defaults to the current directory. Pass an
absolute path: it is compared as text against the IDE's workspace folders, so `.` never matches.

The bridge writes its own WebSocket handshake: the IDE servers close any connection that sends a
`User-Agent`, which `java.net.http` always does.

It replaced a Python script (`bridge.py`, later with a pinned `websocket-client`). Update any
saved MCP client configuration that still runs Python.
