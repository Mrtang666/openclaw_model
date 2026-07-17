package com.example.spring.bailian;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.spring.memory.MemoryMessage;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class BailianChatServiceTests {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void usesBailianCompatibleApiAndKeepsPerUserHistory() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        List<JsonNode> requests = new ArrayList<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requests.add(objectMapper.readTree(exchange.getRequestBody()));
            byte[] response = "{\"choices\":[{\"message\":{\"content\":\"模型回复\"}}]}"
                .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        BailianProperties properties = new BailianProperties();
        properties.setApiKey("test-runtime-key");
        properties.setCompatibleBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setChatModel("qwen-plus");
        properties.setSystemPrompt("使用中文回答");
        properties.setRequestTimeout(Duration.ofSeconds(2));
        BailianChatService service = new BailianChatService(
            properties, objectMapper, HttpClient.newHttpClient());

        assertThat(service.chat("user-1", "第一问", List.of())).isEqualTo("模型回复");
        assertThat(service.chat("user-1", "第二问", List.of(
            new MemoryMessage("user", "第一问"),
            new MemoryMessage("assistant", "模型回复"))))
            .isEqualTo("模型回复");
        assertThat(authorization.get()).isEqualTo("Bearer test-runtime-key");
        assertThat(requests).hasSize(2);
        assertThat(requests.get(0).path("model").asText()).isEqualTo("qwen-plus");
        assertThat(requests.get(0).path("messages")).hasSize(2);
        assertThat(requests.get(1).path("messages")).hasSize(4);
        assertThat(requests.get(1).path("messages").path(1).path("content").asText())
            .isEqualTo("第一问");
    }
}
