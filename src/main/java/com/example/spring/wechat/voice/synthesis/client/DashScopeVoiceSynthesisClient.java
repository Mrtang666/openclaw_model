package com.example.spring.wechat.voice.synthesis.client;

import com.example.spring.wechat.voice.synthesis.exception.VoiceSynthesisException;
import com.example.spring.wechat.voice.synthesis.model.VoiceSynthesisAudio;
import com.example.spring.wechat.voice.synthesis.model.VoiceSynthesisRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.Base64;
import java.util.Map;

/**
 * 百炼 DashScope TTS 客户端，通过 HTTP 调用语音合成接口并下载音频。
 */
@Component
public class DashScopeVoiceSynthesisClient implements VoiceSynthesisClient {

    private final RestClient restClient;
    private final RestClient downloadClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    @Autowired
    public DashScopeVoiceSynthesisClient(
            @Value("${dashscope.api-key:}") String apiKey,
            @Value("${dashscope.tts-base-url:https://dashscope.aliyuncs.com/api/v1}") String baseUrl,
            ObjectMapper objectMapper) {
        this(RestClient.builder(), RestClient.builder(), objectMapper, apiKey, baseUrl);
    }

    DashScopeVoiceSynthesisClient(
            RestClient.Builder restClientBuilder,
            RestClient.Builder downloadClientBuilder,
            ObjectMapper objectMapper,
            String apiKey,
            String baseUrl) {
        this.apiKey = apiKey == null ? "" : apiKey.strip();
        this.objectMapper = objectMapper;
        this.restClient = restClientBuilder.baseUrl(trimTrailingSlash(baseUrl)).build();
        this.downloadClient = downloadClientBuilder.build();
    }

    @Override
    public VoiceSynthesisAudio synthesize(VoiceSynthesisRequest request) {
        if (apiKey.isBlank()) {
            throw new VoiceSynthesisException("缺少 DASHSCOPE_API_KEY，无法调用语音合成服务");
        }

        try {
            Map<String, Object> body = Map.of(
                    "model", request.model(),
                    "input", Map.of(
                            "text", request.text(),
                            "voice", request.voice(),
                            "language_type", "Chinese"));
            String response = restClient.post()
                    .uri("/services/aigc/multimodal-generation/generation")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + apiKey)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            return parseResponse(response, request);
        } catch (VoiceSynthesisException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new VoiceSynthesisException("语音合成调用失败：" + rootMessage(exception), exception);
        }
    }

    private VoiceSynthesisAudio parseResponse(String response, VoiceSynthesisRequest request) {
        if (response == null || response.isBlank()) {
            throw new VoiceSynthesisException("语音合成接口返回为空");
        }

        try {
            JsonNode root = objectMapper.readTree(response);
            String base64Audio = textAt(root, "/output/audio/data");
            if (!base64Audio.isBlank()) {
                return new VoiceSynthesisAudio(
                        Base64.getDecoder().decode(base64Audio),
                        request.format(),
                        "audio/" + request.format(),
                        request.sampleRate());
            }

            String audioUrl = firstNonBlank(textAt(root, "/output/audio/url"), textAt(root, "/output/url"));
            if (!audioUrl.isBlank()) {
                byte[] audioBytes = downloadClient.get().uri(URI.create(audioUrl)).retrieve().body(byte[].class);
                return new VoiceSynthesisAudio(audioBytes, request.format(), "audio/" + request.format(), request.sampleRate());
            }

            String message = firstNonBlank(textAt(root, "/message"), textAt(root, "/output/message"));
            throw new VoiceSynthesisException(message.isBlank() ? "语音合成接口没有返回音频" : "语音合成失败：" + message);
        } catch (VoiceSynthesisException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new VoiceSynthesisException("语音合成结果解析失败：" + rootMessage(exception), exception);
        }
    }

    private String textAt(JsonNode root, String pointer) {
        JsonNode node = root == null ? null : root.at(pointer);
        return node == null || node.isMissingNode() || node.isNull() ? "" : node.asText("").strip();
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.strip();
            }
        }
        return "";
    }

    private String trimTrailingSlash(String value) {
        String text = value == null ? "" : value.strip();
        while (text.endsWith("/")) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }

    private String rootMessage(Throwable exception) {
        Throwable current = exception;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
