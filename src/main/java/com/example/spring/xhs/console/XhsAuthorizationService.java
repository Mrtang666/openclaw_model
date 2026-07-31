package com.example.spring.xhs.console;

import com.example.spring.xhs.config.XhsCollectorProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.Map;
import java.util.function.Supplier;

@Service
public class XhsAuthorizationService {

    private static final String API_KEY_HEADER = "X-Collector-Api-Key";

    private final XhsCollectorProperties properties;
    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper;

    public XhsAuthorizationService(
            XhsCollectorProperties properties,
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.restClientBuilder = restClientBuilder;
        this.objectMapper = objectMapper;
    }

    public AuthorizationStatus status() {
        return authorization(exchange(() -> client().get()
                .uri("/internal/v1/auth/status")
                .retrieve().body(JsonNode.class)));
    }

    public AuthorizationStatus validate() {
        return authorization(exchange(() -> client().post()
                .uri("/internal/v1/auth/validate")
                .retrieve().body(JsonNode.class)));
    }

    public AuthorizationStatus updateCookie(String cookie) {
        if (cookie == null || cookie.isBlank()) {
            throw new IllegalArgumentException("Cookie 不能为空");
        }
        JsonNode response = exchange(() -> client().post()
                .uri("/internal/v1/auth/cookie")
                .contentType(MediaType.APPLICATION_JSON)
                .body(jsonBytes(Map.of("cookie", cookie.strip())))
                .retrieve().body(JsonNode.class));
        return authorization(response);
    }

    public QrAuthorization startQr() {
        return qr(exchange(() -> client().post()
                .uri("/internal/v1/auth/qr")
                .retrieve().body(JsonNode.class)));
    }

    public QrAuthorization pollQr(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("二维码授权会话不能为空");
        }
        return qr(exchange(() -> client().get()
                .uri(builder -> builder.path("/internal/v1/auth/qr/{sessionId}")
                        .build(sessionId.strip()))
                .retrieve().body(JsonNode.class)));
    }

    public void clear() {
        exchange(() -> {
            client().delete().uri("/internal/v1/auth").retrieve().toBodilessEntity();
            return objectMapper.createObjectNode();
        });
    }

    private RestClient client() {
        if (!properties.enabled() || properties.baseUrl().isBlank()) {
            throw new IllegalStateException("小红书采集 Sidecar 未启用或未配置地址");
        }
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        Duration timeout = properties.timeout().compareTo(Duration.ofSeconds(60)) < 0
                ? Duration.ofSeconds(60) : properties.timeout();
        factory.setConnectTimeout(properties.timeout());
        factory.setReadTimeout(timeout);
        RestClient.Builder builder = restClientBuilder.clone()
                .baseUrl(properties.baseUrl()).requestFactory(factory);
        if (!properties.apiKey().isBlank()) {
            builder.defaultHeader(API_KEY_HEADER, properties.apiKey());
        }
        return builder.build();
    }

    private JsonNode exchange(Supplier<JsonNode> request) {
        try {
            JsonNode response = request.get();
            if (response == null || response.isNull()) {
                throw new IllegalStateException("小红书授权服务返回空响应");
            }
            return response;
        } catch (RestClientResponseException exception) {
            throw new IllegalStateException(sidecarError(exception), exception);
        } catch (ResourceAccessException exception) {
            throw new IllegalStateException("无法连接小红书 Sidecar，请确认 18081 端口服务已启动", exception);
        }
    }

    private byte[] jsonBytes(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsBytes(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("授权请求序列化失败", exception);
        }
    }

    private String sidecarError(RestClientResponseException exception) {
        try {
            JsonNode response = objectMapper.readTree(exception.getResponseBodyAsByteArray());
            String message = text(response, "errorMessage");
            String code = text(response, "errorCode");
            if (!message.isBlank()) {
                return code.isBlank() ? message : message + "（" + code + "）";
            }
        } catch (Exception ignored) {
            // Use the HTTP status fallback below.
        }
        return "小红书授权服务调用失败（HTTP " + exception.getStatusCode().value() + "）";
    }

    private AuthorizationStatus authorization(JsonNode node) {
        return new AuthorizationStatus(
                text(node, "status"), node.path("collectAllowed").asBoolean(false),
                node.path("requiresReauthorization").asBoolean(false), text(node, "source"),
                text(node, "accountNickname"), text(node, "accountRedId"),
                text(node, "updatedAt"), text(node, "lastVerifiedAt"),
                text(node, "lastError"), node.path("consecutiveAuthFailures").asInt(0));
    }

    private QrAuthorization qr(JsonNode node) {
        JsonNode authorization = node.path("authorization");
        return new QrAuthorization(
                text(node, "sessionId"), text(node, "status"), text(node, "message"),
                text(node, "qrImage"), text(node, "expiresAt"),
                authorization.isObject() ? authorization(authorization) : null);
    }

    private String text(JsonNode node, String name) {
        JsonNode value = node == null ? null : node.get(name);
        return value == null || value.isNull() ? "" : value.asText("").strip();
    }

    public record AuthorizationStatus(
            String status,
            boolean collectAllowed,
            boolean requiresReauthorization,
            String source,
            String accountNickname,
            String accountRedId,
            String updatedAt,
            String lastVerifiedAt,
            String lastError,
            int consecutiveAuthFailures) {
    }

    public record QrAuthorization(
            String sessionId,
            String status,
            String message,
            String qrImage,
            String expiresAt,
            AuthorizationStatus authorization) {
    }
}
