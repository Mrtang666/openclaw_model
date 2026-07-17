package com.example.spring.bailian;

import com.example.spring.agent.ImageAsset;
import com.example.spring.media.RemoteImageLoader;
import com.fasterxml.jackson.databind.JsonNode;
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
public class BailianImageEditService {
    private final BailianProperties properties;
    private final RemoteImageLoader imageLoader;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public BailianImageEditService(
        BailianProperties properties,
        RemoteImageLoader imageLoader) {
        this(properties, imageLoader, new ObjectMapper(), HttpClient.newBuilder()
            .connectTimeout(properties.getConnectTimeout()).build());
    }

    BailianImageEditService(
        BailianProperties properties,
        RemoteImageLoader imageLoader,
        ObjectMapper objectMapper,
        HttpClient httpClient) {
        this.properties = properties;
        this.imageLoader = imageLoader;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    public ImageAsset edit(String prompt, List<ImageAsset> sourceImages)
        throws IOException, InterruptedException {
        if (!properties.isConfigured()) {
            throw new IllegalStateException("BAILIAN_API_KEY 未配置");
        }
        if (sourceImages == null || sourceImages.isEmpty()) {
            throw new IllegalArgumentException("图片编辑需要至少一张参考图片");
        }
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("请说明需要如何修改图片");
        }

        List<Map<String, String>> content = new ArrayList<>();
        for (ImageAsset image : sourceImages.stream().limit(3).toList()) {
            String dataUrl = "data:" + image.mediaType() + ";base64,"
                + Base64.getEncoder().encodeToString(image.data());
            content.add(Map.of("image", dataUrl));
        }
        content.add(Map.of("text", prompt.trim()));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getImageEditModel());
        body.put("input", Map.of(
            "messages", List.of(Map.of("role", "user", "content", content))));
        body.put("parameters", Map.of(
            "n", 1,
            "prompt_extend", true,
            "watermark", false,
            "size", properties.getImageSize()));

        HttpRequest request = HttpRequest.newBuilder(imageEditUri())
            .timeout(properties.getImageTimeout())
            .header("Authorization", "Bearer " + properties.getApiKey().trim())
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(
                objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
            .build();
        HttpResponse<String> response = httpClient.send(
            request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("百炼图片编辑接口返回 HTTP " + response.statusCode()
                + "：" + BailianChatService.abbreviate(response.body()));
        }
        String imageUrl = imageUrl(objectMapper.readTree(response.body()));
        return imageLoader.load(imageUrl);
    }

    URI imageEditUri() {
        String configured = properties.getImageEditUrl();
        if (configured != null && !configured.isBlank()) {
            return URI.create(configured.trim());
        }
        String compatibleBase = properties.getCompatibleBaseUrl();
        String suffix = "/compatible-mode/v1";
        if (compatibleBase == null || !compatibleBase.endsWith(suffix)) {
            throw new IllegalStateException(
                "无法从 BAILIAN_COMPATIBLE_BASE_URL 推导图片编辑地址，请配置 BAILIAN_IMAGE_EDIT_URL");
        }
        String root = compatibleBase.substring(0, compatibleBase.length() - suffix.length());
        return URI.create(root + "/api/v1/services/aigc/multimodal-generation/generation");
    }

    private static String imageUrl(JsonNode response) throws IOException {
        JsonNode content = response.path("output").path("choices").path(0)
            .path("message").path("content");
        if (content.isArray()) {
            for (JsonNode item : content) {
                String image = item.path("image").asText("");
                if (!image.isBlank()) {
                    return image;
                }
            }
        }
        throw new IOException("百炼图片编辑响应中没有图片地址");
    }
}
