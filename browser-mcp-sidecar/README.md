# OpenClaw Browser MCP Sidecar

Dockerized Chrome DevTools MCP sidecar for OpenClaw browser automation.

## Start

```powershell
docker compose -f browser-mcp-sidecar/compose.yaml up --build
```

## Health Check

```powershell
Invoke-RestMethod http://127.0.0.1:3333/health
```

## Java Configuration

Use local Docker from the host:

```properties
BROWSER_AUTOMATION_ENABLED=true
BROWSER_AUTOMATION_MCP_ENDPOINT=http://127.0.0.1:3333/mcp
BROWSER_AUTOMATION_ALLOW_EXTERNAL_URL=false
BROWSER_AUTOMATION_ALLOWED_HOSTS=localhost,127.0.0.1
```

If the Java app runs in the same Compose network, set:

```properties
BROWSER_AUTOMATION_MCP_ENDPOINT=http://browser-mcp-sidecar:3333/mcp
```

The sidecar runs Chromium inside Docker and does not read the host Chrome profile.
