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
    void openDelegatesToBrowserOpenTool() {
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
    void typeDoesNotRenameArguments() {
        RecordingMcpToolClient toolClient = new RecordingMcpToolClient("""
                {"success":true,"message":"typed"}
                """);
        BrowserMcpClient client = new BrowserMcpClient(toolClient, properties());

        client.type("Search", "OpenClaw");

        assertThat(toolClient.toolName).isEqualTo("browser_type");
        assertThat(toolClient.arguments).containsEntry("target", "Search");
        assertThat(toolClient.arguments).containsEntry("text", "OpenClaw");
    }

    @Test
    void usesLocalPlaceholderTokenWhenApiKeyIsBlank() {
        RecordingMcpToolClient toolClient = new RecordingMcpToolClient("""
                {"success":true,"message":"opened"}
                """);
        BrowserMcpClient client = new BrowserMcpClient(toolClient, new BrowserAutomationProperties(
                true,
                "http://127.0.0.1:3333/mcp",
                "",
                30_000,
                false,
                "localhost,127.0.0.1",
                "data/browser/screenshots",
                true));

        client.open("http://localhost:8080");

        assertThat(toolClient.apiKey).isEqualTo("browser-sidecar-local");
    }

    @Test
    void readsStructuredContentFromMcpToolResult() {
        RecordingMcpToolClient toolClient = new RecordingMcpToolClient("""
                {"content":[{"type":"text","text":"Opened page"}],
                 "structuredContent":{"success":true,"message":"Opened page","title":"Home","url":"http://localhost:8080"}}
                """);
        BrowserMcpClient client = new BrowserMcpClient(toolClient, properties());

        var result = client.open("http://localhost:8080");

        assertThat(result.message()).isEqualTo("Opened page");
        assertThat(result.title()).isEqualTo("Home");
        assertThat(result.url()).isEqualTo("http://localhost:8080");
    }

    @Test
    void screenshotPassesConfiguredDirectoryToSidecar() {
        RecordingMcpToolClient toolClient = new RecordingMcpToolClient("""
                {"success":true,"message":"captured","screenshotPath":"data/browser/screenshots/home.png"}
                """);
        BrowserMcpClient client = new BrowserMcpClient(toolClient, properties());

        client.screenshot("home");

        assertThat(toolClient.toolName).isEqualTo("browser_screenshot");
        assertThat(toolClient.arguments).containsEntry("name", "home");
        assertThat(toolClient.arguments).containsEntry("screenshotDir", "data/browser/screenshots");
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
