package com.example.spring.wechat.web.client;

import com.example.spring.wechat.web.mcp.McpCallResult;
import com.example.spring.wechat.web.mcp.McpToolClient;
import com.example.spring.wechat.web.exception.WebToolException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.Map;

class BailianWebSearchClientTests {

    @Test
    void callsBailianWebSearchMcpToolAndParsesPages() {
        RecordingMcpToolClient mcpToolClient = new RecordingMcpToolClient("""
                        {
                          "status": 0,
                          "pages": [
                            {
                              "title": "OpenClaw 项目说明",
                              "url": "https://example.com/openclaw",
                              "content": "这是一段搜索摘要。",
                              "source": "example"
                            }
                          ]
                        }
                        """);

        BailianWebSearchClient client = new BailianWebSearchClient(
                RestClient.builder(),
                mcpToolClient,
                "https://dashscope.aliyuncs.com/api/v1/mcps/WebSearch/mcp",
                "test-key");

        var results = client.search("OpenClaw", 3, "any", "zh-CN");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).title()).isEqualTo("OpenClaw 项目说明");
        assertThat(results.get(0).url()).isEqualTo("https://example.com/openclaw");
        assertThat(results.get(0).snippet()).isEqualTo("这是一段搜索摘要。");
        assertThat(mcpToolClient.toolName).isEqualTo("bailian_web_search");
        assertThat(mcpToolClient.arguments).containsEntry("query", "OpenClaw").containsEntry("count", 3);
    }

    @Test
    void parsesSearchResultsNestedInsideMcpTextContent() {
        RecordingMcpToolClient mcpToolClient = new RecordingMcpToolClient("""
                        {
                          "content": [
                            {
                              "type": "text",
                              "text": "{\\"pages\\":[{\\"pageTitle\\":\\"Qdrant Java 接入\\",\\"pageUrl\\":\\"https://example.com/qdrant-java\\",\\"summary\\":\\"Qdrant Java 接入摘要\\"}]}"
                            }
                          ]
                        }
                        """);

        BailianWebSearchClient client = new BailianWebSearchClient(
                RestClient.builder(),
                mcpToolClient,
                "https://dashscope.aliyuncs.com/api/v1/mcps/WebSearch/mcp",
                "test-key");

        var results = client.search("Qdrant Java 接入方式", 5, "any", "zh-CN");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).title()).isEqualTo("Qdrant Java 接入");
        assertThat(results.get(0).url()).isEqualTo("https://example.com/qdrant-java");
        assertThat(results.get(0).snippet()).isEqualTo("Qdrant Java 接入摘要");
    }

    @Test
    void fallsBackToDashScopeChatSearchWhenMcpReturnsEmptyBody() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://workspace.example.com/compatible-mode/v1/chat/completions"))
                .andExpect(jsonPath("$.extra_body.enable_search").value(true))
                .andExpect(jsonPath("$.extra_body.enable_thinking").value(false))
                .andExpect(jsonPath("$.extra_body.search_options.forced_search").value(true))
                .andRespond(withSuccess("""
                        {
                          "choices": [
                            {
                              "message": {
                                "content": "Qdrant Java 可以通过官方 Java 客户端接入，参考 [Qdrant Java Client](https://qdrant.tech/documentation/frameworks/java/)。"
                              }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        BailianWebSearchClient client = new BailianWebSearchClient(
                builder,
                new RecordingMcpToolClient("{}"),
                "https://dashscope.aliyuncs.com/api/v1/mcps/WebSearch/mcp",
                "test-key",
                "https://workspace.example.com/compatible-mode/v1",
                "qwen3.7-max-2026-06-08");

        var results = client.search("Qdrant Java 接入方式", 5, "any", "zh-CN");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).title()).contains("Qdrant Java");
        assertThat(results.get(0).url()).isEqualTo("https://qdrant.tech/documentation/frameworks/java/");
        assertThat(results.get(0).snippet()).contains("官方 Java 客户端");
        server.verify();
    }

    @Test
    void fallsBackToDashScopeChatSearchWhenMcpCallFails() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://workspace.example.com/compatible-mode/v1/chat/completions"))
                .andRespond(withSuccess("""
                        {
                          "choices": [
                            {
                              "message": {
                                "content": "参考 [Qdrant Java Client](https://qdrant.tech/documentation/frameworks/java/) 接入。"
                              }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        BailianWebSearchClient client = new BailianWebSearchClient(
                builder,
                (endpoint, apiKey, toolName, arguments) -> {
                    throw new WebToolException("MCP 调用失败");
                },
                "https://dashscope.aliyuncs.com/api/v1/mcps/WebSearch/mcp",
                "test-key",
                "https://workspace.example.com/compatible-mode/v1",
                "qwen3.7-max-2026-06-08");

        var results = client.search("Qdrant Java 接入方式", 5, "any", "zh-CN");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).url()).isEqualTo("https://qdrant.tech/documentation/frameworks/java/");
        server.verify();
    }

    private static final class RecordingMcpToolClient implements McpToolClient {

        private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

        private final String resultJson;
        private String toolName;
        private Map<String, Object> arguments;

        private RecordingMcpToolClient(String resultJson) {
            this.resultJson = resultJson;
        }

        @Override
        public McpCallResult callTool(String endpoint, String apiKey, String toolName, Map<String, Object> arguments) {
            try {
                this.toolName = toolName;
                this.arguments = arguments;
                return new McpCallResult(OBJECT_MAPPER.readTree(resultJson), "session-1");
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }
    }
}
