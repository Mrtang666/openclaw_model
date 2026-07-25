package com.example.spring.wechat.netdisk.client;

import com.example.spring.wechat.netdisk.config.BaiduNetdiskProperties;
import com.example.spring.wechat.web.mcp.McpCallResult;
import com.example.spring.wechat.web.mcp.McpToolClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BaiduNetdiskMcpClientTests {

    @Test
    void keywordSearchCallsOfficialToolWithAccessTokenEndpoint() throws Exception {
        RecordingMcpToolClient toolClient = new RecordingMcpToolClient("{\"list\":[]}");
        BaiduNetdiskMcpClient client = new BaiduNetdiskMcpClient(toolClient, new ObjectMapper(), properties());

        client.keywordSearch("access-token-1", "项目文档", "/", 10);

        assertThat(toolClient.endpoint).isEqualTo("https://mcp-pan.baidu.com/sse?access_token=access-token-1");
        assertThat(toolClient.apiKey).isEqualTo("access-token-1");
        assertThat(toolClient.toolName).isEqualTo("file_keyword_search");
        assertThat(toolClient.arguments).containsEntry("key", "项目文档")
                .containsEntry("dir", "/")
                .containsEntry("num", 10);
    }

    @Test
    void uploadTextCallsTextUploadTool() throws Exception {
        RecordingMcpToolClient toolClient = new RecordingMcpToolClient("{\"fsid\":\"100\"}");
        BaiduNetdiskMcpClient client = new BaiduNetdiskMcpClient(toolClient, new ObjectMapper(), properties());

        client.uploadText("access-token-1", "内容", "/OpenClaw/", "note.md");

        assertThat(toolClient.toolName).isEqualTo("file_upload_by_text");
        assertThat(toolClient.arguments).containsEntry("content", "内容")
                .containsEntry("dir", "/OpenClaw/")
                .containsEntry("filename", "note.md");
    }

    private BaiduNetdiskProperties properties() {
        return new BaiduNetdiskProperties(
                true,
                "client-id",
                "app-key",
                "",
                "client-secret",
                "sign-key",
                "https://openclaw.example.com/api/netdisk/baidu/callback",
                "https://openapi.baidu.com/oauth/2.0/authorize",
                "https://openapi.baidu.com/oauth/2.0/token",
                "https://mcp-pan.baidu.com/sse",
                "test-encryption-key",
                10,
                30,
                20_000,
                5,
                "/OpenClaw/");
    }

    private static final class RecordingMcpToolClient implements McpToolClient {

        private final ObjectMapper objectMapper = new ObjectMapper();
        private final String responseJson;
        private String endpoint;
        private String apiKey;
        private String toolName;
        private Map<String, Object> arguments;

        private RecordingMcpToolClient(String responseJson) {
            this.responseJson = responseJson;
        }

        @Override
        public McpCallResult callTool(String endpoint, String apiKey, String toolName, Map<String, Object> arguments) {
            try {
                this.endpoint = endpoint;
                this.apiKey = apiKey;
                this.toolName = toolName;
                this.arguments = arguments;
                return new McpCallResult(objectMapper.readTree(responseJson), "session-1");
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }
    }
}
