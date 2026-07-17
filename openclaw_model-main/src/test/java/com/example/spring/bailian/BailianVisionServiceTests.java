package com.example.spring.bailian;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.spring.agent.ImageAsset;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class BailianVisionServiceTests {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendsWechatImageAsOpenAiCompatibleDataUrl() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        AtomicReference<JsonNode> requestBody = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            requestBody.set(objectMapper.readTree(exchange.getRequestBody()));
            byte[] response = "{\"choices\":[{\"message\":{\"content\":\"图片中是一片蓝天\"}}]}"
                .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        BailianProperties properties = new BailianProperties();
        properties.setApiKey("vision-test-key");
        properties.setCompatibleBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setVisionModel("qwen3-vl-plus");
        properties.setRequestTimeout(Duration.ofSeconds(2));
        BailianVisionService service = new BailianVisionService(
            properties, objectMapper, HttpClient.newHttpClient());

        String reply = service.analyze(
            "请识别图片",
            List.of(new ImageAsset(new byte[] {1, 2, 3}, "image/png", "test.png")));

        assertThat(reply).isEqualTo("图片中是一片蓝天");
        assertThat(requestBody.get().path("model").asText()).isEqualTo("qwen3-vl-plus");
        JsonNode content = requestBody.get().path("messages").path(1).path("content");
        assertThat(content.path(0).path("text").asText()).isEqualTo("请识别图片");
        assertThat(content.path(1).path("image_url").path("url").asText())
            .startsWith("data:image/png;base64,");
    }
}
