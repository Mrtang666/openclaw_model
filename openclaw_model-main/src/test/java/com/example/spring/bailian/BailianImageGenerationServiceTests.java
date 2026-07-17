package com.example.spring.bailian;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.spring.agent.ImageAsset;
import com.example.spring.media.RemoteImageLoader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class BailianImageGenerationServiceTests {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void createsPollsAndDownloadsGeneratedImage() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        AtomicReference<JsonNode> createRequest = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/image-synthesis", exchange -> {
            createRequest.set(objectMapper.readTree(exchange.getRequestBody()));
            respond(exchange, "{\"output\":{\"task_id\":\"task-1\"}}");
        });
        server.createContext("/tasks/task-1", exchange -> respond(exchange, """
            {"output":{"task_status":"SUCCEEDED","results":[
            {"url":"https://example.com/generated.png"}]}}
            """));
        server.start();

        BailianProperties properties = new BailianProperties();
        properties.setApiKey("image-test-key");
        properties.setImageModel("wanx-v1");
        properties.setImageSynthesisUrl(
            "http://127.0.0.1:" + server.getAddress().getPort() + "/image-synthesis");
        properties.setTaskUrl(
            "http://127.0.0.1:" + server.getAddress().getPort() + "/tasks");
        properties.setImagePollInterval(Duration.ZERO);
        properties.setImageTimeout(Duration.ofSeconds(2));
        ImageAsset generated = new ImageAsset(new byte[] {9, 8}, "image/png", "generated.png");
        AtomicReference<String> downloadedUrl = new AtomicReference<>();
        RemoteImageLoader loader = new RemoteImageLoader() {
            @Override
            public ImageAsset load(String url) {
                downloadedUrl.set(url);
                return generated;
            }
        };

        BailianImageGenerationService service = new BailianImageGenerationService(
            properties, loader, objectMapper, HttpClient.newHttpClient());
        ImageAsset result = service.generate("水彩江南古镇");

        assertThat(result.fileName()).isEqualTo("generated.png");
        assertThat(createRequest.get().path("model").asText()).isEqualTo("wanx-v1");
        assertThat(createRequest.get().path("input").path("prompt").asText())
            .isEqualTo("水彩江南古镇");
        assertThat(downloadedUrl.get()).isEqualTo("https://example.com/generated.png");
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, String body)
        throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
