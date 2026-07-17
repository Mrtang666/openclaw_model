package com.example.spring.bailian;

import com.example.spring.agent.ImageAsset;
import com.example.spring.media.RemoteImageLoader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class BailianImageGenerationService {
    private final BailianProperties properties;
    private final RemoteImageLoader imageLoader;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public BailianImageGenerationService(
        BailianProperties properties,
        RemoteImageLoader imageLoader) {
        this(properties, imageLoader, new ObjectMapper(), HttpClient.newBuilder()
            .connectTimeout(properties.getConnectTimeout()).build());
    }

    BailianImageGenerationService(
        BailianProperties properties,
        RemoteImageLoader imageLoader,
        ObjectMapper objectMapper,
        HttpClient httpClient) {
        this.properties = properties;
        this.imageLoader = imageLoader;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    public ImageAsset generate(String prompt) throws IOException, InterruptedException {
        if (!properties.isConfigured()) {
            throw new IllegalStateException("BAILIAN_API_KEY 未配置");
        }
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("请提供需要生成的图片内容");
        }

        String taskId = createTask(prompt.trim());
        Instant deadline = Instant.now().plus(properties.getImageTimeout());
        while (Instant.now().isBefore(deadline)) {
            JsonNode task = getTask(taskId);
            String status = task.path("output").path("task_status").asText();
            if ("SUCCEEDED".equalsIgnoreCase(status)) {
                String imageUrl = task.path("output").path("results").path(0).path("url").asText();
                if (imageUrl.isBlank()) {
                    throw new IOException("百炼图片生成任务成功，但未返回图片地址");
                }
                return imageLoader.load(imageUrl);
            }
            if ("FAILED".equalsIgnoreCase(status)
                || "CANCELED".equalsIgnoreCase(status)
                || "UNKNOWN".equalsIgnoreCase(status)) {
                throw new IOException("百炼图片生成失败："
                    + task.path("output").path("message").asText(status));
            }
            Thread.sleep(properties.getImagePollInterval().toMillis());
        }
        throw new IOException("百炼图片生成超时");
    }

    private String createTask(String prompt) throws IOException, InterruptedException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getImageModel());
        body.put("input", Map.of("prompt", prompt));
        body.put("parameters", Map.of("size", properties.getImageSize(), "n", 1));

        HttpRequest request = HttpRequest.newBuilder(URI.create(properties.getImageSynthesisUrl()))
            .timeout(properties.getRequestTimeout())
            .header("Authorization", "Bearer " + properties.getApiKey().trim())
            .header("Content-Type", "application/json")
            .header("X-DashScope-Async", "enable")
            .POST(HttpRequest.BodyPublishers.ofString(
                objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
            .build();
        JsonNode response = sendJson(request, "创建图片生成任务");
        String taskId = response.path("output").path("task_id").asText();
        if (taskId.isBlank()) {
            throw new IOException("百炼未返回图片生成任务 ID");
        }
        return taskId;
    }

    private JsonNode getTask(String taskId) throws IOException, InterruptedException {
        String base = properties.getTaskUrl();
        String url = (base.endsWith("/") ? base : base + "/")
            + URLEncoder.encode(taskId, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .timeout(properties.getRequestTimeout())
            .header("Authorization", "Bearer " + properties.getApiKey().trim())
            .GET()
            .build();
        return sendJson(request, "查询图片生成任务");
    }

    private JsonNode sendJson(HttpRequest request, String operation)
        throws IOException, InterruptedException {
        HttpResponse<String> response = httpClient.send(
            request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException(operation + "返回 HTTP " + response.statusCode()
                + "：" + BailianChatService.abbreviate(response.body()));
        }
        return objectMapper.readTree(response.body());
    }
}
