# IntelliJ MCP bridge

Forwards MCP over standard input/output to the Claude Code plugin's local IntelliJ
WebSocket server. This optional Python tool is independent of the Java build.

## Use

With Python 3 installed and this project open in IntelliJ with the Claude Code
plugin enabled, run from the repository root:

```powershell
python tools/intellij-mcp-bridge/bridge.py --workspace . --probe
```

For an MCP client, configure Python as the command and pass the absolute path to
`tools/intellij-mcp-bridge/bridge.py`, followed by `--workspace` and the absolute
project path. Omit `--probe` for normal forwarding. The workspace defaults to the
current directory if it is not supplied.

The bridge previously lived at `intellij-mcp-bridge/bridge.py` in the repository
root. Update any saved client configuration that still uses that old path.

## Files

- `bridge.py` — repository-owned forwarding script.
- `vendor/` — the bundled `websocket-client` 1.9.2 distribution, including its
  upstream tests, metadata, and [license](vendor/websocket_client-1.9.2.dist-info/licenses/LICENSE).

Keep the upstream distribution together when updating it. Python bytecode is
regenerated locally and ignored by the repository.
