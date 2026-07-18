package com.example.spring.wechat.voice.client;

import com.example.spring.wechat.voice.VoiceRecognitionException;
import com.example.spring.wechat.voice.model.VoiceRecognitionRequest;
import com.example.spring.wechat.voice.model.VoiceRecognitionResult;
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
public class DashScopeVoiceRecognitionClient implements VoiceRecognitionClient {

    private static final long MAX_AUDIO_BYTES = 10L * 1024L * 1024L;
    private static final String SYSTEM_PROMPT = """
            你是一个中文语音识别助手。
            请只输出用户语音中真实说出的文字，不要解释、不要补充、不要改写。
            需要尽量保留原话顺序、语气和关键信息，包括数字、英文、品牌名、地名和专有名词。
            如果语音里有明显口语停顿，可以整理成自然中文，但不要改变原意。
            如果听不清某个词，不要猜测，不要凭空补全。
            """;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;

    public DashScopeVoiceRecognitionClient(
            RestClient.Builder builder,
            ObjectMapper objectMapper,
            @Value("${dashscope.api-key:}") String apiKey,
            @Value("${dashscope.voice-base-url:${dashscope.base-url:https://ws-6gncy95g9skiwjfi.cn-beijing.maas.aliyuncs.com/compatible-mode/v1}}") String baseUrl,
            @Value("${dashscope.voice-model:qwen3-asr-flash}") String model,
            @Value("${dashscope.voice-max-poll-attempts:20}") int ignoredMaxPollAttempts,
            @Value("${dashscope.voice-poll-interval-ms:1000}") long ignoredPollIntervalMillis) {
        this.restClient = builder.baseUrl(stripTrailingSlash(baseUrl)).build();
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public VoiceRecognitionResult recognize(VoiceRecognitionRequest request) {
        validateConfiguration();
        validateRequest(request);

        try {
            JsonNode response = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .headers(headers -> headers.setBearerAuth(apiKey))
                    .body(requestBody(request))
                    .exchange((httpRequest, httpResponse) -> {
                        if (httpResponse.getStatusCode().isError()) {
                            throw new VoiceRecognitionException("百炼语音识别接口返回错误：" + responseError(httpResponse));
                        }
                        try {
                            return objectMapper.readTree(httpResponse.getBody());
                        } catch (IOException exception) {
                            throw new VoiceRecognitionException("语音识别响应读取失败", exception);
                        }
                    });

            return new VoiceRecognitionResult(
                    extractText(response),
                    languageOrDefault(request.language()),
                    null,
                    request.durationMs(),
                    "DASHSCOPE_QWEN_ASR");
        } catch (VoiceRecognitionException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new VoiceRecognitionException("语音识别服务暂时不可用", exception);
        }
    }

    private Map<String, Object> requestBody(VoiceRecognitionRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages(request));
        body.put("asr_options", Map.of("language", languageOrDefault(request.language())));
        body.put("stream", false);
        return body;
    }

    private List<Map<String, Object>> messages(VoiceRecognitionRequest request) {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));

        List<Map<String, Object>> userContent = new ArrayList<>();
        userContent.add(Map.of(
                "type", "input_audio",
                "input_audio", Map.of("data", audioData(request))));
        messages.add(Map.of("role", "user", "content", userContent));
        return messages;
    }

    private String audioData(VoiceRecognitionRequest request) {
        if (request.audioBytes() != null && request.audioBytes().length > 0) {
            return "data:"
                    + mimeTypeOrDefault(request.contentType(), request.format())
                    + ";base64,"
                    + Base64.getEncoder().encodeToString(request.audioBytes());
        }

        if (request.sourceUrl() != null && !request.sourceUrl().isBlank()) {
            return request.sourceUrl().strip();
        }

        throw new VoiceRecognitionException("语音缺少可识别的音频内容");
    }

    private String extractText(JsonNode response) {
        JsonNode choices = response.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            throw new VoiceRecognitionException("语音识别未返回结果");
        }

        JsonNode content = choices.get(0).path("message").path("content");
        String text = extractContentText(content);
        if (text.isBlank()) {
            throw new VoiceRecognitionException("语音识别未返回有效文本");
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
        if (apiKey == null || apiKey.isBlank()) {
            throw new VoiceRecognitionException("未配置 DASHSCOPE_API_KEY");
        }
    }

    private void validateRequest(VoiceRecognitionRequest request) {
        if (request == null) {
            throw new VoiceRecognitionException("语音识别请求不能为空");
        }

        boolean hasBytes = request.audioBytes() != null && request.audioBytes().length > 0;
        boolean hasUrl = request.sourceUrl() != null && !request.sourceUrl().isBlank();
        if (!hasBytes && !hasUrl) {
            throw new VoiceRecognitionException("语音缺少可识别的音频内容");
        }

        if (hasBytes && request.audioBytes().length > MAX_AUDIO_BYTES) {
            throw new VoiceRecognitionException("语音文件过大，请发送 10MB 以内的语音");
        }
    }

    private String languageOrDefault(String language) {
        return language == null || language.isBlank() ? "zh" : language.strip();
    }

    private String mimeTypeOrDefault(String contentType, String format) {
        if (contentType != null && !contentType.isBlank()) {
            return contentType.strip();
        }

        String normalizedFormat = format == null ? "" : format.strip().toLowerCase();
        return switch (normalizedFormat) {
            case "wav" -> "audio/wav";
            case "mp3" -> "audio/mpeg";
            case "m4a" -> "audio/mp4";
            case "flac" -> "audio/flac";
            case "amr" -> "audio/amr";
            case "ogg", "opus" -> "audio/ogg";
            case "silk" -> "audio/silk";
            default -> "application/octet-stream";
        };
    }

    private String responseError(ClientHttpResponse response) {
        try {
            String body = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8).strip();
            if (body.isBlank()) {
                return response.getStatusCode().toString();
            }
            return response.getStatusCode() + "，" + body;
        } catch (IOException exception) {
            return "HTTP 响应读取失败";
        }
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
