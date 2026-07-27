package com.example.spring.wechat.video.client;

import com.example.spring.wechat.model.WechatIncomingVideo;
import com.example.spring.wechat.video.VideoUnderstandingException;
import com.example.spring.wechat.video.model.VideoUnderstandingRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ZhipuVideoUnderstandingClient implements VideoUnderstandingClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String apiUrl;
    private final String model;

    public ZhipuVideoUnderstandingClient(
            RestClient.Builder builder,
            ObjectMapper objectMapper,
            @Value("${zhipu.api.key:${BIGMODEL_API_KEY:${ZHIPU_API_KEY:}}}") String apiKey,
            @Value("${zhipu.api.url:${BIGMODEL_API_URL:https://open.bigmodel.cn/api/paas/v4/chat/completions}}") String apiUrl,
            @Value("${zhipu.api.model:${BIGMODEL_VISION_MODEL:${ZHIPU_VISION_MODEL:glm-4v-plus-0111}}}") String model) {
        this.restClient = builder.build();
        this.objectMapper = objectMapper;
        this.apiKey = apiKey == null ? "" : apiKey.strip();
        this.apiUrl = apiUrl == null ? "" : apiUrl.strip();
        this.model = model == null ? "" : model.strip();
    }

    @Override
    public String reply(VideoUnderstandingRequest request) {
        validateConfiguration();
        validateRequest(request);
        try {
            JsonNode response = restClient.post()
                    .uri(apiUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .headers(headers -> headers.setBearerAuth(apiKey))
                    .body(requestBody(request))
                    .exchange((httpRequest, httpResponse) -> {
                        if (httpResponse.getStatusCode().isError()) {
                            throw new VideoUnderstandingException("视频理解接口返回错误：" + responseError(httpResponse));
                        }
                        try {
                            return objectMapper.readTree(httpResponse.getBody());
                        } catch (IOException exception) {
                            throw new VideoUnderstandingException("视频理解响应读取失败", exception);
                        }
                    });
            return extractText(response);
        } catch (VideoUnderstandingException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new VideoUnderstandingException("视频理解服务暂时不可用", exception);
        }
    }

    private Map<String, Object> requestBody(VideoUnderstandingRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", List.of(Map.of(
                "role", "user",
                "content", userContent(request))));
        body.put("stream", false);
        return body;
    }

    private List<Map<String, Object>> userContent(VideoUnderstandingRequest request) {
        List<Map<String, Object>> content = new ArrayList<>();
        for (WechatIncomingVideo video : request.videos()) {
            content.add(Map.of(
                    "type", "video_url",
                    "video_url", Map.of("url", videoValue(video))));
        }
        content.add(Map.of("type", "text", "text", request.instruction()));
        return content;
    }

    private String videoValue(WechatIncomingVideo video) {
        if (video == null) {
            throw new VideoUnderstandingException("视频输入为空");
        }
        if (video.hasSourceUrl()) {
            return video.sourceUrl();
        }
        if (video.hasBytes()) {
            return Base64.getEncoder().encodeToString(video.bytes());
        }
        throw new VideoUnderstandingException("视频缺少可分析的内容");
    }

    private String extractText(JsonNode response) {
        JsonNode choices = response.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            throw new VideoUnderstandingException("视频理解没有返回结果");
        }
        JsonNode content = choices.get(0).path("message").path("content");
        String text = extractContentText(content);
        if (text.isBlank()) {
            throw new VideoUnderstandingException("视频理解没有返回有效文本");
        }
        return text;
    }

    private String extractContentText(JsonNode content) {
        if (content == null || content.isMissingNode() || content.isNull()) {
            return "";
        }
        if (content.isTextual()) {
            return content.asText("").strip();
        }
        if (content.isArray()) {
            StringBuilder text = new StringBuilder();
            for (JsonNode item : content) {
                String chunk = item.path("text").asText("");
                if (!chunk.isBlank()) {
                    text.append(chunk);
                }
            }
            return text.toString().strip();
        }
        return content.asText("").strip();
    }

    private void validateConfiguration() {
        if (apiKey.isBlank()) {
            throw new VideoUnderstandingException("未配置 zhipu.api.key 或 BIGMODEL_API_KEY");
        }
        if (apiUrl.isBlank()) {
            throw new VideoUnderstandingException("未配置 zhipu.api.url 或 BIGMODEL_API_URL");
        }
        if (model.isBlank()) {
            throw new VideoUnderstandingException("未配置 zhipu.api.model 或 BIGMODEL_VISION_MODEL");
        }
    }

    private void validateRequest(VideoUnderstandingRequest request) {
        if (request == null || !request.hasVideos()) {
            throw new VideoUnderstandingException("没有收到可分析的视频");
        }
    }

    private String responseError(ClientHttpResponse response) {
        try {
            String body = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8).strip();
            return body.isBlank() ? response.getStatusCode().toString() : response.getStatusCode() + "：" + body;
        } catch (IOException exception) {
            return "HTTP 响应读取失败";
        }
    }
}
