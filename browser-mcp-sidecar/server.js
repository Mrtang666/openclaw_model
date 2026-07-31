import { randomUUID } from "node:crypto";
import { mkdir, readFile, rm, writeFile } from "node:fs/promises";
import http from "node:http";
import { isAbsolute, join } from "node:path";
import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { StdioClientTransport } from "@modelcontextprotocol/sdk/client/stdio.js";
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StreamableHTTPServerTransport } from "@modelcontextprotocol/sdk/server/streamableHttp.js";
import { z } from "zod";
import { structuredFromEvaluateResult, textFromContent, valueFromEvaluateResult } from "./tool-result.js";

const host = process.env.BROWSER_MCP_HOST || "0.0.0.0";
const port = Number(process.env.BROWSER_MCP_PORT || 3333);
const screenshotDir = process.env.BROWSER_SCREENSHOT_DIR || "/data/screenshots";
const userDataDir = process.env.CHROME_USER_DATA_DIR || "/data/chrome-profile";
const headless = (process.env.CHROME_HEADLESS || "true").toLowerCase() !== "false";

let chromeClientPromise;

function chromeClient() {
  if (!chromeClientPromise) {
    chromeClientPromise = (async () => {
      await cleanupChromeProfileLocks();
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

async function cleanupChromeProfileLocks() {
  await Promise.all([
    rm(join(userDataDir, "SingletonCookie"), { force: true }),
    rm(join(userDataDir, "SingletonLock"), { force: true }),
    rm(join(userDataDir, "SingletonSocket"), { force: true })
  ]);
}

async function callChromeTool(name, args = {}, allowRetry = true) {
  const client = await chromeClient();
  try {
    const result = await client.callTool({ name, arguments: args });
    if (allowRetry && isChromeToolFailure(result)) {
      await resetChromeClient();
      return callChromeTool(name, args, false);
    }
    return result;
  } catch (error) {
    if (!allowRetry) {
      throw error;
    }
    await resetChromeClient();
    return callChromeTool(name, args, false);
  }
}

async function resetChromeClient(clearProfile = false) {
  const clientPromise = chromeClientPromise;
  chromeClientPromise = undefined;
  try {
    const client = await clientPromise;
    await client?.close();
  } catch {
    // Best-effort cleanup before the next call starts a fresh Chrome DevTools MCP client.
  }
  if (clearProfile) {
    if (!isSafeProfileDirectory(userDataDir)) {
      throw new Error(`Refusing to clear unsafe Chrome profile directory: ${userDataDir}`);
    }
    await rm(userDataDir, { recursive: true, force: true });
    await mkdir(userDataDir, { recursive: true });
  } else {
    await cleanupChromeProfileLocks();
  }
}

function isSafeProfileDirectory(directory) {
  const value = (directory || "").replace(/\\/g, "/");
  return value === "/data/chrome-profile" || value.startsWith("/data/chrome-profile/");
}

function isChromeToolFailure(result) {
  if (result?.isError) {
    return true;
  }
  const text = textFromContent(result);
  return text.includes("Protocol error") || text.includes("Target closed");
}

async function evaluatePageScript(functionBody, args = []) {
  return callChromeTool("evaluate", {
    script: `(${functionBody})()`,
    ...(args.length > 0 ? { args } : {})
  });
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

function browserStateScript() {
  return `
    () => {
      const text = (document.body?.innerText || '').replace(/\\s+/g, ' ').trim();
      const summarize = (el) => {
        const labelText = Array.from(el.labels || []).map((label) => label.innerText || label.textContent || '').join(' ');
        const fields = [
          el.tagName?.toLowerCase(),
          el.type ? 'type=' + el.type : '',
          el.name ? 'name=' + el.name : '',
          el.id ? 'id=' + el.id : '',
          el.placeholder ? 'placeholder=' + el.placeholder : '',
          el.getAttribute('aria-label') ? 'aria=' + el.getAttribute('aria-label') : '',
          labelText ? 'label=' + labelText : '',
          el.innerText || el.textContent || el.value || ''
        ];
        return fields.filter(Boolean).join(' | ').replace(/\\s+/g, ' ').trim().slice(0, 160);
      };
      const inputs = Array.from(document.querySelectorAll('input,textarea,[contenteditable="true"]'))
        .filter((el) => !el.disabled && el.type !== 'hidden')
        .map(summarize)
        .filter(Boolean)
        .slice(0, 30);
      const buttons = Array.from(document.querySelectorAll('button,[role="button"],input[type="button"],input[type="submit"],a'))
        .filter((el) => !el.disabled)
        .map(summarize)
        .filter(Boolean)
        .slice(0, 40);
      const lower = text.toLowerCase();
      const isLoginPage = ['login', 'sign in', 'password', '邮箱', '邮件', '登录', '密码'].some((word) => lower.includes(word.toLowerCase()));
      const requiresVerification = ['验证码', '二次验证', 'otp', 'verification code', 'two-factor', '2fa'].some((word) => lower.includes(word.toLowerCase()));
      return {
        success: true,
        message: 'Current page state',
        title: document.title || '',
        url: location.href,
        pageText: text.slice(0, 4000),
        inputs,
        buttons,
        isLoginPage,
        requiresVerification
      };
    }
  `;
}

async function currentBrowserState(message = "Current page state", success = true, extra = {}) {
  const result = await evaluatePageScript(browserStateScript());
  const structured = structuredFromEvaluateResult(result);
  return {
    ...structured,
    ...extra,
    success,
    message
  };
}

function waitConditionScript(condition, value) {
  return `
    () => {
      const condition = ${JSON.stringify(condition)};
      const value = ${JSON.stringify(value)};
      const title = document.title || '';
      const url = location.href;
      const pageText = (document.body?.innerText || '').replace(/\\s+/g, ' ').trim();
      let matched = false;
      let observed = '';
      if (condition === 'url') {
        observed = url;
        matched = url.includes(value);
      } else if (condition === 'title') {
        observed = title;
        matched = title.toLowerCase().includes(value.toLowerCase());
      } else if (condition === 'text') {
        observed = pageText.slice(0, 4000);
        matched = pageText.toLowerCase().includes(value.toLowerCase());
      } else if (condition === 'selector') {
        observed = value;
        try {
          matched = Boolean(document.querySelector(value));
        } catch {
          matched = false;
        }
      }
      return { success: true, matched, observed, title, url, pageText: pageText.slice(0, 4000) };
    }
  `;
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
      await callChromeTool("navigate", { url });
      const pageState = await evaluatePageScript("() => ({ title: document.title, url: location.href, text: document.body?.innerText || '' })");
      const parsed = valueFromEvaluateResult(pageState);
      const text = textFromContent(pageState);
      const pageTitle = parsed && typeof parsed === "object" ? parsed.title || firstLine(parsed.text) : firstLine(text);
      return toolResponse("Opened page", {
        url,
        title: pageTitle,
        pageText: (parsed?.text || text).slice(0, 2000)
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
        const normalizedTarget = target.trim().toLowerCase();
        const bySelector = (() => { try { return document.querySelector(target); } catch { return null; } })();
        const elements = Array.from(document.querySelectorAll('button,a,input,[role="button"],[onclick],[type="submit"]'));
        const byText = elements.find((el) => {
          const text = [
            el.innerText,
            el.textContent,
            el.value,
            el.getAttribute('aria-label'),
            el.getAttribute('title')
          ].filter(Boolean).join(' ').trim().toLowerCase();
          return text.includes(normalizedTarget);
        });
        const el = bySelector || byText;
        if (!el) return { success: false, message: 'Element not found: ' + target };
        el.click();
        return { success: true, message: 'Clicked: ' + target, title: document.title, url: location.href };
      }
    `;
      const result = await evaluatePageScript(script);
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
        const normalizedTarget = target.trim().toLowerCase();
        const bySelector = (() => { try { return document.querySelector(target); } catch { return null; } })();
        const controls = Array.from(document.querySelectorAll('input,textarea,[contenteditable="true"]'));
        const byHint = controls.find((el) => {
          const labelText = Array.from(el.labels || []).map((label) => label.innerText || label.textContent || '').join(' ');
          const wrapperLabel = el.closest('label');
          const hint = [
            el.name,
            el.id,
            el.type,
            el.autocomplete,
            el.placeholder,
            el.getAttribute('aria-label'),
            el.getAttribute('title'),
            labelText,
            wrapperLabel ? wrapperLabel.innerText || wrapperLabel.textContent : ''
          ].filter(Boolean).join(' ').toLowerCase();
          return hint.includes(normalizedTarget);
        });
        const el = bySelector || byHint;
        if (!el) return { success: false, message: 'Input not found: ' + target };
        el.focus();
        if ('value' in el) {
          const prototype = Object.getPrototypeOf(el);
          const descriptor = Object.getOwnPropertyDescriptor(prototype, 'value');
          if (descriptor && descriptor.set) {
            descriptor.set.call(el, value);
          } else {
            el.value = value;
          }
          el.dispatchEvent(new Event('input', { bubbles: true }));
          el.dispatchEvent(new Event('change', { bubbles: true }));
        } else {
          el.textContent = value;
          el.dispatchEvent(new InputEvent('input', { bubbles: true, inputType: 'insertText', data: value }));
        }
        return { success: true, message: 'Typed text', title: document.title, url: location.href };
      }
    `;
      const result = await evaluatePageScript(script);
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
      const result = await callChromeTool("screenshot", {});
      if (isChromeToolFailure(result)) {
        const errorText = textFromContent(result) || "Chrome DevTools MCP target is not available.";
        return toolResponse(`Screenshot failed: ${errorText}`, { success: false });
      }
      const content = Array.isArray(result?.content) ? result.content : [];
      const image = content.find((item) => item?.type === "image" && item?.data);
      const safeName = name.replace(/[^a-zA-Z0-9._-]/g, "_").slice(0, 60) || "screenshot";
      const fileName = `${Date.now()}-${safeName}.png`;
      const file = join(directory, fileName);
      let imageBase64 = "";
      if (image) {
        imageBase64 = image.data;
        await writeFile(file, Buffer.from(imageBase64, "base64"));
      } else {
        const sourcePath = firstLine(textFromContent(result));
        if (!sourcePath) {
          return toolResponse("Screenshot failed: Chrome DevTools MCP did not return image data or a file path.", { success: false });
        }
        const bytes = await readFile(sourcePath);
        imageBase64 = bytes.toString("base64");
        await writeFile(file, bytes);
      }
      return toolResponse("Screenshot captured", {
        screenshotPath: file,
        screenshotImageBase64: imageBase64,
        screenshotContentType: "image/png",
        screenshotFileName: fileName
      });
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
      const result = await evaluatePageScript("() => document.body?.innerText || ''");
      const value = valueFromEvaluateResult(result);
      const text = String(value ?? textFromContent(result)).slice(0, maxChars);
      return toolResponse(text || "No visible page text was found.", { pageText: text });
    }
  );

  server.registerTool(
    "browser_current_state",
    {
      title: "Read Current Browser State",
      description: "Return the current URL, title, visible text summary, inputs, buttons, and login hints.",
      inputSchema: {}
    },
    async () => {
      const state = await currentBrowserState();
      return toolResponse(state.message, state);
    }
  );

  server.registerTool(
    "browser_wait_for",
    {
      title: "Wait For Browser Condition",
      description: "Wait until the current page URL, title, text, or selector matches a value.",
      inputSchema: {
        condition: z.enum(["url", "title", "text", "selector"]),
        value: z.string().min(1),
        timeoutMs: z.number().int().positive().max(60_000).optional()
      }
    },
    async ({ condition, value, timeoutMs = 15_000 }) => {
      const deadline = Date.now() + Math.min(Math.max(timeoutMs, 1_000), 60_000);
      let last = {};
      while (Date.now() <= deadline) {
        const result = await evaluatePageScript(waitConditionScript(condition, value));
        last = structuredFromEvaluateResult(result);
        if (last.matched) {
          return toolResponse("Wait condition met", {
            ...last,
            condition,
            value,
            success: true
          });
        }
        await sleep(250);
      }
      const state = await currentBrowserState("Wait condition timed out", false, {
        condition,
        value,
        observed: last.observed || ""
      });
      return toolResponse(state.message, state);
    }
  );

  server.registerTool(
    "browser_reset",
    {
      title: "Reset Browser Session",
      description: "Restart the managed Chromium connection, optionally clearing the Chrome profile.",
      inputSchema: { clearProfile: z.boolean().optional() }
    },
    async ({ clearProfile = false }) => {
      await resetChromeClient(clearProfile);
      return toolResponse("Browser reset completed", {
        clearProfile
      });
    }
  );

  return server;
}

function firstLine(text) {
  return (text || "").split(/\r?\n/).map((line) => line.trim()).find(Boolean) || "";
}

function safeDirectory(value) {
  const directory = (value || "").trim();
  if (!directory || directory.includes("\0") || !isAbsolute(directory)) {
    return "";
  }
  return directory;
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
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
