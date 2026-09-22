# Developer tools

These tools are optional and separate from the Java game and its build.

## IntelliJ MCP bridge

[`intellij-mcp-bridge/`](intellij-mcp-bridge/README.md) forwards an MCP client to the
Claude Code plugin in a local IntelliJ instance. Its one Python dependency is pinned in
`requirements.txt` and installed into a tool-local virtual environment; it is not a game
dependency.
