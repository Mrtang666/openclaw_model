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
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("name", safe(name));
        return call("browser_screenshot", arguments);
    }

    public BrowserActionResult readPage(int maxChars) {
        return call("browser_read_page", Map.of("maxChars", maxChars));
    }

    private BrowserActionResult call(String toolName, Map<String, Object> arguments) {
        return BrowserActionResult.from(mcpToolClient.callTool(
                properties.mcpEndpoint(),
                effectiveApiKey(),
                toolName,
                arguments).result());
    }

    private String effectiveApiKey() {
        return properties.apiKey().isBlank() ? "browser-sidecar-local" : properties.apiKey();
    }

    private String safe(String value) {
        return value == null ? "" : value.strip();
    }
}
