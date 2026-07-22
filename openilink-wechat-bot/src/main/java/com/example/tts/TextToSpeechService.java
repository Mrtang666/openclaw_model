package com.example.tts;

import com.example.LocalLLMService;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;

public class TextToSpeechService {

    private static final Logger log = LoggerFactory.getLogger(TextToSpeechService.class);

    private final String apiKey;
    private final String ttsUrl;
    private final String model;
    private final String voice;
    private final String responseFormat;
    private final int sampleRate;
    private final double speed;
    private final double gain;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public TextToSpeechService() {
        LocalLLMService.Config cfg = LocalLLMService.getConfig();
        this.apiKey = firstNonBlank(
                System.getenv("TTS_API_KEY"),
                cfg.getTtsApiKey(),
                cfg.getApiKey()
        );
        this.ttsUrl = envOrDefault("TTS_API_URL", cfg.getTtsUrl());
        this.model = envOrDefault("TTS_MODEL", cfg.getTtsModel());
        this.voice = envOrDefault("TTS_VOICE", cfg.getTtsVoice());
        this.responseFormat = normalizeFormat(envOrDefault("TTS_RESPONSE_FORMAT", cfg.getTtsResponseFormat()));
        this.sampleRate = normalizeSampleRate(
                this.responseFormat,
                Integer.parseInt(envOrDefault("TTS_SAMPLE_RATE", String.valueOf(cfg.getTtsSampleRate())))
        );
        this.speed = Double.parseDouble(envOrDefault("TTS_SPEED", String.valueOf(cfg.getTtsSpeed())));
        this.gain = Double.parseDouble(envOrDefault("TTS_GAIN", String.valueOf(cfg.getTtsGain())));
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public TtsResult synthesize(String text) {
        return synthesize(text, responseFormat);
    }

    public TtsResult synthesize(String text, String requestedFormat) {
        return synthesize(text, requestedFormat, voice);
    }

    public TtsResult synthesize(String text, String requestedFormat, String requestedVoice) {
        if (text == null || text.isBlank()) {
            return TtsResult.failure("文本为空");
        }
        if (ttsUrl == null || ttsUrl.isBlank()) {
            return TtsResult.failure("TTS URL 未配置");
        }
        if (apiKey == null || apiKey.isBlank()) {
            return TtsResult.failure("TTS API Key 未配置");
        }

        String normalizedFormat = normalizeFormat(requestedFormat);
        int requestedSampleRate = normalizeSampleRate(normalizedFormat, sampleRate);
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", model);
            body.put("input", text);
            body.put("voice", firstNonBlank(requestedVoice, voice));
            body.put("speed", speed);
            body.put("gain", gain);
            body.put("response_format", normalizedFormat);
            body.put("sample_rate", requestedSampleRate);
            body.put("stream", false);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ttsUrl))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .timeout(Duration.ofSeconds(120))
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            String contentType = response.headers().firstValue("Content-Type").orElse("");
            if (response.statusCode() != 200) {
                String err = new String(response.body() == null ? new byte[0] : response.body(), StandardCharsets.UTF_8);
                log.warn("TTS API 失败: status={}, body={}", response.statusCode(), abbreviate(err, 300));
                return TtsResult.failure("TTS API 错误: " + response.statusCode());
            }

            byte[] audioBytes = response.body();
            if (audioBytes == null || audioBytes.length == 0) {
                return TtsResult.failure("TTS 未返回音频");
            }

            if (looksLikeJson(audioBytes, contentType)) {
                String err = new String(audioBytes, StandardCharsets.UTF_8);
                log.warn("TTS API 返回的不是音频: contentType={}, body={}", contentType, abbreviate(err, 300));
                return TtsResult.failure("TTS API 未返回音频");
            }

            Integer playTime = estimatePlayTimeSeconds(audioBytes, normalizedFormat, requestedSampleRate);
            log.info("TTS 合成成功: model={}, format={}, sampleRate={}, bytes={}, playTime={}",
                    model, normalizedFormat, requestedSampleRate, audioBytes.length, playTime);
            return TtsResult.success(audioBytes, normalizedFormat, requestedSampleRate, playTime);
        } catch (Exception e) {
            log.error("TTS 合成失败", e);
            return TtsResult.failure(e.getMessage());
        }
    }

    private Integer estimatePlayTimeSeconds(byte[] audioBytes, String format, int sampleRate) {
        if ("pcm".equalsIgnoreCase(format)) {
            return estimatePcmPlayTimeSeconds(audioBytes, sampleRate);
        }
        if (!"wav".equalsIgnoreCase(format)) return null;
        try (AudioInputStream ais = AudioSystem.getAudioInputStream(new ByteArrayInputStream(audioBytes))) {
            AudioFormat audioFormat = ais.getFormat();
            float frameRate = audioFormat.getFrameRate();
            long frameLength = ais.getFrameLength();
            if (frameRate > 0 && frameLength > 0) {
                return Math.max(1, (int) Math.round(frameLength / frameRate));
            }
        } catch (Exception e) {
            log.debug("无法解析音频时长: {}", e.getMessage());
        }
        return null;
    }

    private boolean looksLikeJson(byte[] bytes, String contentType) {
        if (contentType != null && contentType.toLowerCase(Locale.ROOT).contains("json")) {
            return true;
        }
        int i = 0;
        while (i < bytes.length && Character.isWhitespace((char) bytes[i])) {
            i++;
        }
        return i < bytes.length && (bytes[i] == '{' || bytes[i] == '[');
    }

    private Integer estimatePcmPlayTimeSeconds(byte[] audioBytes, int sampleRate) {
        if (audioBytes == null || audioBytes.length == 0 || sampleRate <= 0) {
            return null;
        }
        double seconds = audioBytes.length / (sampleRate * 2.0);
        return Math.max(1, (int) Math.ceil(seconds));
    }

    private int normalizeSampleRate(String format, int requestedSampleRate) {
        if ("mp3".equalsIgnoreCase(format)) {
            return requestedSampleRate == 44100 ? 44100 : 32000;
        }
        if (requestedSampleRate == 8000 || requestedSampleRate == 12000
                || requestedSampleRate == 16000 || requestedSampleRate == 24000) {
            return requestedSampleRate;
        }
        return 24000;
    }

    private String normalizeFormat(String format) {
        String value = format == null ? "pcm" : format.trim().toLowerCase(Locale.ROOT);
        switch (value) {
            case "mp3":
            case "wav":
            case "pcm":
                return value;
            default:
                return "pcm";
        }
    }

    private String envOrDefault(String envKey, String defaultValue) {
        String value = System.getenv(envKey);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String abbreviate(String text, int max) {
        if (text == null || text.length() <= max) {
            return text;
        }
        return text.substring(0, max) + "...";
    }

    public static class TtsResult {
        private final boolean success;
        private final String message;
        private final byte[] audioBytes;
        private final String responseFormat;
        private final int sampleRate;
        private final Integer playTimeSeconds;

        private TtsResult(boolean success, String message, byte[] audioBytes, String responseFormat, int sampleRate, Integer playTimeSeconds) {
            this.success = success;
            this.message = message;
            this.audioBytes = audioBytes;
            this.responseFormat = responseFormat;
            this.sampleRate = sampleRate;
            this.playTimeSeconds = playTimeSeconds;
        }

        public static TtsResult success(byte[] audioBytes, String responseFormat, int sampleRate, Integer playTimeSeconds) {
            return new TtsResult(true, "ok", audioBytes, responseFormat, sampleRate, playTimeSeconds);
        }

        public static TtsResult failure(String message) {
            return new TtsResult(false, message, null, null, 0, null);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public byte[] getAudioBytes() {
            return audioBytes;
        }

        public String getResponseFormat() {
            return responseFormat;
        }

        public int getSampleRate() {
            return sampleRate;
        }

        public Integer getPlayTimeSeconds() {
            return playTimeSeconds;
        }
    }
}
