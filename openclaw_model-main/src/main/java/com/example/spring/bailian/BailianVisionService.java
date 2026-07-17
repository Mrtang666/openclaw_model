package com.example.spring.bailian;

import com.example.spring.agent.ImageAsset;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class BailianVisionService {
    private final BailianProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public BailianVisionService(BailianProperties properties) {
        this(properties, new ObjectMapper(), HttpClient.newBuilder()
            .connectTimeout(properties.getConnectTimeout()).build());
    }

    BailianVisionService(
        BailianProperties properties,
        ObjectMapper objectMapper,
        HttpClient httpClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    public String analyze(String prompt, List<ImageAsset> images)
        throws IOException, InterruptedException {
        if (!properties.isConfigured()) {
            throw new IllegalStateException("BAILIAN_API_KEY 未配置");
        }
        if (images == null || images.isEmpty()) {
            throw new IllegalArgumentException("没有可识别的图片");
        }

        List<Map<String, Object>> content = new ArrayList<>();
        content.add(Map.of(
            "type", "text",
            "text", prompt == null || prompt.isBlank()
                ? "请详细识别并说明图片中的内容。"
                : prompt));
        for (ImageAsset image : images) {
            String dataUrl = "data:" + image.mediaType() + ";base64,"
                + Base64.getEncoder().encodeToString(image.data());
            content.add(Map.of(
                "type", "image_url",
                "image_url", Map.of("url", dataUrl)));
        }

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content",
            "你是图片识别助手。请结合用户要求识别图片，并使用中文给出准确反馈。"));
        messages.add(Map.of("role", "user", "content", content));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getVisionModel());
        body.put("messages", messages);
        body.put("stream", false);

        HttpRequest request = HttpRequest.newBuilder(chatCompletionsUri())
            .timeout(properties.getRequestTimeout())
            .header("Authorization", "Bearer " + properties.getApiKey().trim())
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(
                objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
            .build();
        HttpResponse<String> response = httpClient.send(
            request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("百炼视觉接口返回 HTTP " + response.statusCode()
                + "：" + BailianChatService.abbreviate(response.body()));
        }
        return BailianResponseParser.assistantText(objectMapper.readTree(response.body()));
    }

    private URI chatCompletionsUri() {
        String base = properties.getCompatibleBaseUrl();
        return URI.create((base.endsWith("/") ? base : base + "/") + "chat/completions");
    }
}
