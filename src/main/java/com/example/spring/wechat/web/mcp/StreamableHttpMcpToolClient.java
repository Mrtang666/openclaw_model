package com.example.spring.wechat.web.mcp;

import com.example.spring.wechat.web.exception.WebToolException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 原生 Streamable HTTP MCP 客户端。
 *
 * <p>百炼 WebSearch MCP 属于 Streamable HTTP 服务，不能只 POST 一次 tools/call。
 * 这里按 MCP 标准流程执行 initialize、tools/list、tools/call，并兼容 JSON 与 SSE 两种响应格式。</p>
 */
@Component
public class StreamableHttpMcpToolClient implements McpToolClient {

    private static final Logger log = LoggerFactory.getLogger(StreamableHttpMcpToolClient.class);
    private static final String PROTOCOL_VERSION = "2025-03-26";
    private static final String SESSION_HEADER = "Mcp-Session-Id";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public StreamableHttpMcpToolClient(RestClient.Builder builder, ObjectMapper objectMapper) {
        this.restClient = builder.build();
        this.objectMapper = objectMapper;
    }

    @Override
    public McpCallResult callTool(String endpoint, String apiKey, String toolName, Map<String, Object> arguments) {
        if (endpoint == null || endpoint.isBlank()) {
            throw new WebToolException("MCP endpoint 不能为空");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new WebToolException("MCP api key 不能为空");
        }
        try {
            log.info("MCP Streamable HTTP initialize 开始，endpoint={}", endpoint);
            McpResponse initialized = post(endpoint, apiKey, "", initializeBody());
            String sessionId = initialized.sessionId();
            log.info("MCP Streamable HTTP initialize 完成，hasSession={}", sessionId != null && !sessionId.isBlank());
            log.info("MCP Streamable HTTP tools/list 开始");
            post(endpoint, apiKey, sessionId, listToolsBody());
            log.info("MCP Streamable HTTP tools/list 完成");
            log.info("MCP Streamable HTTP tools/call 开始，tool={}", toolName);
            McpResponse toolResponse = post(endpoint, apiKey, sessionId, callToolBody(toolName, arguments));
            log.info("MCP Streamable HTTP tools/call 完成，tool={}", toolName);
            return new McpCallResult(toolResponse.result(), firstNonBlank(toolResponse.sessionId(), sessionId));
        } catch (RestClientException exception) {
            throw new WebToolException("MCP Streamable HTTP 调用失败", exception);
        }
    }

    private McpResponse post(String endpoint, String apiKey, String sessionId, Map<String, Object> body) {
        return restClient.post()
                .uri(endpoint)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                .headers(headers -> fillHeaders(headers, apiKey, sessionId))
                .body(body)
                .exchange((request, response) -> {
                    if (response.getStatusCode().isError()) {
                        String errorBody = new String(response.getBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                        throw new WebToolException("MCP Streamable HTTP 返回错误：" + response.getStatusCode() + "，" + errorBody);
                    }
                    String responseSessionId = firstNonBlank(
                            response.getHeaders().getFirst(SESSION_HEADER),
                            response.getHeaders().getFirst(SESSION_HEADER.toLowerCase()));
                    String bodyText = new String(response.getBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    JsonNode root = parseBody(bodyText).orElse(objectMapper.createObjectNode());
                    return new McpResponse(root.path("result"), responseSessionId);
                });
    }

    private void fillHeaders(HttpHeaders headers, String apiKey, String sessionId) {
        headers.setBearerAuth(apiKey);
        headers.set("MCP-Protocol-Version", PROTOCOL_VERSION);
        if (sessionId != null && !sessionId.isBlank()) {
            headers.set(SESSION_HEADER, sessionId);
        }
    }

    private Optional<JsonNode> parseBody(String bodyText) {
        String body = bodyText == null ? "" : bodyText.strip();
        if (body.isBlank()) {
            return Optional.empty();
        }
        if (body.startsWith("{") || body.startsWith("[")) {
            return readJson(body);
        }
        for (String line : body.split("\\R")) {
            String text = line == null ? "" : line.strip();
            if (text.startsWith("data:")) {
                String data = text.substring("data:".length()).strip();
                if (!data.isBlank() && !"[DONE]".equals(data)) {
                    Optional<JsonNode> parsed = readJson(data);
                    if (parsed.isPresent()) {
                        return parsed;
                    }
                }
            }
        }
        return Optional.empty();
    }

    private Optional<JsonNode> readJson(String value) {
        try {
            return Optional.of(objectMapper.readTree(value));
        } catch (Exception exception) {
            return Optional.empty();
        }
    }

    private Map<String, Object> initializeBody() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("protocolVersion", PROTOCOL_VERSION);
        params.put("capabilities", Map.of());
        params.put("clientInfo", Map.of("name", "openclaw", "version", "1.0"));
        return jsonRpc("initialize", params);
    }

    private Map<String, Object> listToolsBody() {
        return jsonRpc("tools/list", Map.of());
    }

    private Map<String, Object> callToolBody(String toolName, Map<String, Object> arguments) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", toolName == null ? "" : toolName.strip());
        params.put("arguments", arguments == null ? Map.of() : arguments);
        return jsonRpc("tools/call", params);
    }

    private Map<String, Object> jsonRpc(String method, Map<String, Object> params) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jsonrpc", "2.0");
        body.put("id", UUID.randomUUID().toString());
        body.put("method", method);
        body.put("params", params);
        return body;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.strip();
            }
        }
        return "";
    }

    private record McpResponse(JsonNode result, String sessionId) {
    }
}
