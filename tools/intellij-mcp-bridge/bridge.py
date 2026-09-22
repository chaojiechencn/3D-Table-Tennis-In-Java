"""Forward MCP stdio to the local IntelliJ Claude Code plugin WebSocket."""
import argparse
import json
import os
from pathlib import Path
import sys
import threading

try:
    import websocket
except ImportError:
    websocket = None

MISSING_DEPENDENCY = """websocket-client is not installed for this Python ({python}).
Install it into the bridge's virtual environment, from the repository root:
  python -m venv tools/intellij-mcp-bridge/.venv
  tools/intellij-mcp-bridge/.venv/Scripts/python -m pip install -r tools/intellij-mcp-bridge/requirements.txt   (Windows)
  tools/intellij-mcp-bridge/.venv/bin/python -m pip install -r tools/intellij-mcp-bridge/requirements.txt       (macOS/Linux)
then run bridge.py with that environment's python."""


def normalized(path):
    path = str(path).replace("\\", "/").lower().rstrip("/")
    if path.startswith("/mnt/") and len(path) > 6:
        path = path[5] + ":" + path[6:]
    return path


def connect(workspace):
    candidates = []
    for path in (Path.home() / ".claude" / "ide").glob("*.lock"):
        try:
            data = json.loads(path.read_text(encoding="utf-8"))
            if data.get("transport") != "ws" or "IntelliJ" not in data.get("ideName", ""):
                continue
            folders = [normalized(p) for p in data.get("workspaceFolders", [])]
            if normalized(workspace) not in folders:
                continue
            candidates.append((path, data))
        except (OSError, ValueError):
            continue
    for path, data in sorted(candidates, key=lambda item: item[0].stat().st_mtime, reverse=True):
        try:
            port = int(path.stem)
            connection = websocket.create_connection(
                f"ws://127.0.0.1:{port}",
                header={"x-claude-code-ide-authorization": data["authToken"]},
                timeout=5,
                suppress_origin=True,
                subprotocols=["mcp"],
                http_no_proxy=["127.0.0.1"],
            )
            connection.settimeout(None)
            return connection
        except (OSError, ValueError, KeyError, websocket.WebSocketException) as error:
            detail = str(error).replace(data.get("authToken", "<none>"), "[redacted]")
            print(f"IntelliJ port {path.stem}: {type(error).__name__}: {detail}", file=sys.stderr)
            continue
    raise RuntimeError("No reachable IntelliJ MCP server for this workspace. Open IntelliJ with the Claude Code plugin enabled.")


def probe(connection):
    def request(identifier, method, params):
        connection.send(json.dumps({"jsonrpc": "2.0", "id": identifier, "method": method, "params": params}))
        while True:
            response = json.loads(connection.recv())
            if response.get("id") == identifier:
                if "error" in response:
                    raise RuntimeError(str(response["error"]))
                return response["result"]

    connection.settimeout(20)
    initialized = request(1, "initialize", {"protocolVersion": "2024-11-05", "capabilities": {}, "clientInfo": {"name": "codex-intellij-bridge", "version": "1.0"}})
    print(json.dumps({"serverInfo": initialized.get("serverInfo")}))
    connection.send(json.dumps({"jsonrpc": "2.0", "method": "notifications/initialized"}))
    result = request(2, "tools/list", {})
    print(json.dumps(result))
    if any(tool["name"] == "getDiagnostics" for tool in result.get("tools", [])):
        print(json.dumps({"diagnostics": request(3, "tools/call", {"name": "getDiagnostics", "arguments": {}})}))


def forward(connection):
    def receive():
        try:
            while True:
                message = connection.recv()
                if not message:
                    break
                if isinstance(message, bytes):
                    message = message.decode("utf-8")
                sys.stdout.write(json.dumps(json.loads(message), ensure_ascii=True) + "\n")
                sys.stdout.flush()
        except (OSError, ValueError, websocket.WebSocketException):
            pass
        print("IntelliJ connection closed; restart this MCP server to reconnect.", file=sys.stderr)
        os._exit(1)

    threading.Thread(target=receive, daemon=True).start()
    for line in sys.stdin:
        if line.strip():
            connection.send(line.strip())


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--workspace", default=str(Path.cwd()))
    parser.add_argument("--probe", action="store_true")
    args = parser.parse_args()
    if websocket is None:
        raise RuntimeError(MISSING_DEPENDENCY.format(python=sys.executable))
    connection = connect(args.workspace)
    try:
        if args.probe:
            probe(connection)
        else:
            forward(connection)
    finally:
        connection.close()


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print(str(error), file=sys.stderr)
        sys.exit(1)
