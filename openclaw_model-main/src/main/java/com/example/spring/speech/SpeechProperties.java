package com.example.spring.speech;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "speech")
public class SpeechProperties {
    private boolean enabled = true;
    private String apiKey = "";
    private String endpoint = "";
    private String compatibleBaseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";
    private String model = "qwen-audio-turbo";
    private Duration connectTimeout = Duration.ofSeconds(10);
    private Duration requestTimeout = Duration.ofSeconds(60);
    private int maxDurationSeconds = 60;
    private long maxBytes = 10L * 1024 * 1024;
    private String silkDecoderPath = "";
    private String silkEncoderPath = "";
    private boolean ttsEnabled = true;
    private String ttsApiKey = "";
    private String ttsEndpoint = "";
    private String ttsModel = "qwen3-tts-flash";
    private String ttsVoice = "Cherry";
    private String ttsLanguageType = "Chinese";
    private String ttsFormat = "wav";
    private int ttsSampleRate = 16000;
    private int ttsMaxTextLength = 2000;
    private Duration ttsTimeout = Duration.ofSeconds(60);
    private Duration voiceSendInterval = Duration.ofMillis(2500);
    private int voiceRetryMaxAttempts = 5;
    private Duration voiceRetryBaseDelay = Duration.ofMillis(1500);
    private Duration voiceRetryMaxDelay = Duration.ofSeconds(10);

    public boolean isConfigured() {
        return enabled && apiKey != null && !apiKey.isBlank();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getCompatibleBaseUrl() {
        return compatibleBaseUrl;
    }

    public void setCompatibleBaseUrl(String compatibleBaseUrl) {
        this.compatibleBaseUrl = compatibleBaseUrl;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(Duration requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    public int getMaxDurationSeconds() {
        return maxDurationSeconds;
    }

    public void setMaxDurationSeconds(int maxDurationSeconds) {
        this.maxDurationSeconds = maxDurationSeconds;
    }

    public long getMaxBytes() {
        return maxBytes;
    }

    public void setMaxBytes(long maxBytes) {
        this.maxBytes = maxBytes;
    }

    public String getSilkDecoderPath() {
        return silkDecoderPath;
    }

    public void setSilkDecoderPath(String silkDecoderPath) {
        this.silkDecoderPath = silkDecoderPath;
    }

    public String getSilkEncoderPath() {
        return silkEncoderPath;
    }

    public void setSilkEncoderPath(String silkEncoderPath) {
        this.silkEncoderPath = silkEncoderPath;
    }

    public boolean isTtsConfigured() {
        return ttsEnabled && ttsApiKey != null && !ttsApiKey.isBlank();
    }

    public boolean isTtsEnabled() {
        return ttsEnabled;
    }

    public void setTtsEnabled(boolean ttsEnabled) {
        this.ttsEnabled = ttsEnabled;
    }

    public String getTtsApiKey() {
        return ttsApiKey;
    }

    public void setTtsApiKey(String ttsApiKey) {
        this.ttsApiKey = ttsApiKey;
    }

    public String getTtsEndpoint() {
        return ttsEndpoint;
    }

    public void setTtsEndpoint(String ttsEndpoint) {
        this.ttsEndpoint = ttsEndpoint;
    }

    public String getTtsModel() {
        return ttsModel;
    }

    public void setTtsModel(String ttsModel) {
        this.ttsModel = ttsModel;
    }

    public String getTtsVoice() {
        return ttsVoice;
    }

    public void setTtsVoice(String ttsVoice) {
        this.ttsVoice = ttsVoice;
    }

    public String getTtsLanguageType() {
        return ttsLanguageType;
    }

    public void setTtsLanguageType(String ttsLanguageType) {
        this.ttsLanguageType = ttsLanguageType;
    }

    public String getTtsFormat() {
        return ttsFormat;
    }

    public void setTtsFormat(String ttsFormat) {
        this.ttsFormat = ttsFormat;
    }

    public int getTtsSampleRate() {
        return ttsSampleRate;
    }

    public void setTtsSampleRate(int ttsSampleRate) {
        this.ttsSampleRate = ttsSampleRate;
    }

    public int getTtsMaxTextLength() {
        return ttsMaxTextLength;
    }

    public void setTtsMaxTextLength(int ttsMaxTextLength) {
        this.ttsMaxTextLength = ttsMaxTextLength;
    }

    public Duration getTtsTimeout() {
        return ttsTimeout;
    }

    public void setTtsTimeout(Duration ttsTimeout) {
        this.ttsTimeout = ttsTimeout;
    }

    public Duration getVoiceSendInterval() {
        return voiceSendInterval;
    }

    public void setVoiceSendInterval(Duration voiceSendInterval) {
        this.voiceSendInterval = voiceSendInterval;
    }

    public int getVoiceRetryMaxAttempts() {
        return voiceRetryMaxAttempts;
    }

    public void setVoiceRetryMaxAttempts(int voiceRetryMaxAttempts) {
        this.voiceRetryMaxAttempts = voiceRetryMaxAttempts;
    }

    public Duration getVoiceRetryBaseDelay() {
        return voiceRetryBaseDelay;
    }

    public void setVoiceRetryBaseDelay(Duration voiceRetryBaseDelay) {
        this.voiceRetryBaseDelay = voiceRetryBaseDelay;
    }

    public Duration getVoiceRetryMaxDelay() {
        return voiceRetryMaxDelay;
    }

    public void setVoiceRetryMaxDelay(Duration voiceRetryMaxDelay) {
        this.voiceRetryMaxDelay = voiceRetryMaxDelay;
    }
}
