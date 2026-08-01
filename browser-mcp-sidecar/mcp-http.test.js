import { strict as assert } from "node:assert";
import { spawn } from "node:child_process";
import test from "node:test";

test("streamable http reuses initialized MCP session", async () => {
  const port = 43000 + Math.floor(Math.random() * 1000);
  const child = spawn(process.execPath, ["server.js"], {
    env: {
      ...process.env,
      BROWSER_MCP_HOST: "127.0.0.1",
      BROWSER_MCP_PORT: String(port)
    },
    stdio: ["ignore", "pipe", "pipe"]
  });

  try {
    await waitForHealth(port);
    const initialized = await postMcp(port, initializeBody());
    assert.equal(initialized.response.status, 200);
    const sessionId = initialized.response.headers.get("mcp-session-id");
    assert.ok(sessionId, "initialize should return mcp-session-id");

    const tools = await postMcp(port, listToolsBody(), sessionId);
    assert.equal(tools.response.status, 200);
    const toolNames = tools.body.result.tools.map((tool) => tool.name);
    assert.ok(toolNames.includes("browser_open"));
    assert.ok(toolNames.includes("browser_read_page"));
    assert.ok(toolNames.includes("browser_current_state"));
    assert.ok(toolNames.includes("browser_wait_for"));
    assert.ok(toolNames.includes("browser_reset"));
  } finally {
    child.kill();
    await onceExit(child);
  }
});

async function waitForHealth(port) {
  const deadline = Date.now() + 10_000;
  while (Date.now() < deadline) {
    try {
      const response = await fetch(`http://127.0.0.1:${port}/health`);
      if (response.ok) {
        return;
      }
    } catch {
      await new Promise((resolve) => setTimeout(resolve, 100));
    }
  }
  throw new Error("sidecar health check timed out");
}

async function postMcp(port, body, sessionId = "") {
  const response = await fetch(`http://127.0.0.1:${port}/mcp`, {
    method: "POST",
    headers: {
      "content-type": "application/json",
      accept: "application/json, text/event-stream",
      "mcp-protocol-version": "2025-03-26",
      ...(sessionId ? { "mcp-session-id": sessionId } : {})
    },
    body: JSON.stringify(body)
  });
  return { response, body: await parseBody(response) };
}

async function parseBody(response) {
  const text = await response.text();
  if (text.trim().startsWith("data:")) {
    const json = text.split(/\r?\n/)
      .map((line) => line.trim())
      .find((line) => line.startsWith("data:"))
      ?.slice("data:".length)
      .trim();
    return JSON.parse(json);
  }
  return JSON.parse(text);
}

function initializeBody() {
  return {
    jsonrpc: "2.0",
    id: "init-1",
    method: "initialize",
    params: {
      protocolVersion: "2025-03-26",
      capabilities: {},
      clientInfo: { name: "sidecar-test", version: "0.1.0" }
    }
  };
}

function listToolsBody() {
  return {
    jsonrpc: "2.0",
    id: "tools-1",
    method: "tools/list",
    params: {}
  };
}

function onceExit(child) {
  if (child.exitCode !== null || child.signalCode !== null) {
    return Promise.resolve();
  }
  return new Promise((resolve) => child.once("exit", resolve));
}
