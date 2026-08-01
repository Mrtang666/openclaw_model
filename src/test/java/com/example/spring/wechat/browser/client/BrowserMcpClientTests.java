package com.example.spring.wechat.browser.client;

import com.example.spring.wechat.browser.config.BrowserAutomationProperties;
import com.example.spring.wechat.web.mcp.McpCallResult;
import com.example.spring.wechat.web.mcp.McpToolClient;
import com.example.spring.wechat.web.exception.WebToolException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.channels.ClosedChannelException;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    void currentStateDelegatesToBrowserCurrentStateTool() {
        RecordingMcpToolClient toolClient = new RecordingMcpToolClient("""
                {"success":true,"message":"Current page state","title":"Dashboard","url":"https://example.com/dashboard"}
                """);
        BrowserMcpClient client = new BrowserMcpClient(toolClient, properties());

        var result = client.currentState();

        assertThat(toolClient.toolName).isEqualTo("browser_current_state");
        assertThat(toolClient.arguments).isEmpty();
        assertThat(result.title()).isEqualTo("Dashboard");
    }

    @Test
    void waitForPassesConditionArguments() {
        RecordingMcpToolClient toolClient = new RecordingMcpToolClient("""
                {"success":true,"message":"Wait condition met","url":"https://example.com/dashboard"}
                """);
        BrowserMcpClient client = new BrowserMcpClient(toolClient, properties());

        client.waitFor("url", "dashboard", 12000);

        assertThat(toolClient.toolName).isEqualTo("browser_wait_for");
        assertThat(toolClient.arguments)
                .containsEntry("condition", "url")
                .containsEntry("value", "dashboard")
                .containsEntry("timeoutMs", 12000);
    }

    @Test
    void resetDelegatesToBrowserResetTool() {
        RecordingMcpToolClient toolClient = new RecordingMcpToolClient("""
                {"success":true,"message":"Browser reset completed"}
                """);
        BrowserMcpClient client = new BrowserMcpClient(toolClient, properties());

        client.reset(true);

        assertThat(toolClient.toolName).isEqualTo("browser_reset");
        assertThat(toolClient.arguments).containsEntry("clearProfile", true);
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
    void screenshotLetsSidecarChooseItsContainerDirectory() {
        RecordingMcpToolClient toolClient = new RecordingMcpToolClient("""
                {"success":true,"message":"captured","screenshotPath":"data/browser/screenshots/home.png"}
                """);
        BrowserMcpClient client = new BrowserMcpClient(toolClient, properties());

        client.screenshot("home");

        assertThat(toolClient.toolName).isEqualTo("browser_screenshot");
        assertThat(toolClient.arguments).containsEntry("name", "home");
        assertThat(toolClient.arguments).doesNotContainKey("screenshotDir");
    }

    @Test
    void screenshotReadsImagePayloadFromStructuredContent() {
        RecordingMcpToolClient toolClient = new RecordingMcpToolClient("""
                {"content":[{"type":"text","text":"Screenshot captured"}],
                 "structuredContent":{
                   "success":true,
                   "message":"Screenshot captured",
                   "screenshotPath":"data/browser/screenshots/home.png",
                   "screenshotImageBase64":"iVBORw0KGgo=",
                   "screenshotContentType":"image/png",
                   "screenshotFileName":"home.png"
                 }}
                """);
        BrowserMcpClient client = new BrowserMcpClient(toolClient, properties());

        var result = client.screenshot("home");

        assertThat(result.screenshotImageBase64()).isEqualTo("iVBORw0KGgo=");
        assertThat(result.screenshotContentType()).isEqualTo("image/png");
        assertThat(result.screenshotFileName()).isEqualTo("home.png");
    }

    @Test
    void readsMcpErrorTextWhenStructuredContentIsMissing() {
        RecordingMcpToolClient toolClient = new RecordingMcpToolClient("""
                {"content":[{"type":"text","text":"EACCES: permission denied, mkdir 'data'"}],"isError":true}
                """);
        BrowserMcpClient client = new BrowserMcpClient(toolClient, properties());

        var result = client.screenshot("home");

        assertThat(result.success()).isFalse();
        assertThat(result.message()).isEqualTo("EACCES: permission denied, mkdir 'data'");
    }

    @Test
    void explainsSidecarConnectivityFailure() {
        RecordingMcpToolClient toolClient = new RecordingMcpToolClient("""
                {"success":true,"message":"unused"}
                """);
        toolClient.failure = new WebToolException("MCP Streamable HTTP 调用失败", new ClosedChannelException());
        BrowserMcpClient client = new BrowserMcpClient(toolClient, properties());

        assertThatThrownBy(() -> client.open("https://example.com"))
                .isInstanceOf(WebToolException.class)
                .hasMessageContaining("浏览器自动化服务不可用")
                .hasMessageContaining("127.0.0.1:3333")
                .hasMessageContaining("docker compose -f browser-mcp-sidecar/compose.yaml up -d --build");
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
        private RuntimeException failure;

        private RecordingMcpToolClient(String responseJson) {
            this.responseJson = responseJson;
        }

        @Override
        public McpCallResult callTool(String endpoint, String apiKey, String toolName, Map<String, Object> arguments) {
            if (failure != null) {
                throw failure;
            }
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
