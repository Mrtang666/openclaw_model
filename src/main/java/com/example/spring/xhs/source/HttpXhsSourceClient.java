package com.example.spring.xhs.source;

import com.example.spring.xhs.config.XhsCollectorProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "xhs.collector", name = "enabled", havingValue = "true")
public class HttpXhsSourceClient implements XhsSourceClient {

    private static final String API_KEY_HEADER = "X-Collector-Api-Key";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public HttpXhsSourceClient(
            RestClient.Builder builder,
            ObjectMapper objectMapper,
            XhsCollectorProperties properties) {
        this(buildClient(builder, properties), objectMapper);
    }

    HttpXhsSourceClient(RestClient restClient, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    private static RestClient buildClient(RestClient.Builder builder, XhsCollectorProperties properties) {
        if (properties.baseUrl().isBlank()) {
            throw new IllegalStateException("启用小红书采集侧车时必须配置 XHS_COLLECTOR_BASE_URL");
        }
        RestClient.Builder configured = builder.clone().baseUrl(properties.baseUrl());
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.timeout());
        requestFactory.setReadTimeout(properties.timeout());
        configured.requestFactory(requestFactory);
        if (!properties.apiKey().isBlank()) {
            configured.defaultHeader(API_KEY_HEADER, properties.apiKey());
        }
        return configured.build();
    }

    @Override
    public XhsCollectorSubmission submitSearch(XhsCollectionRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query", request.query());
        body.put("limit", request.limit());
        if (!request.cursor().isBlank()) {
            body.put("cursor", request.cursor());
        }
        JsonNode response = exchange(() -> restClient.post()
                .uri("/internal/v1/jobs/search")
                .contentType(MediaType.APPLICATION_JSON)
                .body(jsonBytes(body))
                .retrieve()
                .body(JsonNode.class));
        return new XhsCollectorSubmission(text(response, "jobId", "job_id"));
    }

    @Override
    public XhsCollectorJobResult getJob(String externalJobId) {
        if (externalJobId == null || externalJobId.isBlank()) {
            throw new IllegalArgumentException("externalJobId 不能为空");
        }
        JsonNode response = exchange(() -> restClient.get()
                .uri(builder -> builder.path("/internal/v1/jobs/{jobId}").build(externalJobId.strip()))
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(JsonNode.class));
        return new XhsCollectorJobResult(
                XhsCollectionStatus.from(text(response, "status")),
                response.path("complete").asBoolean(false),
                text(response, "nextCursor", "next_cursor"),
                first(response, "records", "posts"),
                text(response, "errorCode", "error_code"),
                text(response, "errorMessage", "error_message"),
                instant(text(response, "collectedAt", "collected_at")));
    }

    @Override
    public XhsResolvedLink resolveLink(String noteId, String query, int limit) {
        if (noteId == null || noteId.isBlank() || query == null || query.isBlank()) {
            throw new IllegalArgumentException("刷新原帖链接需要 noteId 和 query");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("noteId", noteId.strip());
        body.put("query", query.strip());
        body.put("limit", Math.max(1, Math.min(limit <= 0 ? 100 : limit, 100)));
        JsonNode response = exchange(() -> restClient.post()
                .uri("/internal/v1/links/resolve")
                .contentType(MediaType.APPLICATION_JSON)
                .body(jsonBytes(body))
                .retrieve()
                .body(JsonNode.class));
        return new XhsResolvedLink(
                "FOUND".equalsIgnoreCase(text(response, "status")),
                text(response, "accessUrl", "access_url"),
                text(response, "errorCode", "error_code"),
                text(response, "errorMessage", "error_message"));
    }

    private JsonNode exchange(java.util.function.Supplier<JsonNode> request) {
        try {
            JsonNode response = request.get();
            if (response == null || response.isNull()) {
                throw new XhsCollectorException("小红书采集侧车返回空响应");
            }
            return response;
        } catch (XhsCollectorException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            throw new XhsCollectorException(responseError(exception), exception);
        } catch (ResourceAccessException exception) {
            throw new XhsCollectorException(
                    "无法连接小红书采集 Sidecar，请确认 18081 端口服务已启动；本地可运行 .\\run-xhs-local.ps1",
                    exception);
        } catch (RuntimeException exception) {
            throw new XhsCollectorException("小红书采集侧车调用失败", exception);
        }
    }

    private byte[] jsonBytes(Map<String, Object> body) {
        try {
            return objectMapper.writeValueAsBytes(body);
        } catch (JsonProcessingException exception) {
            throw new XhsCollectorException("小红书采集请求序列化失败", exception);
        }
    }

    private String responseError(RestClientResponseException exception) {
        String errorCode = "";
        String errorMessage = "";
        try {
            JsonNode response = objectMapper.readTree(exception.getResponseBodyAsByteArray());
            errorCode = text(response, "errorCode", "error_code");
            errorMessage = text(response, "errorMessage", "error_message");
        } catch (Exception ignored) {
            // Fall back to the status code when the sidecar did not return its JSON error contract.
        }
        StringBuilder message = new StringBuilder("小红书采集侧车调用失败（HTTP ")
                .append(exception.getStatusCode().value());
        if (!errorCode.isBlank()) {
            message.append("，").append(errorCode);
        }
        message.append("）");
        if (!errorMessage.isBlank()) {
            message.append("：").append(errorMessage);
        }
        return message.toString();
    }

    private JsonNode first(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node == null ? null : node.get(name);
            if (value != null && !value.isNull()) {
                return value;
            }
        }
        return objectMapper.createArrayNode();
    }

    private String text(JsonNode node, String... names) {
        JsonNode value = first(node, names);
        return value.isValueNode() ? value.asText("").strip() : "";
    }

    private Instant instant(String value) {
        if (value == null || value.isBlank()) {
            return Instant.now();
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
            return Instant.now();
        }
    }
}
