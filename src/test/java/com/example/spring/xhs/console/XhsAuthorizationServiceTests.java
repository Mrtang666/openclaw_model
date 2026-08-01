package com.example.spring.xhs.console;

import com.example.spring.xhs.config.XhsCollectorProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class XhsAuthorizationServiceTests {

    @Test
    void readsSanitizedAuthorizationStatusAndForwardsApiKey() throws Exception {
        AtomicReference<String> apiKey = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/v1/auth/status", exchange -> {
            apiKey.set(exchange.getRequestHeaders().getFirst("X-Collector-Api-Key"));
            respond(exchange, 200, """
                    {"status":"VALID","collectAllowed":true,"source":"QR",
                     "accountNickname":"测试账号","accountRedId":"red-1",
                     "lastVerifiedAt":"2026-07-31T08:00:00Z","consecutiveAuthFailures":0}
                    """);
        });
        server.start();
        try {
            XhsAuthorizationService service = service(server, "collector-secret");

            XhsAuthorizationService.AuthorizationStatus status = service.status();

            assertThat(status.status()).isEqualTo("VALID");
            assertThat(status.collectAllowed()).isTrue();
            assertThat(status.accountNickname()).isEqualTo("测试账号");
            assertThat(apiKey.get()).isEqualTo("collector-secret");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void sendsCookieOnlyToInternalAuthorizationEndpointAndMapsErrors() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/v1/auth/cookie", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 422, "{\"errorCode\":\"AUTH_INVALID\",\"errorMessage\":\"登录会话无效\"}");
        });
        server.start();
        try {
            XhsAuthorizationService service = service(server, "");

            assertThatThrownBy(() -> service.updateCookie("a1=test; web_session=expired"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("登录会话无效")
                    .hasMessageContaining("AUTH_INVALID");
            assertThat(new ObjectMapper().readTree(body.get()).path("cookie").asText())
                    .isEqualTo("a1=test; web_session=expired");
        } finally {
            server.stop(0);
        }
    }

    private XhsAuthorizationService service(HttpServer server, String apiKey) {
        return new XhsAuthorizationService(
                new XhsCollectorProperties(
                        true, "http://127.0.0.1:" + server.getAddress().getPort(), apiKey,
                        Duration.ofSeconds(2), Duration.ofSeconds(1), 3),
                RestClient.builder(), new ObjectMapper());
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String json)
            throws java.io.IOException {
        byte[] response = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json;charset=UTF-8");
        exchange.sendResponseHeaders(status, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }
}
