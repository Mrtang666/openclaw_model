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
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class BailianImageEditServiceTests {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void editsHistoricalImageWithQwenImage() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        AtomicReference<JsonNode> requestBody = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/edit", exchange -> {
            requestBody.set(objectMapper.readTree(exchange.getRequestBody()));
            byte[] response = """
                {"output":{"choices":[{"message":{"content":[
                {"image":"https://example.com/edited.png"}]}}]}}
                """.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        BailianProperties properties = new BailianProperties();
        properties.setApiKey("edit-test-key");
        properties.setImageEditModel("qwen-image-2.0");
        properties.setImageEditUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/edit");
        properties.setImageTimeout(Duration.ofSeconds(2));
        ImageAsset edited = new ImageAsset(new byte[] {9}, "image/png", "edited.png");
        AtomicReference<String> downloadedUrl = new AtomicReference<>();
        RemoteImageLoader loader = new RemoteImageLoader() {
            @Override
            public ImageAsset load(String url) {
                downloadedUrl.set(url);
                return edited;
            }
        };

        BailianImageEditService service = new BailianImageEditService(
            properties, loader, objectMapper, HttpClient.newHttpClient());
        ImageAsset result = service.edit(
            "把背景改成夜景",
            List.of(new ImageAsset(new byte[] {1, 2, 3}, "image/png", "history.png")));

        assertThat(result.fileName()).isEqualTo("edited.png");
        assertThat(requestBody.get().path("model").asText()).isEqualTo("qwen-image-2.0");
        JsonNode content = requestBody.get().path("input").path("messages").path(0).path("content");
        assertThat(content.path(0).path("image").asText())
            .startsWith("data:image/png;base64,");
        assertThat(content.path(1).path("text").asText()).isEqualTo("把背景改成夜景");
        assertThat(downloadedUrl.get()).isEqualTo("https://example.com/edited.png");
    }
}
