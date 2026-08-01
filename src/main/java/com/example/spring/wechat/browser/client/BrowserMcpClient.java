package com.example.spring.wechat.browser.client;

import com.example.spring.wechat.browser.config.BrowserAutomationProperties;
import com.example.spring.wechat.browser.model.BrowserActionResult;
import com.example.spring.wechat.web.exception.WebToolException;
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
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("name", safe(name));
        return call("browser_screenshot", arguments);
    }

    public BrowserActionResult readPage(int maxChars) {
        return call("browser_read_page", Map.of("maxChars", maxChars));
    }

    public BrowserActionResult currentState() {
        return call("browser_current_state", Map.of());
    }

    public BrowserActionResult waitFor(String condition, String value, int timeoutMs) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("condition", safe(condition));
        arguments.put("value", safe(value));
        arguments.put("timeoutMs", timeoutMs);
        return call("browser_wait_for", arguments);
    }

    public BrowserActionResult reset(boolean clearProfile) {
        return call("browser_reset", Map.of("clearProfile", clearProfile));
    }

    private BrowserActionResult call(String toolName, Map<String, Object> arguments) {
        try {
            return BrowserActionResult.from(mcpToolClient.callTool(
                    properties.mcpEndpoint(),
                    effectiveApiKey(),
                    toolName,
                    arguments).result());
        } catch (WebToolException exception) {
            throw new WebToolException(browserUnavailableMessage(exception), null);
        }
    }

    private String browserUnavailableMessage(WebToolException exception) {
        return """
                浏览器自动化服务不可用，无法连接 browser-mcp-sidecar。
                请先启动 sidecar：docker compose -f browser-mcp-sidecar/compose.yaml up -d --build
                当前 MCP endpoint：%s
                原始错误：%s
                """.formatted(properties.mcpEndpoint(), originalMessage(exception)).strip();
    }

    private String originalMessage(Throwable exception) {
        Throwable current = exception;
        while (current != null && current.getCause() != null) {
            current = current.getCause();
        }
        if (current == null || current.getMessage() == null || current.getMessage().isBlank()) {
            return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
        }
        return current.getMessage();
    }

    private String effectiveApiKey() {
        return properties.apiKey().isBlank() ? "browser-sidecar-local" : properties.apiKey();
    }

    private String safe(String value) {
        return value == null ? "" : value.strip();
    }
}
