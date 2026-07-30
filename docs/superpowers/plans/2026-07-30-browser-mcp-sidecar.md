# Browser MCP Sidecar Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Dockerized Chrome DevTools MCP browser automation to the Java WeChat Agent through safe, opt-in browser tools.

**Architecture:** A `browser-mcp-sidecar` Docker service exposes a Streamable HTTP MCP server with five stable tools: `browser_open`, `browser_click`, `browser_type`, `browser_screenshot`, and `browser_read_page`. The sidecar internally launches `chrome-devtools-mcp` in slim, headless mode and forwards high-level browser actions to Chrome DevTools MCP tools. The Java app calls this sidecar through a `BrowserMcpClient`, while `BrowserAutomationService` owns URL safety, risky-action confirmation, sensitive-input checks, and user-facing result formatting.

**Tech Stack:** Java 17, Spring Boot 3.4.7, RestClient, JUnit 5, MockRestServiceServer, Docker Compose, Node.js 22, `@modelcontextprotocol/sdk@1.30.0`, `chrome-devtools-mcp@1.6.0`, `zod@4.4.3`.

---

## File Structure

- Create: `src/main/java/com/example/spring/wechat/browser/config/BrowserAutomationProperties.java`
  - Binds `browser.automation.*` configuration, normalizes defaults, parses allowed hosts.
- Create: `src/test/java/com/example/spring/wechat/browser/config/BrowserAutomationPropertiesTests.java`
  - Verifies defaults, host parsing, timeout defaults, and enabled flag.
- Create: `src/main/java/com/example/spring/wechat/browser/model/BrowserActionResult.java`
  - Value object for user-visible browser action results.
- Create: `src/main/java/com/example/spring/wechat/browser/client/BrowserMcpClient.java`
  - Calls the browser sidecar MCP endpoint through existing `McpToolClient`.
- Create: `src/test/java/com/example/spring/wechat/browser/client/BrowserMcpClientTests.java`
  - Verifies tool names, arguments, and response formatting.
- Create: `src/main/java/com/example/spring/wechat/browser/service/BrowserAutomationService.java`
  - Enforces browser safety policy, confirmation flow, and delegates MCP calls.
- Create: `src/test/java/com/example/spring/wechat/browser/service/BrowserAutomationServiceTests.java`
  - Verifies URL whitelist, risky click confirmation, sensitive input blocking, and success formatting.
- Create: `src/main/java/com/example/spring/wechat/conversation/tools/BrowserOpenWechatTool.java`
- Create: `src/main/java/com/example/spring/wechat/conversation/tools/BrowserClickWechatTool.java`
- Create: `src/main/java/com/example/spring/wechat/conversation/tools/BrowserTypeWechatTool.java`
- Create: `src/main/java/com/example/spring/wechat/conversation/tools/BrowserScreenshotWechatTool.java`
- Create: `src/main/java/com/example/spring/wechat/conversation/tools/BrowserReadPageWechatTool.java`
  - Thin WeChat tool wrappers around `BrowserAutomationService`.
- Create: `src/test/java/com/example/spring/wechat/conversation/tools/BrowserWechatToolTests.java`
  - Verifies argument parsing and service delegation.
- Modify: `src/main/resources/application.properties`
  - Adds `browser.automation.*` configuration.
- Modify: `.env.example`
  - Adds browser automation environment variables.
- Create: `browser-mcp-sidecar/package.json`
  - Pins Node dependencies and scripts.
- Create: `browser-mcp-sidecar/server.js`
  - Streamable HTTP MCP server exposing high-level browser tools and bridging to `chrome-devtools-mcp`.
- Create: `browser-mcp-sidecar/Dockerfile`
  - Builds Node + Chrome for Testing / Chromium runtime image.
- Create: `browser-mcp-sidecar/compose.yaml`
  - Local Docker Compose service.
- Create: `browser-mcp-sidecar/.dockerignore`
  - Keeps Docker context small.
- Create: `browser-mcp-sidecar/README.md`
  - Documents build, run, health check, and Java configuration.
- Modify: `docs/COLLABORATOR_BOOTSTRAP.md`
  - Adds opt-in browser sidecar startup notes.

## Task 1: Browser Configuration

**Files:**
- Create: `src/test/java/com/example/spring/wechat/browser/config/BrowserAutomationPropertiesTests.java`
- Create: `src/main/java/com/example/spring/wechat/browser/config/BrowserAutomationProperties.java`

- [ ] **Step 1: Write the failing configuration test**

Create `src/test/java/com/example/spring/wechat/browser/config/BrowserAutomationPropertiesTests.java`:

```java
package com.example.spring.wechat.browser.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BrowserAutomationPropertiesTests {

    @Test
    void appliesSafeDefaultsForEmptyConfiguration() {
        BrowserAutomationProperties properties = new BrowserAutomationProperties(
                false,
                "",
                "",
                0,
                false,
                "",
                "",
                false);

        assertThat(properties.enabled()).isFalse();
        assertThat(properties.mcpEndpoint()).isEqualTo("http://127.0.0.1:3333/mcp");
        assertThat(properties.apiKey()).isEmpty();
        assertThat(properties.timeoutMs()).isEqualTo(30_000);
        assertThat(properties.allowExternalUrl()).isFalse();
        assertThat(properties.allowedHosts()).containsExactly("localhost", "127.0.0.1");
        assertThat(properties.screenshotDir()).isEqualTo("data/browser/screenshots");
        assertThat(properties.requireConfirmationForRiskyActions()).isFalse();
    }

    @Test
    void trimsAndDeduplicatesAllowedHosts() {
        BrowserAutomationProperties properties = new BrowserAutomationProperties(
                true,
                " http://browser-mcp-sidecar:3333/mcp ",
                " key-1 ",
                15_000,
                true,
                " localhost, example.com, localhost , 127.0.0.1 ",
                " data/custom ",
                true);

        assertThat(properties.enabled()).isTrue();
        assertThat(properties.mcpEndpoint()).isEqualTo("http://browser-mcp-sidecar:3333/mcp");
        assertThat(properties.apiKey()).isEqualTo("key-1");
        assertThat(properties.timeoutMs()).isEqualTo(15_000);
        assertThat(properties.allowExternalUrl()).isTrue();
        assertThat(properties.allowedHosts()).containsExactly("localhost", "example.com", "127.0.0.1");
        assertThat(properties.screenshotDir()).isEqualTo("data/custom");
        assertThat(properties.requireConfirmationForRiskyActions()).isTrue();
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```powershell
mvn -q "-Dtest=BrowserAutomationPropertiesTests" test
```

Expected: compile failure because `BrowserAutomationProperties` does not exist.

- [ ] **Step 3: Add the configuration record**

Create `src/main/java/com/example/spring/wechat/browser/config/BrowserAutomationProperties.java`:

```java
package com.example.spring.wechat.browser.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashSet;
import java.util.List;

@ConfigurationProperties(prefix = "browser.automation")
public record BrowserAutomationProperties(
        boolean enabled,
        String mcpEndpoint,
        String apiKey,
        int timeoutMs,
        boolean allowExternalUrl,
        List<String> allowedHosts,
        String screenshotDir,
        boolean requireConfirmationForRiskyActions) {

    public BrowserAutomationProperties(
            boolean enabled,
            String mcpEndpoint,
            String apiKey,
            int timeoutMs,
            boolean allowExternalUrl,
            String allowedHosts,
            String screenshotDir,
            boolean requireConfirmationForRiskyActions) {
        this(
                enabled,
                mcpEndpoint,
                apiKey,
                timeoutMs,
                allowExternalUrl,
                parseHosts(allowedHosts),
                screenshotDir,
                requireConfirmationForRiskyActions);
    }

    public BrowserAutomationProperties {
        mcpEndpoint = safeOrDefault(mcpEndpoint, "http://127.0.0.1:3333/mcp");
        apiKey = safe(apiKey);
        timeoutMs = timeoutMs <= 0 ? 30_000 : timeoutMs;
        allowedHosts = allowedHosts == null || allowedHosts.isEmpty()
                ? List.of("localhost", "127.0.0.1")
                : cleanHosts(allowedHosts);
        screenshotDir = safeOrDefault(screenshotDir, "data/browser/screenshots");
    }

    private static List<String> parseHosts(String hosts) {
        if (hosts == null || hosts.isBlank()) {
            return List.of();
        }
        return cleanHosts(List.of(hosts.split(",")));
    }

    private static List<String> cleanHosts(List<String> hosts) {
        LinkedHashSet<String> cleaned = new LinkedHashSet<>();
        for (String host : hosts) {
            if (host != null && !host.isBlank()) {
                cleaned.add(host.strip().toLowerCase());
            }
        }
        return cleaned.isEmpty() ? List.of("localhost", "127.0.0.1") : List.copyOf(cleaned);
    }

    private static String safe(String value) {
        return value == null ? "" : value.strip();
    }

    private static String safeOrDefault(String value, String fallback) {
        String safe = safe(value);
        return safe.isBlank() ? fallback : safe;
    }
}
```

- [ ] **Step 4: Run the configuration test**

Run:

```powershell
mvn -q "-Dtest=BrowserAutomationPropertiesTests" test
```

Expected: pass.

## Task 2: Browser MCP Client

**Files:**
- Create: `src/main/java/com/example/spring/wechat/browser/model/BrowserActionResult.java`
- Create: `src/test/java/com/example/spring/wechat/browser/client/BrowserMcpClientTests.java`
- Create: `src/main/java/com/example/spring/wechat/browser/client/BrowserMcpClient.java`

- [ ] **Step 1: Add the result model**

Create `src/main/java/com/example/spring/wechat/browser/model/BrowserActionResult.java`:

```java
package com.example.spring.wechat.browser.model;

import com.fasterxml.jackson.databind.JsonNode;

public record BrowserActionResult(
        boolean success,
        String message,
        String title,
        String url,
        String screenshotPath,
        JsonNode raw) {

    public BrowserActionResult {
        message = message == null ? "" : message.strip();
        title = title == null ? "" : title.strip();
        url = url == null ? "" : url.strip();
        screenshotPath = screenshotPath == null ? "" : screenshotPath.strip();
    }

    public static BrowserActionResult from(JsonNode node) {
        JsonNode safe = node == null ? com.fasterxml.jackson.databind.node.MissingNode.getInstance() : node;
        return new BrowserActionResult(
                safe.path("success").asBoolean(true),
                safe.path("message").asText("浏览器操作已完成"),
                safe.path("title").asText(""),
                safe.path("url").asText(""),
                safe.path("screenshotPath").asText(""),
                safe);
    }
}
```

- [ ] **Step 2: Write the failing MCP client test**

Create `src/test/java/com/example/spring/wechat/browser/client/BrowserMcpClientTests.java`:

```java
package com.example.spring.wechat.browser.client;

import com.example.spring.wechat.browser.config.BrowserAutomationProperties;
import com.example.spring.wechat.web.mcp.McpCallResult;
import com.example.spring.wechat.web.mcp.McpToolClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BrowserMcpClientTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void openDelegatesToBrowserOpenTool() throws Exception {
        RecordingMcpToolClient toolClient = new RecordingMcpToolClient("""
                {"success":true,"message":"opened","title":"Home","url":"http://localhost:8080"}
                """);
        BrowserMcpClient client = new BrowserMcpClient(toolClient, properties());

        var result = client.open("http://localhost:8080");

        assertThat(toolClient.endpoint).isEqualTo("http://127.0.0.1:3333/mcp");
        assertThat(toolClient.apiKey).isEqualTo("browser-key");
        assertThat(toolClient.toolName).isEqualTo("browser_open");
        assertThat(toolClient.arguments).containsEntry("url", "http://localhost:8080");
        assertThat(result.title()).isEqualTo("Home");
    }

    @Test
    void typeDoesNotRenameArguments() throws Exception {
        RecordingMcpToolClient toolClient = new RecordingMcpToolClient("""
                {"success":true,"message":"typed"}
                """);
        BrowserMcpClient client = new BrowserMcpClient(toolClient, properties());

        client.type("Search", "OpenClaw");

        assertThat(toolClient.toolName).isEqualTo("browser_type");
        assertThat(toolClient.arguments).containsEntry("target", "Search");
        assertThat(toolClient.arguments).containsEntry("text", "OpenClaw");
    }

    private BrowserAutomationProperties properties() {
        return new BrowserAutomationProperties(
                true,
                "http://127.0.0.1:3333/mcp",
                "browser-key",
                30_000,
                false,
                "localhost,127.0.0.1",
                "data/browser/screenshots",
                true);
    }

    private final class RecordingMcpToolClient implements McpToolClient {
        private final String responseJson;
        private String endpoint;
        private String apiKey;
        private String toolName;
        private Map<String, Object> arguments = Map.of();

        private RecordingMcpToolClient(String responseJson) {
            this.responseJson = responseJson;
        }

        @Override
        public McpCallResult callTool(String endpoint, String apiKey, String toolName, Map<String, Object> arguments) {
            this.endpoint = endpoint;
            this.apiKey = apiKey;
            this.toolName = toolName;
            this.arguments = new LinkedHashMap<>(arguments);
            try {
                return new McpCallResult(objectMapper.readTree(responseJson), "session-1");
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run:

```powershell
mvn -q "-Dtest=BrowserMcpClientTests" test
```

Expected: compile failure because `BrowserMcpClient` does not exist.

- [ ] **Step 4: Implement the MCP client**

Create `src/main/java/com/example/spring/wechat/browser/client/BrowserMcpClient.java`:

```java
package com.example.spring.wechat.browser.client;

import com.example.spring.wechat.browser.config.BrowserAutomationProperties;
import com.example.spring.wechat.browser.model.BrowserActionResult;
import com.example.spring.wechat.web.mcp.McpToolClient;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class BrowserMcpClient {

    private final McpToolClient mcpToolClient;
    private final BrowserAutomationProperties properties;

    public BrowserMcpClient(McpToolClient mcpToolClient, BrowserAutomationProperties properties) {
        this.mcpToolClient = mcpToolClient;
        this.properties = properties;
    }

    public BrowserActionResult open(String url) {
        return call("browser_open", Map.of("url", safe(url)));
    }

    public BrowserActionResult click(String target) {
        return call("browser_click", Map.of("target", safe(target)));
    }

    public BrowserActionResult type(String target, String text) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("target", safe(target));
        arguments.put("text", safe(text));
        return call("browser_type", arguments);
    }

    public BrowserActionResult screenshot(String name) {
        return call("browser_screenshot", Map.of("name", safe(name)));
    }

    public BrowserActionResult readPage(int maxChars) {
        return call("browser_read_page", Map.of("maxChars", maxChars));
    }

    private BrowserActionResult call(String toolName, Map<String, Object> arguments) {
        return BrowserActionResult.from(mcpToolClient.callTool(
                properties.mcpEndpoint(),
                properties.apiKey(),
                toolName,
                arguments).result());
    }

    private String safe(String value) {
        return value == null ? "" : value.strip();
    }
}
```

- [ ] **Step 5: Run MCP client tests**

Run:

```powershell
mvn -q "-Dtest=BrowserMcpClientTests" test
```

Expected: pass.

## Task 3: Browser Automation Service Safety

**Files:**
- Create: `src/test/java/com/example/spring/wechat/browser/service/BrowserAutomationServiceTests.java`
- Create: `src/main/java/com/example/spring/wechat/browser/service/BrowserAutomationService.java`

- [ ] **Step 1: Write the failing safety tests**

Create `src/test/java/com/example/spring/wechat/browser/service/BrowserAutomationServiceTests.java`:

```java
package com.example.spring.wechat.browser.service;

import com.example.spring.wechat.browser.client.BrowserMcpClient;
import com.example.spring.wechat.browser.config.BrowserAutomationProperties;
import com.example.spring.wechat.browser.model.BrowserActionResult;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BrowserAutomationServiceTests {

    @Test
    void rejectsExternalUrlWhenExternalAccessDisabled() {
        RecordingBrowserMcpClient client = new RecordingBrowserMcpClient();
        BrowserAutomationService service = new BrowserAutomationService(client, localOnlyProperties());

        String result = service.open("wx-user-1", "https://example.com");

        assertThat(result).contains("当前只允许访问配置白名单内的地址");
        assertThat(client.openedUrl).isNull();
    }

    @Test
    void opensAllowedLocalhostUrl() {
        RecordingBrowserMcpClient client = new RecordingBrowserMcpClient();
        BrowserAutomationService service = new BrowserAutomationService(client, localOnlyProperties());

        String result = service.open("wx-user-1", "http://localhost:8080");

        assertThat(client.openedUrl).isEqualTo("http://localhost:8080");
        assertThat(result).contains("Home").contains("http://localhost:8080");
    }

    @Test
    void riskyClickRequiresConfirmationBeforeCallingMcp() {
        RecordingBrowserMcpClient client = new RecordingBrowserMcpClient();
        BrowserAutomationService service = new BrowserAutomationService(client, localOnlyProperties());

        String first = service.click("wx-user-1", "删除订单", "");

        assertThat(first).contains("需要确认");
        assertThat(first).contains("confirm_token");
        assertThat(client.clickedTarget).isNull();
    }

    @Test
    void blocksSensitiveInput() {
        RecordingBrowserMcpClient client = new RecordingBrowserMcpClient();
        BrowserAutomationService service = new BrowserAutomationService(client, localOnlyProperties());

        String result = service.type("wx-user-1", "密码", "123456");

        assertThat(result).contains("不能输入密码、验证码、银行卡号或私钥");
        assertThat(client.typedText).isNull();
    }

    private BrowserAutomationProperties localOnlyProperties() {
        return new BrowserAutomationProperties(
                true,
                "http://127.0.0.1:3333/mcp",
                "",
                30_000,
                false,
                "localhost,127.0.0.1",
                "data/browser/screenshots",
                true);
    }

    private static final class RecordingBrowserMcpClient extends BrowserMcpClient {
        private String openedUrl;
        private String clickedTarget;
        private String typedText;

        private RecordingBrowserMcpClient() {
            super(null, null);
        }

        @Override
        public BrowserActionResult open(String url) {
            this.openedUrl = url;
            return result("已打开页面", "Home", url, "");
        }

        @Override
        public BrowserActionResult click(String target) {
            this.clickedTarget = target;
            return result("已点击", "", "", "");
        }

        @Override
        public BrowserActionResult type(String target, String text) {
            this.typedText = text;
            return result("已输入", "", "", "");
        }

        @Override
        public BrowserActionResult screenshot(String name) {
            return result("已截图", "", "", "data/browser/screenshots/test.png");
        }

        @Override
        public BrowserActionResult readPage(int maxChars) {
            return result("页面文本", "", "", "");
        }

        private BrowserActionResult result(String message, String title, String url, String screenshotPath) {
            return new BrowserActionResult(true, message, title, url, screenshotPath, JsonNodeFactory.instance.objectNode());
        }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```powershell
mvn -q "-Dtest=BrowserAutomationServiceTests" test
```

Expected: compile failure because `BrowserAutomationService` does not exist.

- [ ] **Step 3: Implement the service**

Create `src/main/java/com/example/spring/wechat/browser/service/BrowserAutomationService.java`:

```java
package com.example.spring.wechat.browser.service;

import com.example.spring.wechat.browser.client.BrowserMcpClient;
import com.example.spring.wechat.browser.config.BrowserAutomationProperties;
import com.example.spring.wechat.browser.model.BrowserActionResult;
import com.example.spring.wechat.web.exception.WebToolException;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class BrowserAutomationService {

    private final BrowserMcpClient browserMcpClient;
    private final BrowserAutomationProperties properties;
    private final Map<String, PendingClick> pendingClicks = new ConcurrentHashMap<>();

    public BrowserAutomationService(BrowserMcpClient browserMcpClient, BrowserAutomationProperties properties) {
        this.browserMcpClient = browserMcpClient;
        this.properties = properties;
    }

    public String open(String userId, String url) {
        String validation = validateUrl(url);
        if (!validation.isBlank()) {
            return validation;
        }
        return format(browserMcpClient.open(url));
    }

    public String click(String userId, String target, String confirmToken) {
        String safeTarget = safe(target);
        if (safeTarget.isBlank()) {
            return "请告诉我要点击哪个页面元素。";
        }
        if (isRiskyTarget(safeTarget)) {
            PendingClick pending = pendingClicks.get(confirmToken);
            if (confirmToken == null || confirmToken.isBlank() || pending == null || !pending.userId().equals(safe(userId))) {
                String token = UUID.randomUUID().toString();
                pendingClicks.put(token, new PendingClick(safe(userId), safeTarget));
                return "这个点击可能会产生外部影响，需要确认后再执行。请再次调用 browser_click，并带上 confirm_token=" + token + "。";
            }
            pendingClicks.remove(confirmToken);
        }
        return format(browserMcpClient.click(safeTarget));
    }

    public String type(String userId, String target, String text) {
        if (safe(target).isBlank()) {
            return "请告诉我要向哪个输入框输入内容。";
        }
        if (safe(text).isBlank()) {
            return "请告诉我要输入什么内容。";
        }
        if (looksSensitive(target, text)) {
            return "出于安全原因，浏览器自动化不能输入密码、验证码、银行卡号或私钥。";
        }
        return format(browserMcpClient.type(target, text));
    }

    public String screenshot(String userId, String name) {
        return format(browserMcpClient.screenshot(name));
    }

    public String readPage(String userId, int maxChars) {
        int safeMax = maxChars <= 0 ? 2000 : Math.min(maxChars, 6000);
        return format(browserMcpClient.readPage(safeMax));
    }

    private String validateUrl(String rawUrl) {
        String value = safe(rawUrl);
        if (value.isBlank()) {
            return "请提供要打开的 URL。";
        }
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                return "浏览器自动化只支持 http 或 https URL。";
            }
            if (!properties.allowExternalUrl() && !properties.allowedHosts().contains(host)) {
                return "当前只允许访问配置白名单内的地址：" + String.join(", ", properties.allowedHosts()) + "。";
            }
            return "";
        } catch (IllegalArgumentException exception) {
            return "URL 格式不正确，请提供完整的 http 或 https 地址。";
        }
    }

    private boolean isRiskyTarget(String target) {
        String text = target.toLowerCase(Locale.ROOT);
        return text.contains("删除")
                || text.contains("支付")
                || text.contains("购买")
                || text.contains("提交")
                || text.contains("发送")
                || text.contains("授权")
                || text.contains("登录")
                || text.contains("delete")
                || text.contains("pay")
                || text.contains("buy")
                || text.contains("submit")
                || text.contains("send")
                || text.contains("authorize")
                || text.contains("login");
    }

    private boolean looksSensitive(String target, String text) {
        String combined = (safe(target) + " " + safe(text)).toLowerCase(Locale.ROOT);
        return combined.contains("密码")
                || combined.contains("验证码")
                || combined.contains("银行卡")
                || combined.contains("私钥")
                || combined.contains("password")
                || combined.contains("verification code")
                || combined.contains("secret")
                || combined.contains("private key");
    }

    private String format(BrowserActionResult result) {
        try {
            StringBuilder text = new StringBuilder(result.message().isBlank() ? "浏览器操作已完成。" : result.message());
            if (!result.title().isBlank()) {
                text.append("\n标题：").append(result.title());
            }
            if (!result.url().isBlank()) {
                text.append("\n地址：").append(result.url());
            }
            if (!result.screenshotPath().isBlank()) {
                text.append("\n截图：").append(result.screenshotPath());
            }
            return text.toString();
        } catch (RuntimeException exception) {
            throw new WebToolException("浏览器自动化结果解析失败", exception);
        }
    }

    private String safe(String value) {
        return value == null ? "" : value.strip();
    }

    private record PendingClick(String userId, String target) {
    }
}
```

- [ ] **Step 4: Run service tests**

Run:

```powershell
mvn -q "-Dtest=BrowserAutomationServiceTests" test
```

Expected: pass.

## Task 4: WeChat Browser Tools

**Files:**
- Create: `src/test/java/com/example/spring/wechat/conversation/tools/BrowserWechatToolTests.java`
- Create: `src/main/java/com/example/spring/wechat/conversation/tools/BrowserOpenWechatTool.java`
- Create: `src/main/java/com/example/spring/wechat/conversation/tools/BrowserClickWechatTool.java`
- Create: `src/main/java/com/example/spring/wechat/conversation/tools/BrowserTypeWechatTool.java`
- Create: `src/main/java/com/example/spring/wechat/conversation/tools/BrowserScreenshotWechatTool.java`
- Create: `src/main/java/com/example/spring/wechat/conversation/tools/BrowserReadPageWechatTool.java`

- [ ] **Step 1: Write failing WeChat tool tests**

Create `src/test/java/com/example/spring/wechat/conversation/tools/BrowserWechatToolTests.java`:

```java
package com.example.spring.wechat.conversation.tools;

import com.example.spring.wechat.browser.service.BrowserAutomationService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BrowserWechatToolTests {

    @Test
    void openToolDelegatesUrl() {
        RecordingBrowserAutomationService service = new RecordingBrowserAutomationService();
        BrowserOpenWechatTool tool = new BrowserOpenWechatTool(service);

        var reply = tool.execute(request(Map.of("url", "http://localhost:8080")));

        assertThat(tool.name()).isEqualTo("browser_open");
        assertThat(reply.text()).isEqualTo("open-result");
        assertThat(service.url).isEqualTo("http://localhost:8080");
        assertThat(service.userId).isEqualTo("wx-user-1");
    }

    @Test
    void clickToolPassesConfirmToken() {
        RecordingBrowserAutomationService service = new RecordingBrowserAutomationService();
        BrowserClickWechatTool tool = new BrowserClickWechatTool(service);

        tool.execute(request(Map.of("target", "删除", "confirm_token", "token-1")));

        assertThat(service.target).isEqualTo("删除");
        assertThat(service.confirmToken).isEqualTo("token-1");
    }

    @Test
    void typeToolPassesTargetAndText() {
        RecordingBrowserAutomationService service = new RecordingBrowserAutomationService();
        BrowserTypeWechatTool tool = new BrowserTypeWechatTool(service);

        tool.execute(request(Map.of("target", "搜索框", "text", "OpenClaw")));

        assertThat(service.target).isEqualTo("搜索框");
        assertThat(service.text).isEqualTo("OpenClaw");
    }

    private WechatToolRequest request(Map<String, String> arguments) {
        return new WechatToolRequest("wx-user-1", "用户原始需求", arguments, "", null, null);
    }

    private static final class RecordingBrowserAutomationService extends BrowserAutomationService {
        private String userId;
        private String url;
        private String target;
        private String text;
        private String confirmToken;

        private RecordingBrowserAutomationService() {
            super(null, null);
        }

        @Override
        public String open(String userId, String url) {
            this.userId = userId;
            this.url = url;
            return "open-result";
        }

        @Override
        public String click(String userId, String target, String confirmToken) {
            this.userId = userId;
            this.target = target;
            this.confirmToken = confirmToken;
            return "click-result";
        }

        @Override
        public String type(String userId, String target, String text) {
            this.userId = userId;
            this.target = target;
            this.text = text;
            return "type-result";
        }

        @Override
        public String screenshot(String userId, String name) {
            return "screenshot-result";
        }

        @Override
        public String readPage(String userId, int maxChars) {
            return "read-result";
        }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```powershell
mvn -q "-Dtest=BrowserWechatToolTests" test
```

Expected: compile failure because browser tool classes do not exist.

- [ ] **Step 3: Implement `browser_open`**

Create `src/main/java/com/example/spring/wechat/conversation/tools/BrowserOpenWechatTool.java`:

```java
package com.example.spring.wechat.conversation.tools;

import com.example.spring.wechat.bot.WechatReply;
import com.example.spring.wechat.browser.service.BrowserAutomationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(prefix = "browser.automation", name = "enabled", havingValue = "true")
public class BrowserOpenWechatTool implements WechatTool {

    private final BrowserAutomationService browserAutomationService;

    public BrowserOpenWechatTool(BrowserAutomationService browserAutomationService) {
        this.browserAutomationService = browserAutomationService;
    }

    @Override
    public String name() {
        return "browser_open";
    }

    @Override
    public String description() {
        return "打开允许范围内的网页，用于本地 Web 应用测试和受控浏览器自动化。";
    }

    @Override
    public List<String> arguments() {
        return List.of("url");
    }

    @Override
    public List<WechatToolParameter> parameters() {
        return List.of(WechatToolParameter.requiredString("url", "要打开的完整 http 或 https URL", "http://localhost:8080"));
    }

    @Override
    public WechatToolCapability capability() {
        return new WechatToolCapability(
                "打开浏览器页面。",
                List.of("默认只允许访问配置白名单内的地址。", "不能用于绕过登录、验证码或网站风控。"),
                List.of("url：完整网页地址。"),
                List.of("页面标题、当前地址或可操作错误。"));
    }

    @Override
    public WechatReply execute(WechatToolRequest request) {
        return WechatReply.text(browserAutomationService.open(request.sessionKey(), request.argument("url")));
    }
}
```

- [ ] **Step 4: Implement remaining browser tools**

Create `src/main/java/com/example/spring/wechat/conversation/tools/BrowserClickWechatTool.java`:

```java
package com.example.spring.wechat.conversation.tools;

import com.example.spring.wechat.bot.WechatReply;
import com.example.spring.wechat.browser.service.BrowserAutomationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(prefix = "browser.automation", name = "enabled", havingValue = "true")
public class BrowserClickWechatTool implements WechatTool {

    private final BrowserAutomationService browserAutomationService;

    public BrowserClickWechatTool(BrowserAutomationService browserAutomationService) {
        this.browserAutomationService = browserAutomationService;
    }

    @Override
    public String name() {
        return "browser_click";
    }

    @Override
    public String description() {
        return "点击当前浏览器页面上的元素，支持文本、元素描述或 CSS selector。";
    }

    @Override
    public List<String> arguments() {
        return List.of("target", "confirm_token");
    }

    @Override
    public List<WechatToolParameter> parameters() {
        return List.of(
                WechatToolParameter.requiredString("target", "要点击的元素文本、描述或 CSS selector", "登录按钮"),
                WechatToolParameter.optionalString("confirm_token", "危险点击确认令牌", "uuid"));
    }

    @Override
    public WechatToolCapability capability() {
        return new WechatToolCapability(
                "点击页面元素。",
                List.of("删除、支付、提交、发送、授权、登录等高风险点击需要确认。"),
                List.of("target：页面元素描述。"),
                List.of("点击结果或确认提示。"));
    }

    @Override
    public WechatReply execute(WechatToolRequest request) {
        return WechatReply.text(browserAutomationService.click(
                request.sessionKey(),
                request.argument("target"),
                request.argument("confirm_token")));
    }
}
```

Create `src/main/java/com/example/spring/wechat/conversation/tools/BrowserTypeWechatTool.java`:

```java
package com.example.spring.wechat.conversation.tools;

import com.example.spring.wechat.bot.WechatReply;
import com.example.spring.wechat.browser.service.BrowserAutomationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(prefix = "browser.automation", name = "enabled", havingValue = "true")
public class BrowserTypeWechatTool implements WechatTool {

    private final BrowserAutomationService browserAutomationService;

    public BrowserTypeWechatTool(BrowserAutomationService browserAutomationService) {
        this.browserAutomationService = browserAutomationService;
    }

    @Override
    public String name() {
        return "browser_type";
    }

    @Override
    public String description() {
        return "向当前页面的输入框输入普通文本。";
    }

    @Override
    public List<String> arguments() {
        return List.of("target", "text");
    }

    @Override
    public List<WechatToolParameter> parameters() {
        return List.of(
                WechatToolParameter.requiredString("target", "输入框描述或 CSS selector", "搜索框"),
                WechatToolParameter.requiredString("text", "要输入的普通文本", "OpenClaw"));
    }

    @Override
    public WechatToolCapability capability() {
        return new WechatToolCapability(
                "向页面输入普通文本。",
                List.of("不能输入密码、验证码、银行卡号、私钥等敏感内容。"),
                List.of("target：输入框描述。", "text：普通文本。"),
                List.of("输入结果。"));
    }

    @Override
    public WechatReply execute(WechatToolRequest request) {
        return WechatReply.text(browserAutomationService.type(
                request.sessionKey(),
                request.argument("target"),
                request.argument("text")));
    }
}
```

Create `src/main/java/com/example/spring/wechat/conversation/tools/BrowserScreenshotWechatTool.java`:

```java
package com.example.spring.wechat.conversation.tools;

import com.example.spring.wechat.bot.WechatReply;
import com.example.spring.wechat.browser.service.BrowserAutomationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(prefix = "browser.automation", name = "enabled", havingValue = "true")
public class BrowserScreenshotWechatTool implements WechatTool {

    private final BrowserAutomationService browserAutomationService;

    public BrowserScreenshotWechatTool(BrowserAutomationService browserAutomationService) {
        this.browserAutomationService = browserAutomationService;
    }

    @Override
    public String name() {
        return "browser_screenshot";
    }

    @Override
    public String description() {
        return "截取当前浏览器页面截图。";
    }

    @Override
    public List<String> arguments() {
        return List.of("name");
    }

    @Override
    public List<WechatToolParameter> parameters() {
        return List.of(WechatToolParameter.optionalString("name", "截图文件名提示", "home-page"));
    }

    @Override
    public WechatReply execute(WechatToolRequest request) {
        return WechatReply.text(browserAutomationService.screenshot(request.sessionKey(), request.argument("name")));
    }
}
```

Create `src/main/java/com/example/spring/wechat/conversation/tools/BrowserReadPageWechatTool.java`:

```java
package com.example.spring.wechat.conversation.tools;

import com.example.spring.wechat.bot.WechatReply;
import com.example.spring.wechat.browser.service.BrowserAutomationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(prefix = "browser.automation", name = "enabled", havingValue = "true")
public class BrowserReadPageWechatTool implements WechatTool {

    private final BrowserAutomationService browserAutomationService;

    public BrowserReadPageWechatTool(BrowserAutomationService browserAutomationService) {
        this.browserAutomationService = browserAutomationService;
    }

    @Override
    public String name() {
        return "browser_read_page";
    }

    @Override
    public String description() {
        return "读取当前浏览器页面的可见文本摘要。";
    }

    @Override
    public List<String> arguments() {
        return List.of("max_chars");
    }

    @Override
    public List<WechatToolParameter> parameters() {
        return List.of(WechatToolParameter.optionalString("max_chars", "最多返回字符数，默认 2000", "2000"));
    }

    @Override
    public WechatReply execute(WechatToolRequest request) {
        return WechatReply.text(browserAutomationService.readPage(
                request.sessionKey(),
                parseInt(request.argument("max_chars"), 2000)));
    }

    private int parseInt(String value, int fallback) {
        try {
            return value == null || value.isBlank() ? fallback : Integer.parseInt(value.strip());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }
}
```

- [ ] **Step 5: Run WeChat tool tests**

Run:

```powershell
mvn -q "-Dtest=BrowserWechatToolTests" test
```

Expected: pass.

## Task 5: Java Configuration Files

**Files:**
- Modify: `src/main/resources/application.properties`
- Modify: `.env.example`

- [ ] **Step 1: Add application properties**

Append this block near the other external tool configuration in `src/main/resources/application.properties`:

```properties
# =========================
# Browser automation / Chrome DevTools MCP sidecar
# =========================
browser.automation.enabled=${BROWSER_AUTOMATION_ENABLED:false}
browser.automation.mcp-endpoint=${BROWSER_AUTOMATION_MCP_ENDPOINT:http://127.0.0.1:3333/mcp}
browser.automation.api-key=${BROWSER_AUTOMATION_API_KEY:}
browser.automation.timeout-ms=${BROWSER_AUTOMATION_TIMEOUT_MS:30000}
browser.automation.allow-external-url=${BROWSER_AUTOMATION_ALLOW_EXTERNAL_URL:false}
browser.automation.allowed-hosts=${BROWSER_AUTOMATION_ALLOWED_HOSTS:localhost,127.0.0.1}
browser.automation.screenshot-dir=${BROWSER_AUTOMATION_SCREENSHOT_DIR:data/browser/screenshots}
browser.automation.require-confirmation-for-risky-actions=${BROWSER_AUTOMATION_REQUIRE_CONFIRMATION_FOR_RISKY_ACTIONS:true}
```

- [ ] **Step 2: Add `.env.example` entries**

Append this block to `.env.example`:

```properties
# Browser automation / Chrome DevTools MCP sidecar
BROWSER_AUTOMATION_ENABLED=false
BROWSER_AUTOMATION_MCP_ENDPOINT=http://127.0.0.1:3333/mcp
BROWSER_AUTOMATION_API_KEY=
BROWSER_AUTOMATION_TIMEOUT_MS=30000
BROWSER_AUTOMATION_ALLOW_EXTERNAL_URL=false
BROWSER_AUTOMATION_ALLOWED_HOSTS=localhost,127.0.0.1
BROWSER_AUTOMATION_SCREENSHOT_DIR=data/browser/screenshots
BROWSER_AUTOMATION_REQUIRE_CONFIRMATION_FOR_RISKY_ACTIONS=true
```

- [ ] **Step 3: Run Java compile check**

Run:

```powershell
mvn -q "-Dtest=BrowserAutomationPropertiesTests,BrowserMcpClientTests,BrowserAutomationServiceTests,BrowserWechatToolTests" test
```

Expected: pass.

## Task 6: Browser MCP Sidecar Package

**Files:**
- Create: `browser-mcp-sidecar/package.json`
- Create: `browser-mcp-sidecar/server.js`

- [ ] **Step 1: Add package manifest**

Create `browser-mcp-sidecar/package.json`:

```json
{
  "name": "openclaw-browser-mcp-sidecar",
  "version": "0.1.0",
  "private": true,
  "type": "module",
  "scripts": {
    "start": "node server.js",
    "test": "node --check server.js"
  },
  "dependencies": {
    "@modelcontextprotocol/sdk": "1.30.0",
    "chrome-devtools-mcp": "1.6.0",
    "zod": "4.4.3"
  }
}
```

- [ ] **Step 2: Add sidecar MCP server**

Create `browser-mcp-sidecar/server.js`:

```javascript
import { randomUUID } from "node:crypto";
import { mkdir, writeFile } from "node:fs/promises";
import { join } from "node:path";
import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { StdioClientTransport } from "@modelcontextprotocol/sdk/client/stdio.js";
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StreamableHTTPServerTransport } from "@modelcontextprotocol/sdk/server/streamableHttp.js";
import { z } from "zod";

const host = process.env.BROWSER_MCP_HOST || "0.0.0.0";
const port = Number(process.env.BROWSER_MCP_PORT || 3333);
const screenshotDir = process.env.BROWSER_SCREENSHOT_DIR || "/data/screenshots";
const userDataDir = process.env.CHROME_USER_DATA_DIR || "/data/chrome-profile";
const headless = (process.env.CHROME_HEADLESS || "true").toLowerCase() !== "false";

let chromeClientPromise;

function chromeClient() {
  if (!chromeClientPromise) {
    const args = [
      "chrome-devtools-mcp",
      "--slim",
      "--no-usage-statistics",
      "--no-update-checks",
      `--user-data-dir=${userDataDir}`,
      "--screenshot-format=png"
    ];
    if (headless) {
      args.push("--headless");
    }
    chromeClientPromise = (async () => {
      const client = new Client({ name: "openclaw-browser-sidecar", version: "0.1.0" });
      const transport = new StdioClientTransport({
        command: "node",
        args: ["./node_modules/chrome-devtools-mcp/build/src/bin/chrome-devtools-mcp.js", ...args.slice(1)]
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

function textFromContent(result) {
  const content = Array.isArray(result?.content) ? result.content : [];
  return content.map((item) => item?.text || "").filter(Boolean).join("\n").trim();
}

function response(message, extra = {}) {
  return {
    content: [{ type: "text", text: message }],
    structuredContent: {
      success: true,
      message,
      ...extra
    }
  };
}

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
    return response("已打开页面", { url, title: firstLine(text), pageText: text.slice(0, 2000) });
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
      (() => {
        const target = ${JSON.stringify(target)};
        const bySelector = (() => { try { return document.querySelector(target); } catch { return null; } })();
        const elements = Array.from(document.querySelectorAll('button,a,input,[role="button"],[onclick]'));
        const byText = elements.find((el) => (el.innerText || el.value || el.getAttribute('aria-label') || '').trim().includes(target));
        const el = bySelector || byText;
        if (!el) return { success: false, message: '未找到页面元素：' + target };
        el.click();
        return { success: true, message: '已点击：' + target, title: document.title, url: location.href };
      })()
    `;
    const result = await callChromeTool("evaluate_script", { function: script });
    const text = textFromContent(result);
    return response(text || "已点击页面元素");
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
      (() => {
        const target = ${JSON.stringify(target)};
        const value = ${JSON.stringify(text)};
        const bySelector = (() => { try { return document.querySelector(target); } catch { return null; } })();
        const controls = Array.from(document.querySelectorAll('input,textarea,[contenteditable="true"]'));
        const byHint = controls.find((el) => {
          const hint = [el.name, el.id, el.placeholder, el.getAttribute('aria-label')].filter(Boolean).join(' ');
          return hint.includes(target);
        });
        const el = bySelector || byHint;
        if (!el) return { success: false, message: '未找到输入框：' + target };
        el.focus();
        if ('value' in el) {
          el.value = value;
          el.dispatchEvent(new Event('input', { bubbles: true }));
          el.dispatchEvent(new Event('change', { bubbles: true }));
        } else {
          el.textContent = value;
          el.dispatchEvent(new InputEvent('input', { bubbles: true, inputType: 'insertText', data: value }));
        }
        return { success: true, message: '已输入文本', title: document.title, url: location.href };
      })()
    `;
    const result = await callChromeTool("evaluate_script", { function: script });
    const resultText = textFromContent(result);
    return response(resultText || "已输入文本");
  }
);

server.registerTool(
  "browser_screenshot",
  {
    title: "Take Browser Screenshot",
    description: "Take a screenshot and save it to the sidecar screenshot directory.",
    inputSchema: { name: z.string().optional() }
  },
  async ({ name = "screenshot" }) => {
    await mkdir(screenshotDir, { recursive: true });
    const result = await callChromeTool("take_screenshot", {});
    const content = Array.isArray(result?.content) ? result.content : [];
    const image = content.find((item) => item?.type === "image" && item?.data);
    if (!image) {
      return response("截图失败：Chrome DevTools MCP 未返回图片。", { success: false });
    }
    const safeName = name.replace(/[^a-zA-Z0-9._-]/g, "_").slice(0, 60) || "screenshot";
    const file = join(screenshotDir, `${Date.now()}-${safeName}.png`);
    await writeFile(file, Buffer.from(image.data, "base64"));
    return response("已截图", { screenshotPath: file });
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
    return response(text || "当前页面没有可读取的文本。", { pageText: text });
  }
);

function firstLine(text) {
  return (text || "").split(/\r?\n/).map((line) => line.trim()).find(Boolean) || "";
}

async function handleMcp(req, res) {
  const transport = new StreamableHTTPServerTransport({
    sessionIdGenerator: () => randomUUID(),
    enableJsonResponse: true
  });
  await server.connect(transport);
  await transport.handleRequest(req, res);
}

const httpServer = await import("node:http");
const app = httpServer.createServer(async (req, res) => {
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
});

app.listen(port, host, () => {
  console.log(`browser-mcp-sidecar listening on http://${host}:${port}/mcp`);
});
```

- [ ] **Step 3: Validate Node syntax**

Run:

```powershell
Push-Location browser-mcp-sidecar
& 'C:\Program Files\nodejs\npm.cmd' install
& 'C:\Program Files\nodejs\npm.cmd' test
Pop-Location
```

Expected: `node --check server.js` exits 0. This creates `browser-mcp-sidecar/package-lock.json`; keep it.

## Task 7: Docker Sidecar

**Files:**
- Create: `browser-mcp-sidecar/Dockerfile`
- Create: `browser-mcp-sidecar/compose.yaml`
- Create: `browser-mcp-sidecar/.dockerignore`
- Create: `browser-mcp-sidecar/README.md`

- [ ] **Step 1: Add Dockerfile**

Create `browser-mcp-sidecar/Dockerfile`:

```dockerfile
ARG BASE_REGISTRY=dockerproxy.net/library

FROM ${BASE_REGISTRY}/node:22-bookworm-slim

ENV NODE_ENV=production \
    BROWSER_MCP_HOST=0.0.0.0 \
    BROWSER_MCP_PORT=3333 \
    CHROME_HEADLESS=true \
    CHROME_USER_DATA_DIR=/data/chrome-profile \
    BROWSER_SCREENSHOT_DIR=/data/screenshots \
    CHROME_DEVTOOLS_MCP_NO_USAGE_STATISTICS=1 \
    CHROME_DEVTOOLS_MCP_NO_UPDATE_CHECKS=1

RUN apt-get update \
    && apt-get install --no-install-recommends -y chromium ca-certificates tini fonts-liberation \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --omit=dev

COPY server.js ./server.js

RUN useradd --create-home --uid 10001 sidecar \
    && mkdir -p /data/chrome-profile /data/screenshots \
    && chown -R sidecar:sidecar /data

USER sidecar
EXPOSE 3333

HEALTHCHECK --interval=15s --timeout=3s --start-period=20s --retries=3 \
    CMD ["node", "-e", "fetch('http://127.0.0.1:3333/health').then(r=>process.exit(r.ok?0:1)).catch(()=>process.exit(1))"]

ENTRYPOINT ["/usr/bin/tini", "--"]
CMD ["node", "server.js"]
```

- [ ] **Step 2: Add compose file**

Create `browser-mcp-sidecar/compose.yaml`:

```yaml
name: openclaw-browser-mcp

services:
  browser-mcp-sidecar:
    image: openclaw-browser-mcp-sidecar:local
    build:
      context: .
      dockerfile: Dockerfile
      args:
        BASE_REGISTRY: ${BASE_REGISTRY:-dockerproxy.net/library}
    restart: unless-stopped
    environment:
      BROWSER_MCP_HOST: 0.0.0.0
      BROWSER_MCP_PORT: 3333
      CHROME_HEADLESS: "true"
      CHROME_USER_DATA_DIR: /data/chrome-profile
      BROWSER_SCREENSHOT_DIR: /data/screenshots
      CHROME_DEVTOOLS_MCP_NO_USAGE_STATISTICS: "1"
      CHROME_DEVTOOLS_MCP_NO_UPDATE_CHECKS: "1"
    ports:
      - "127.0.0.1:3333:3333"
    volumes:
      - browser-chrome-profile:/data/chrome-profile
      - browser-screenshots:/data/screenshots
    shm_size: "1gb"
    healthcheck:
      test:
        - CMD
        - node
        - -e
        - "fetch('http://127.0.0.1:3333/health').then(r=>process.exit(r.ok?0:1)).catch(()=>process.exit(1))"
      interval: 15s
      timeout: 3s
      start_period: 20s
      retries: 3
    security_opt:
      - no-new-privileges:true

volumes:
  browser-chrome-profile:
  browser-screenshots:
```

- [ ] **Step 3: Add Docker ignore**

Create `browser-mcp-sidecar/.dockerignore`:

```text
node_modules
npm-debug.log
.env
.DS_Store
```

- [ ] **Step 4: Add sidecar README**

Create `browser-mcp-sidecar/README.md`:

```markdown
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
```

- [ ] **Step 5: Build Docker image**

Run:

```powershell
docker compose -f browser-mcp-sidecar/compose.yaml build
```

Expected: image builds successfully.

## Task 8: Documentation Updates

**Files:**
- Modify: `docs/COLLABORATOR_BOOTSTRAP.md`

- [ ] **Step 1: Add collaborator notes**

Append this section to `docs/COLLABORATOR_BOOTSTRAP.md`:

```markdown
## Browser Automation Sidecar

Browser automation is opt-in and disabled by default.

Start the Docker sidecar:

```powershell
docker compose -f browser-mcp-sidecar/compose.yaml up --build
```

Enable Java tools in `.env`:

```properties
BROWSER_AUTOMATION_ENABLED=true
BROWSER_AUTOMATION_MCP_ENDPOINT=http://127.0.0.1:3333/mcp
BROWSER_AUTOMATION_ALLOW_EXTERNAL_URL=false
BROWSER_AUTOMATION_ALLOWED_HOSTS=localhost,127.0.0.1
```

Default mode only permits local pages. Do not use this tool for passwords, verification codes, payments, account authorization, or destructive actions without explicit confirmation.
```

- [ ] **Step 2: Run docs diff check**

Run:

```powershell
git diff --check
```

Expected: no whitespace errors.

## Task 9: Full Verification

**Files:**
- No production file changes beyond previous tasks.

- [ ] **Step 1: Run focused Java tests**

Run:

```powershell
mvn -q "-Dtest=BrowserAutomationPropertiesTests,BrowserMcpClientTests,BrowserAutomationServiceTests,BrowserWechatToolTests" test
```

Expected: all focused tests pass.

- [ ] **Step 2: Run full test suite**

Run:

```powershell
mvn -q test
```

Expected: test suite passes. If unrelated existing tests fail, record the failures and rerun focused tests.

- [ ] **Step 3: Run sidecar syntax test**

Run:

```powershell
Push-Location browser-mcp-sidecar
& 'C:\Program Files\nodejs\npm.cmd' test
Pop-Location
```

Expected: `node --check server.js` exits 0.

- [ ] **Step 4: Build sidecar Docker image**

Run:

```powershell
docker compose -f browser-mcp-sidecar/compose.yaml build
```

Expected: Docker image builds successfully. If Docker is unavailable, record the exact error.

- [ ] **Step 5: Final diff check**

Run:

```powershell
git diff --check
```

Expected: no whitespace errors.
