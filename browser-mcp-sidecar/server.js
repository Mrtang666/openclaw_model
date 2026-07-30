import { randomUUID } from "node:crypto";
import { mkdir, writeFile } from "node:fs/promises";
import http from "node:http";
import { join } from "node:path";
import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { StdioClientTransport } from "@modelcontextprotocol/sdk/client/stdio.js";
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StreamableHTTPServerTransport } from "@modelcontextprotocol/sdk/server/streamableHttp.js";
import { z } from "zod";
import { structuredFromEvaluateResult, textFromContent } from "./tool-result.js";

const host = process.env.BROWSER_MCP_HOST || "0.0.0.0";
const port = Number(process.env.BROWSER_MCP_PORT || 3333);
const screenshotDir = process.env.BROWSER_SCREENSHOT_DIR || "/data/screenshots";
const userDataDir = process.env.CHROME_USER_DATA_DIR || "/data/chrome-profile";
const headless = (process.env.CHROME_HEADLESS || "true").toLowerCase() !== "false";

let chromeClientPromise;

function chromeClient() {
  if (!chromeClientPromise) {
    chromeClientPromise = (async () => {
      const client = new Client({ name: "openclaw-browser-sidecar", version: "0.1.0" });
      const args = [
        "./node_modules/chrome-devtools-mcp/build/src/bin/chrome-devtools-mcp.js",
        "--slim",
        "--no-usage-statistics",
        "--executable-path=/usr/bin/chromium",
        `--user-data-dir=${userDataDir}`,
        "--screenshot-format=png",
        "--chrome-arg=--no-sandbox"
      ];
      if (headless) {
        args.push("--headless");
      }
      const transport = new StdioClientTransport({
        command: "node",
        args
      });
      await client.connect(transport);
      return client;
    })();
  }
  return chromeClientPromise;
}

async function callChromeTool(name, args = {}) {
  const client = await chromeClient();
  return client.callTool({ name, arguments: args });
}

function toolResponse(message, extra = {}) {
  return {
    content: [{ type: "text", text: message }],
    structuredContent: {
      success: extra.success ?? true,
      message,
      ...extra
    }
  };
}

const sessions = new Map();

function createBrowserServer() {
  const server = new McpServer({ name: "openclaw-browser-mcp-sidecar", version: "0.1.0" });

  server.registerTool(
    "browser_open",
    {
      title: "Open Browser Page",
      description: "Open a URL in the managed Chromium browser.",
      inputSchema: { url: z.string().url() }
    },
    async ({ url }) => {
      await callChromeTool("new_page", { url });
      const snapshot = await callChromeTool("take_snapshot", {});
      const text = textFromContent(snapshot);
      return toolResponse("Opened page", {
        url,
        title: firstLine(text),
        pageText: text.slice(0, 2000)
      });
    }
  );

  server.registerTool(
    "browser_click",
    {
      title: "Click Browser Element",
      description: "Click an element by selector or visible text through page script.",
      inputSchema: { target: z.string().min(1) }
    },
    async ({ target }) => {
      const script = `
      () => {
        const target = ${JSON.stringify(target)};
        const bySelector = (() => { try { return document.querySelector(target); } catch { return null; } })();
        const elements = Array.from(document.querySelectorAll('button,a,input,[role="button"],[onclick]'));
        const byText = elements.find((el) => (el.innerText || el.value || el.getAttribute('aria-label') || '').trim().includes(target));
        const el = bySelector || byText;
        if (!el) return { success: false, message: 'Element not found: ' + target };
        el.click();
        return { success: true, message: 'Clicked: ' + target, title: document.title, url: location.href };
      }
    `;
      const result = await callChromeTool("evaluate_script", { function: script });
      const structured = structuredFromEvaluateResult(result);
      return toolResponse(structured.message, structured);
    }
  );

  server.registerTool(
    "browser_type",
    {
      title: "Type Browser Text",
      description: "Type normal text into an input selected by selector or label text.",
      inputSchema: { target: z.string().min(1), text: z.string().min(1) }
    },
    async ({ target, text }) => {
      const script = `
      () => {
        const target = ${JSON.stringify(target)};
        const value = ${JSON.stringify(text)};
        const bySelector = (() => { try { return document.querySelector(target); } catch { return null; } })();
        const controls = Array.from(document.querySelectorAll('input,textarea,[contenteditable="true"]'));
        const byHint = controls.find((el) => {
          const hint = [el.name, el.id, el.placeholder, el.getAttribute('aria-label')].filter(Boolean).join(' ');
          return hint.includes(target);
        });
        const el = bySelector || byHint;
        if (!el) return { success: false, message: 'Input not found: ' + target };
        el.focus();
        if ('value' in el) {
          el.value = value;
          el.dispatchEvent(new Event('input', { bubbles: true }));
          el.dispatchEvent(new Event('change', { bubbles: true }));
        } else {
          el.textContent = value;
          el.dispatchEvent(new InputEvent('input', { bubbles: true, inputType: 'insertText', data: value }));
        }
        return { success: true, message: 'Typed text', title: document.title, url: location.href };
      }
    `;
      const result = await callChromeTool("evaluate_script", { function: script });
      const structured = structuredFromEvaluateResult(result);
      return toolResponse(structured.message, structured);
    }
  );

  server.registerTool(
    "browser_screenshot",
    {
      title: "Take Browser Screenshot",
      description: "Take a screenshot and save it to the sidecar screenshot directory.",
      inputSchema: { name: z.string().optional(), screenshotDir: z.string().optional() }
    },
    async ({ name = "screenshot", screenshotDir: requestedScreenshotDir }) => {
      const directory = safeDirectory(requestedScreenshotDir) || screenshotDir;
      await mkdir(directory, { recursive: true });
      const result = await callChromeTool("take_screenshot", {});
      const content = Array.isArray(result?.content) ? result.content : [];
      const image = content.find((item) => item?.type === "image" && item?.data);
      if (!image) {
        return toolResponse("Screenshot failed: Chrome DevTools MCP did not return image data.", { success: false });
      }
      const safeName = name.replace(/[^a-zA-Z0-9._-]/g, "_").slice(0, 60) || "screenshot";
      const file = join(directory, `${Date.now()}-${safeName}.png`);
      await writeFile(file, Buffer.from(image.data, "base64"));
      return toolResponse("Screenshot captured", { screenshotPath: file });
    }
  );

  server.registerTool(
    "browser_read_page",
    {
      title: "Read Browser Page",
      description: "Read visible text from the current browser page.",
      inputSchema: { maxChars: z.number().int().positive().max(10000).optional() }
    },
    async ({ maxChars = 2000 }) => {
      const result = await callChromeTool("take_snapshot", {});
      const text = textFromContent(result).slice(0, maxChars);
      return toolResponse(text || "No visible page text was found.", { pageText: text });
    }
  );

  return server;
}

function firstLine(text) {
  return (text || "").split(/\r?\n/).map((line) => line.trim()).find(Boolean) || "";
}

function safeDirectory(value) {
  const directory = (value || "").trim();
  if (!directory || directory.includes("\0")) {
    return "";
  }
  return directory;
}

async function handleMcp(req, res) {
  const sessionId = req.headers["mcp-session-id"];
  let session = typeof sessionId === "string" ? sessions.get(sessionId) : undefined;
  if (!session) {
    const server = createBrowserServer();
    const transport = new StreamableHTTPServerTransport({
      sessionIdGenerator: () => randomUUID(),
      enableJsonResponse: true,
      onsessioninitialized: (newSessionId) => {
        sessions.set(newSessionId, session);
      }
    });
    session = { server, transport };
    transport.onclose = () => {
      for (const [storedSessionId, storedSession] of sessions) {
        if (storedSession === session) {
          sessions.delete(storedSessionId);
        }
      }
    };
    await server.connect(transport);
  }
  const { transport } = session;
  await transport.handleRequest(req, res);
}

const app = http.createServer(async (req, res) => {
  try {
    if (req.method === "GET" && req.url === "/health") {
      res.writeHead(200, { "content-type": "application/json" });
      res.end(JSON.stringify({ ok: true }));
      return;
    }
    if (req.url === "/mcp") {
      await handleMcp(req, res);
      return;
    }
    res.writeHead(404, { "content-type": "application/json" });
    res.end(JSON.stringify({ error: "not found" }));
  } catch (error) {
    res.writeHead(500, { "content-type": "application/json" });
    res.end(JSON.stringify({ error: error instanceof Error ? error.message : "sidecar error" }));
  }
});

app.listen(port, host, () => {
  console.log(`browser-mcp-sidecar listening on http://${host}:${port}/mcp`);
});
