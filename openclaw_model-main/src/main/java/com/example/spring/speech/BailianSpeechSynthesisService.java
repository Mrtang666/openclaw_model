package com.example.spring.speech;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class BailianSpeechSynthesisService implements SpeechSynthesisService {
    private final SpeechProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public BailianSpeechSynthesisService(SpeechProperties properties) {
        this(properties, new ObjectMapper(), HttpClient.newBuilder()
            .connectTimeout(properties.getConnectTimeout()).build());
    }

    BailianSpeechSynthesisService(
        SpeechProperties properties,
        ObjectMapper objectMapper,
        HttpClient httpClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    @Override
    public SpeechSynthesisResult synthesize(String text)
        throws SpeechRecognitionException, InterruptedException {
        return synthesize(
            text, properties.getTtsFormat(), VoiceSynthesisOptions.defaults(properties));
    }

    @Override
    public SpeechSynthesisResult synthesize(String text, String requestedFormat)
        throws SpeechRecognitionException, InterruptedException {
        return synthesize(
            text, requestedFormat, VoiceSynthesisOptions.defaults(properties));
    }

    @Override
    public SpeechSynthesisResult synthesize(
        String text,
        String requestedFormat,
        VoiceSynthesisOptions options)
        throws SpeechRecognitionException, InterruptedException {
        String input = text == null ? "" : text.trim();
        if (input.isBlank()) {
            throw new SpeechRecognitionException("璇煶鍚堟垚鏂囨湰涓虹┖");
        }
        if (input.length() > properties.getTtsMaxTextLength()) {
            input = input.substring(0, properties.getTtsMaxTextLength());
        }
        if (!properties.isTtsConfigured()) {
            throw new SpeechRecognitionException("璇煶鍥炲鏈厤缃ソ TTS_API_KEY");
        }

        VoiceSynthesisOptions selected = options == null
            ? VoiceSynthesisOptions.defaults(properties) : options;
        String voiceId = selected.voiceId() == null || selected.voiceId().isBlank()
            ? properties.getTtsVoice() : selected.voiceId().trim();
        String languageType = selected.languageType() == null
            || selected.languageType().isBlank()
            ? properties.getTtsLanguageType() : selected.languageType().trim();
        Map<String, Object> inputNode = new LinkedHashMap<>();
        inputNode.put("text", input);
        inputNode.put("voice", voiceId);
        inputNode.put("language_type", languageType);
        String format = requestedFormat == null || requestedFormat.isBlank()
            ? properties.getTtsFormat() : requestedFormat.trim().toLowerCase();
        inputNode.put("format", format);
        inputNode.put("sample_rate", properties.getTtsSampleRate());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getTtsModel());
        body.put("input", inputNode);

        final String requestBody;
        try {
            requestBody = objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException exception) {
            throw new SpeechRecognitionException("璇煶鍥炲璇锋眰缂栫爜澶辫触", exception);
        }
        HttpRequest request = HttpRequest.newBuilder(endpoint())
            .timeout(properties.getTtsTimeout())
            .header("Authorization", "Bearer " + properties.getTtsApiKey().trim())
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
            .build();
        try {
            HttpResponse<String> response = httpClient.send(
                request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String detail = response.body() == null ? "" : response.body().trim();
                if (detail.length() > 500) {
                    detail = detail.substring(0, 500);
                }
                throw new SpeechRecognitionException(
                    "百炼语音合成接口返回 HTTP " + response.statusCode()
                        + (detail.isBlank() ? "" : "：" + detail));
            }
            JsonNode root = objectMapper.readTree(response.body());
            byte[] audio = extractAudio(root);
            if (audio.length == 0) {
                throw new SpeechRecognitionException("百炼语音合成结果中没有音频数据");
            }
            long duration = estimateDuration(audio, format);
            return new SpeechSynthesisResult(
                audio, format, properties.getTtsSampleRate(), 16, 1, duration);
        } catch (IOException exception) {
            throw new SpeechRecognitionException("调用百炼语音合成失败", exception);
        }
    }

    private byte[] extractAudio(JsonNode root)
        throws IOException, InterruptedException, SpeechRecognitionException {
        JsonNode audio = root.path("output").path("audio");
        String base64 = firstText(audio, "data", "base64");
        if (base64 != null && !base64.isBlank()) {
            return Base64.getDecoder().decode(stripDataUrl(base64));
        }
        String url = firstText(audio, "url");
        if (url == null || url.isBlank()) {
            url = root.path("output").path("audio_url").asText("");
        }
        if (url.isBlank()) {
            throw new SpeechRecognitionException("百炼语音合成响应缺少 audio.data 或 audio.url");
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
        HttpResponse<byte[]> response = httpClient.send(
            request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new SpeechRecognitionException("下载百炼语音结果失败 HTTP " + response.statusCode());
        }
        return response.body();
    }

    private static String firstText(JsonNode node, String... names) {
        for (String name : names) {
            String value = node.path(name).asText("");
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String stripDataUrl(String value) {
        int comma = value.indexOf(',');
        return comma >= 0 ? value.substring(comma + 1) : value;
    }

    private long estimateDuration(byte[] audio, String format) {
        if ("wav".equals(format) && audio.length > 44) {
            long bytesPerSecond = (long) properties.getTtsSampleRate() * 2;
            return bytesPerSecond == 0 ? 0 : Math.max(1, (audio.length - 44) * 1000 / bytesPerSecond);
        }
        return 0;
    }

    private URI endpoint() {
        String configured = properties.getTtsEndpoint();
        if (configured != null && !configured.isBlank()) {
            return URI.create(configured.trim());
        }
        String base = properties.getCompatibleBaseUrl();
        if (base != null && !base.isBlank()) {
            String normalized = base.trim();
            int marker = normalized.indexOf("/compatible-mode/v1");
            if (marker >= 0) {
                normalized = normalized.substring(0, marker);
            } else if (normalized.endsWith("/")) {
                normalized = normalized.substring(0, normalized.length() - 1);
            }
            return URI.create(normalized
                + "/api/v1/services/aigc/multimodal-generation/generation");
        }
        return URI.create("https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation");
    }
}
