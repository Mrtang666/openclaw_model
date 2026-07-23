package com.example.spring.wechat.web.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class StreamableHttpMcpToolClientTests {

    @Test
    void initializesListsToolsAndCallsToolWithSessionHeader() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        StreamableHttpMcpToolClient client = new StreamableHttpMcpToolClient(builder, new ObjectMapper());
        String endpoint = "https://dashscope.aliyuncs.com/api/v1/mcps/WebSearch/mcp";

        server.expect(requestTo(endpoint))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(jsonPath("$.method").value("initialize"))
                .andRespond(withSuccess("""
                        {"jsonrpc":"2.0","id":"1","result":{"protocolVersion":"2025-03-26"}}
                        """, MediaType.APPLICATION_JSON)
                        .header("Mcp-Session-Id", "session-1"));

        server.expect(requestTo(endpoint))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Mcp-Session-Id", "session-1"))
                .andExpect(jsonPath("$.method").value("tools/list"))
                .andRespond(withSuccess("""
                        {"jsonrpc":"2.0","id":"2","result":{"tools":[{"name":"bailian_web_search"}]}}
                        """, MediaType.APPLICATION_JSON));

        server.expect(requestTo(endpoint))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Mcp-Session-Id", "session-1"))
                .andExpect(jsonPath("$.method").value("tools/call"))
                .andExpect(jsonPath("$.params.name").value("bailian_web_search"))
                .andExpect(jsonPath("$.params.arguments.query").value("Qdrant Java"))
                .andRespond(withSuccess("""
                        {"jsonrpc":"2.0","id":"3","result":{"pages":[{"title":"Qdrant Java","url":"https://example.com"}]}}
                        """, MediaType.APPLICATION_JSON));

        McpCallResult result = client.callTool(
                endpoint,
                "test-key",
                "bailian_web_search",
                Map.of("query", "Qdrant Java"));

        assertThat(result.sessionId()).isEqualTo("session-1");
        assertThat(result.result().path("pages").get(0).path("title").asText()).isEqualTo("Qdrant Java");
        server.verify();
    }

    @Test
    void parsesSseDataResponse() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        StreamableHttpMcpToolClient client = new StreamableHttpMcpToolClient(builder, new ObjectMapper());
        String endpoint = "https://dashscope.aliyuncs.com/api/v1/mcps/WebSearch/mcp";

        server.expect(requestTo(endpoint))
                .andRespond(withSuccess("{\"jsonrpc\":\"2.0\",\"result\":{}}", MediaType.APPLICATION_JSON)
                        .header("Mcp-Session-Id", "session-1"));
        server.expect(requestTo(endpoint))
                .andRespond(withSuccess("{\"jsonrpc\":\"2.0\",\"result\":{\"tools\":[]}}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(endpoint))
                .andRespond(withSuccess("""
                        event: message
                        data: {"jsonrpc":"2.0","id":"3","result":{"content":[{"type":"text","text":"ok"}]}}

                        """, MediaType.TEXT_EVENT_STREAM));

        McpCallResult result = client.callTool(endpoint, "test-key", "bailian_web_search", Map.of());

        assertThat(result.result().path("content").get(0).path("text").asText()).isEqualTo("ok");
        server.verify();
    }
}
