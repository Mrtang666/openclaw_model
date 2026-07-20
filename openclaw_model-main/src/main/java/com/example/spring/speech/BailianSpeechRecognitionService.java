package com.example.spring.speech;

import com.example.spring.bailian.BailianResponseParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class BailianSpeechRecognitionService implements SpeechRecognitionService {
    private final SpeechProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public BailianSpeechRecognitionService(SpeechProperties properties) {
        this(properties, new ObjectMapper(), HttpClient.newBuilder()
            .connectTimeout(properties.getConnectTimeout()).build());
    }

    BailianSpeechRecognitionService(
        SpeechProperties properties,
        ObjectMapper objectMapper,
        HttpClient httpClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    @Override
    public SpeechRecognitionResult recognize(VoiceAsset source)
        throws SpeechRecognitionException, InterruptedException {
        validate(source);
        if (!properties.isConfigured()) {
            throw new SpeechRecognitionException("百炼语音识别未配置 SPEECH_API_KEY 或 BAILIAN_API_KEY");
        }
        VoiceAsset voice = source;
        if ("silk".equals(voice.format())) {
            voice = SilkAudioDecoder.decode(voice, properties.getSilkDecoderPath());
        }
        if ("unknown".equals(voice.format())) {
            throw new SpeechRecognitionException("无法识别微信语音格式，暂不执行语音识别");
        }

        String dataUrl = "data:audio/" + voice.format() + ";base64,"
            + Base64.getEncoder().encodeToString(voice.data());
        List<Map<String, Object>> content = List.of(
            Map.of(
                "type", "input_audio",
                "input_audio", Map.of("data", dataUrl, "format", voice.format())),
            Map.of(
                "type", "text",
                "text", "请准确转写这段语音，只返回转写文字，不要添加解释。"));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getModel());
        body.put("messages", List.of(Map.of("role", "user", "content", content)));
        body.put("stream", false);

        final String requestBody;
        try {
            requestBody = objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException exception) {
            throw new SpeechRecognitionException("璇煶璇锋眰缂栫爜澶辫触", exception);
        }
        HttpRequest request = HttpRequest.newBuilder(endpoint())
            .timeout(properties.getRequestTimeout())
            .header("Authorization", "Bearer " + properties.getApiKey().trim())
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
            .build();
        try {
            HttpResponse<String> response = httpClient.send(
                request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new SpeechRecognitionException(
                    "百炼语音识别接口返回 HTTP " + response.statusCode());
            }
            JsonNode root = objectMapper.readTree(response.body());
            String text = BailianResponseParser.assistantText(root);
            if (text.isBlank()) {
                throw new SpeechRecognitionException("百炼语音识别结果为空");
            }
            return new SpeechRecognitionResult(text, properties.getModel(), voice.durationMs());
        } catch (IOException exception) {
            throw new SpeechRecognitionException("调用百炼语音识别失败", exception);
        }
    }

    private void validate(VoiceAsset voice) throws SpeechRecognitionException {
        if (voice == null || voice.data().length == 0) {
            throw new SpeechRecognitionException("语音文件为空，请重新发送");
        }
        if (voice.data().length > properties.getMaxBytes()) {
            throw new SpeechRecognitionException("语音文件超过大小限制，请分段发送");
        }
        if (voice.durationMs() != null
            && voice.durationMs() > properties.getMaxDurationSeconds() * 1000L) {
            throw new SpeechRecognitionException(
                "语音超过 " + properties.getMaxDurationSeconds() + " 秒，请分段发送");
        }
    }

    private URI endpoint() {
        String configured = properties.getEndpoint();
        if (configured != null && !configured.isBlank()) {
            return URI.create(configured.trim());
        }
        String base = properties.getCompatibleBaseUrl();
        if (base == null || base.isBlank()) {
            throw new IllegalStateException("speech.compatible-base-url 不能为空");
        }
        return URI.create((base.endsWith("/") ? base : base + "/") + "chat/completions");
    }
}
