package com.example.spring.image.generation.client;

import com.example.spring.image.generation.ImageGenerationClient;
import com.example.spring.image.generation.ImageGenerationException;
import com.example.spring.image.generation.ImageGenerationRequest;
import com.example.spring.image.generation.ImageGenerationResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class DashScopeImageGenerationClient implements ImageGenerationClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;
    private final String defaultSize;
    private final boolean defaultWatermark;
    private final boolean promptExtend;

    public DashScopeImageGenerationClient(
            RestClient.Builder builder,
            ObjectMapper objectMapper,
            @Value("${dashscope.api-key:}") String apiKey,
            @Value("${dashscope.image-base-url:https://ws-6gncy95g9skiwjfi.cn-beijing.maas.aliyuncs.com/api/v1}") String baseUrl,
            @Value("${dashscope.image-model:qwen-image-2.0-pro}") String model,
            @Value("${dashscope.image-size:1024*1024}") String defaultSize,
            @Value("${dashscope.image-watermark:false}") boolean defaultWatermark,
            @Value("${dashscope.image-prompt-extend:true}") boolean promptExtend) {
        this.restClient = builder.baseUrl(stripTrailingSlash(baseUrl)).build();
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = model;
        this.defaultSize = defaultSize;
        this.defaultWatermark = defaultWatermark;
        this.promptExtend = promptExtend;
    }

    @Override
    public ImageGenerationResult generate(ImageGenerationRequest request) {
        validateConfiguration();
        String prompt = buildPrompt(request);
        try {
            return restClient.post()
                    .uri("/services/aigc/multimodal-generation/generation")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .headers(headers -> headers.setBearerAuth(apiKey))
                    .body(requestBody(prompt, request))
                    .exchange((unusedRequest, response) -> {
                        if (response.getStatusCode().isError()) {
                            throw new ImageGenerationException("图片生成接口返回错误：" + responseError(response));
                        }
                        return parseResponse(response, prompt);
                    });
        } catch (ImageGenerationException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new ImageGenerationException("图片生成接口暂时不可用", exception);
        }
    }

    private ImageGenerationResult parseResponse(ClientHttpResponse response, String prompt) throws IOException {
        String body = new String(response.getBody().readAllBytes());
        if (body.isBlank()) {
            throw new ImageGenerationException("图片生成失败，请稍后重试");
        }

        JsonNode root = objectMapper.readTree(body);
        String imageUrl = firstNonBlank(
                textAt(root, "/output/choices/0/message/content/0/image"),
                textAt(root, "/output/choices/0/message/content/0/image_url/url"),
                textAt(root, "/output/results/0/url"),
                textAt(root, "/output/image_url"),
                textAt(root, "/image_url"));

        if (imageUrl == null || imageUrl.isBlank()) {
            throw new ImageGenerationException("图片生成失败，请稍后重试");
        }

        return new ImageGenerationResult(
                prompt,
                imageUrl.strip(),
                null,
                deriveFileName(imageUrl),
                defaultContentType(imageUrl),
                null,
                null);
    }

    private Map<String, Object> requestBody(String prompt, ImageGenerationRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("input", Map.of("messages", List.of(messageContent(prompt))));
        body.put("parameters", parameters(request));
        return body;
    }

    private Map<String, Object> messageContent(String prompt) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "user");
        message.put("content", List.of(Map.of("text", prompt)));
        return message;
    }

    private Map<String, Object> parameters(ImageGenerationRequest request) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("size", resolveSize(request));
        parameters.put("watermark", request != null && request.watermark() != null
                ? request.watermark()
                : defaultWatermark);
        parameters.put("prompt_extend", promptExtend);
        return parameters;
    }

    private String buildPrompt(ImageGenerationRequest request) {
        String prompt = request == null || request.prompt() == null ? "" : request.prompt().strip();
        String styleHint = request == null || request.styleHint() == null ? "" : request.styleHint().strip();
        if (styleHint.isBlank()) {
            return prompt;
        }
        return prompt + System.lineSeparator() + "风格要求：" + styleHint;
    }

    private String resolveSize(ImageGenerationRequest request) {
        if (request != null && request.width() != null && request.height() != null) {
            return request.width() + "*" + request.height();
        }
        return defaultSize;
    }

    private String textAt(JsonNode root, String pointer) {
        JsonNode value = root.at(pointer);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private void validateConfiguration() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new ImageGenerationException("未配置 DASHSCOPE_API_KEY");
        }
    }

    private String responseError(ClientHttpResponse response) {
        try {
            String body = new String(response.getBody().readAllBytes()).strip();
            if (body.isBlank()) {
                return response.getStatusCode().toString();
            }
            return response.getStatusCode() + "：" + body;
        } catch (IOException exception) {
            return "HTTP 响应读取失败";
        }
    }

    private String deriveFileName(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return "generated-image.png";
        }

        try {
            String path = new URI(imageUrl).getPath();
            if (path != null && !path.isBlank()) {
                int lastSlash = path.lastIndexOf('/');
                String name = lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
                if (!name.isBlank()) {
                    return name;
                }
            }
        } catch (Exception ignored) {
            // Fall through to the default name.
        }

        String lowerCaseUrl = imageUrl.toLowerCase(Locale.ROOT);
        if (lowerCaseUrl.endsWith(".jpg") || lowerCaseUrl.endsWith(".jpeg")) {
            return "generated-image.jpg";
        }
        if (lowerCaseUrl.endsWith(".webp")) {
            return "generated-image.webp";
        }
        return "generated-image.png";
    }

    private String defaultContentType(String imageUrl) {
        if (imageUrl == null) {
            return "image/png";
        }

        String lowerCaseUrl = imageUrl.toLowerCase(Locale.ROOT);
        if (lowerCaseUrl.endsWith(".jpg") || lowerCaseUrl.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lowerCaseUrl.endsWith(".webp")) {
            return "image/webp";
        }
        return "image/png";
    }

    private String stripTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String result = value.strip();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
