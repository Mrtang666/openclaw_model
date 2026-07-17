package com.example.vision;

import com.example.LocalLLMService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;

public class VisionService {

    private static final Logger log = LoggerFactory.getLogger(VisionService.class);

    private final String apiKey;
    private final String apiUrl;
    private final String model;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public VisionService() {
        LocalLLMService.Config cfg = LocalLLMService.getConfig();
        this.apiKey = cfg.getApiKey();
        this.apiUrl = cfg.getApiUrl();
        this.model = cfg.getVisionModel();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.objectMapper = new ObjectMapper()
                .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public VisionService(String apiKey, String apiUrl, String model) {
        this.apiKey = apiKey;
        this.apiUrl = apiUrl;
        this.model = model;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.objectMapper = new ObjectMapper()
                .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public String describeImage(byte[] imageBytes, String mimeType) {
        return describeImage(imageBytes, mimeType, "请详细描述这张图片的内容，包括主体、颜色、动作、场景等。");
    }

    public String describeImage(byte[] imageBytes, String mimeType, String prompt) {
        try {
            String base64 = Base64.getEncoder().encodeToString(imageBytes);
            String dataUrl = "data:" + mimeType + ";base64," + base64;

            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", model);
            body.put("temperature", 0.7);
            body.put("max_tokens", 2048);
            body.put("stream", false);

            ArrayNode messages = body.putArray("messages");

            ObjectNode systemMsg = messages.addObject();
            systemMsg.put("role", "system");
            systemMsg.put("content", "你是一个专业的图片识别助手。请用中文详细描述图片内容。");

            ObjectNode userMsg = messages.addObject();
            userMsg.put("role", "user");
            ArrayNode content = userMsg.putArray("content");

            ObjectNode textPart = content.addObject();
            textPart.put("type", "text");
            textPart.put("text", prompt != null && !prompt.isEmpty() ? prompt : "请描述这张图片的内容");

            ObjectNode imagePart = content.addObject();
            imagePart.put("type", "image_url");
            ObjectNode imageUrl = imagePart.putObject("image_url");
            imageUrl.put("url", dataUrl);

            String requestBody = objectMapper.writeValueAsString(body);

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(120));
            if (apiKey != null && !apiKey.isEmpty()) {
                requestBuilder.header("Authorization", "Bearer " + apiKey);
            }
            HttpRequest httpRequest = requestBuilder
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("VLM API error: status={}, body={}", response.statusCode(),
                        response.body() != null ? response.body().substring(0, Math.min(300, response.body().length())) : "");
                return null;
            }

            String respBody = response.body();
            var root = objectMapper.readTree(respBody);
            var choice = root.path("choices").get(0);
            if (choice != null) {
                String text = choice.path("message").path("content").asText(null);
                if (text != null && !text.isBlank()) {
                    log.info("图片识别成功 (model={}): {}...", model, text.substring(0, Math.min(50, text.length())));
                    return text;
                }
            }
            log.warn("VLM 返回了空结果: {}", respBody.length() > 200 ? respBody.substring(0, 200) : respBody);
            return null;

        } catch (Exception e) {
            log.error("图片识别失败", e);
            return null;
        }
    }
}
