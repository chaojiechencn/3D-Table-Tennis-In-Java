# IntelliJ MCP bridge

Forwards MCP over standard input/output to the Claude Code plugin's local IntelliJ
WebSocket server. This optional Python tool is independent of the Java build.

## Install

Python 3 is required. Install the one dependency into a virtual environment kept beside
the tool (it is ignored by Git). From the repository root:

```powershell
# Windows PowerShell
python -m venv tools/intellij-mcp-bridge/.venv
tools/intellij-mcp-bridge/.venv/Scripts/python -m pip install -r tools/intellij-mcp-bridge/requirements.txt
```

```bash
# macOS / Linux / Git Bash
python3 -m venv tools/intellij-mcp-bridge/.venv
tools/intellij-mcp-bridge/.venv/bin/python -m pip install -r tools/intellij-mcp-bridge/requirements.txt
```

`requirements.txt` pins `websocket-client==1.9.2`. Without it, the bridge exits with the
install instructions above.

## Use

With this project open in IntelliJ and the Claude Code plugin enabled, run the bridge with
the environment's Python (`.venv/Scripts/python` on Windows, `.venv/bin/python` elsewhere):

```powershell
tools/intellij-mcp-bridge/.venv/Scripts/python tools/intellij-mcp-bridge/bridge.py --workspace . --probe
```

For an MCP client, configure the environment's Python as the command and pass the absolute
path to `tools/intellij-mcp-bridge/bridge.py`, followed by `--workspace` and the absolute
project path. Omit `--probe` for normal forwarding. The workspace defaults to the current
directory if it is not supplied.

The bridge previously lived at `intellij-mcp-bridge/bridge.py` in the repository root, and
later bundled its dependency under `vendor/`. Update any saved client configuration that
still uses the old path or the system Python.

## Files

- `bridge.py` — repository-owned forwarding script.
- `requirements.txt` — the pinned dependency.
