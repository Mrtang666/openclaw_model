package com.example.spring.wechat.taxi.client;

import com.example.spring.wechat.web.exception.WebToolException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Minimal Didi Streamable HTTP MCP client. The key is kept out of logs. */
@Component
public class DidiMcpClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final DidiMcpProperties properties;

    public DidiMcpClient(RestClient.Builder builder, ObjectMapper objectMapper, DidiMcpProperties properties) {
        this.restClient = builder.build();
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public JsonNode call(String toolName, Map<String, Object> arguments) {
        if (!properties.enabled()) {
            throw new WebToolException("滴滴打车服务未启用");
        }
        if (properties.key().isBlank()) {
            throw new WebToolException("未配置 DIDI_MCP_KEY");
        }
        try {
            String sessionId = post(initializeBody(), "").sessionId();
            post(jsonRpc("tools/list", Map.of()), sessionId);
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("name", toolName == null ? "" : toolName.strip());
            params.put("arguments", arguments == null ? Map.of() : arguments);
            return unwrapToolResult(post(jsonRpc("tools/call", params), sessionId).result());
        } catch (RestClientException exception) {
            throw new WebToolException("滴滴 MCP 调用失败", exception);
        }
    }

    private McpResponse post(Map<String, Object> body, String sessionId) {
        return restClient.post()
                .uri(UriComponentsBuilder.fromUriString(stripTrailingSlash(properties.endpoint()))
                        .replaceQueryParam("key", properties.key())
                        .build(true)
                        .toUri())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                .headers(headers -> {
                    headers.set("MCP-Protocol-Version", "2024-11-05");
                    if (sessionId != null && !sessionId.isBlank()) {
                        headers.set("Mcp-Session-Id", sessionId);
                    }
                })
                .body(body)
                .exchange((request, response) -> {
                    if (response.getStatusCode().isError()) {
                        throw new WebToolException("滴滴 MCP 返回 HTTP 错误：" + response.getStatusCode());
                    }
                    return parseResponse(
                            response.getBody().readAllBytes(),
                            response.getHeaders().getFirst("Mcp-Session-Id"));
                });
    }

    private McpResponse parseResponse(byte[] bytes, String sessionId) {
        try {
            String body = new String(bytes == null ? new byte[0] : bytes, StandardCharsets.UTF_8).strip();
            if (body.startsWith("data:")) {
                body = body.lines()
                        .filter(line -> line.startsWith("data:"))
                        .map(line -> line.substring("data:".length()).strip())
                        .filter(line -> !line.isBlank() && !"[DONE]".equals(line))
                        .findFirst()
                        .orElse(body);
            }
            JsonNode root = objectMapper.readTree(body);
            if (root.has("error")) {
                throw new WebToolException("滴滴 MCP 返回错误：" + root.path("error").toString());
            }
            return new McpResponse(root.path("result"), sessionId);
        } catch (WebToolException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new WebToolException("滴滴 MCP 响应解析失败", exception);
        }
    }

    private Map<String, Object> initializeBody() {
        return jsonRpc("initialize", Map.of(
                "protocolVersion", "2024-11-05",
                "capabilities", Map.of(),
                "clientInfo", Map.of("name", "openclaw", "version", "1.0")));
    }

    private Map<String, Object> jsonRpc(String method, Map<String, Object> params) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jsonrpc", "2.0");
        body.put("id", UUID.randomUUID().toString());
        body.put("method", method);
        body.put("params", params);
        return body;
    }

    private String stripTrailingSlash(String value) {
        String result = value == null ? "" : value.strip();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private JsonNode unwrapToolResult(JsonNode result) {
        if (result == null || result.isMissingNode()) {
            return result;
        }
        if (result.path("isError").asBoolean(false)) {
            String message = result.path("content").isArray() && !result.path("content").isEmpty()
                    ? result.path("content").get(0).path("text").asText("滴滴工具调用失败")
                    : "滴滴工具调用失败";
            throw new WebToolException(message);
        }
        JsonNode structured = result.path("structuredContent");
        if (!structured.isMissingNode() && !structured.isNull()) {
            return structured;
        }
        JsonNode content = result.path("content");
        if (content.isArray()) {
            for (JsonNode item : content) {
                String text = item.path("text").asText("").strip();
                if (!text.isBlank()) {
                    try {
                        return objectMapper.readTree(text);
                    } catch (Exception ignored) {
                        return item;
                    }
                }
            }
        }
        return result;
    }

    private record McpResponse(JsonNode result, String sessionId) {
    }
}
