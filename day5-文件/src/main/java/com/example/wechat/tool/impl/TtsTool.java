package com.example.wechat.tool.impl;

import com.example.wechat.config.DashScopeProperties;
import com.example.wechat.memory.ConversationMemory;
import com.example.wechat.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class TtsTool implements Tool {

    private final DashScopeProperties dashScopeProperties;
    private final OkHttpClient httpClient;
    private final ConversationMemory memory;

    public TtsTool(DashScopeProperties dashScopeProperties,
                   OkHttpClient httpClient,
                   ConversationMemory memory) {
        this.dashScopeProperties = dashScopeProperties;
        this.httpClient = httpClient;
        this.memory = memory;
    }

    @Override
    public String getName() {
        return "text_to_speech";
    }

    @Override
    public String getDescription() {
        return "将文本转换为语音MP3/PCM文件（文字转语音）";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new HashMap<>();
        Map<String, Object> textProp = new HashMap<>();
        textProp.put("type", "string");
        textProp.put("description", "要转换为语音的文本内容");
        properties.put("text", textProp);

        Map<String, Object> voiceProp = new HashMap<>();
        voiceProp.put("type", "string");
        voiceProp.put("description", "音色名称（可选）");
        properties.put("voice", voiceProp);

        Map<String, Object> userIdProp = new HashMap<>();
        userIdProp.put("type", "string");
        userIdProp.put("description", "用户ID（用于获取用户音色偏好）");
        properties.put("userId", userIdProp);

        schema.put("properties", properties);
        schema.put("required", new String[]{"text"});
        return schema;
    }

    @Override
    public String execute(Map<String, Object> params) {
        String text = (String) params.get("text");
        String voice = (String) params.get("voice");
        String userId = (String) params.get("userId");

        log.info("========== TTS工具被调用 ==========");
        log.info("待合成文本: {}", text);
        log.info("传入的voice参数: {}", voice);
        log.info("传入的userId参数: {}", userId);

        if (text == null || text.trim().isEmpty()) {
            return "请提供要转换为语音的文本内容";
        }

        // ============================================================
        // 核心修复：从 memory 读取音色（优先使用 userId）
        // ============================================================
        String finalVoice = null;

        // 1. 如果有 userId，从 memory 读取用户保存的音色
        if (userId != null) {
            String savedVoice = memory.getUserVoice(userId);
            log.info("🔍 从 memory 读取到音色: userId={}, savedVoice={}", userId, savedVoice);
            if (savedVoice != null && memory.isValidVoice(savedVoice)) {
                finalVoice = savedVoice;
                log.info("✅ 使用 memory 中保存的音色: {}", finalVoice);
            } else {
                log.warn("⚠️ memory 中音色无效或不存在，尝试使用传入的voice参数");
            }
        }

        // 2. 如果 memory 中没有，使用传入的 voice 参数
        if (finalVoice == null && voice != null && !voice.isEmpty() && memory.isValidVoice(voice)) {
            finalVoice = voice;
            log.info("✅ 使用传入的音色参数: {}", finalVoice);
        }

        // 3. 最后保底：使用默认音色
        if (finalVoice == null || !memory.isValidVoice(finalVoice)) {
            finalVoice = "Cherry";
            log.warn("⚠️ 使用默认音色 Cherry（因为没有有效音色）");
        }

        log.info("🎤 最终使用音色: {}", finalVoice);
        log.info("🎤 音色显示名称: {}", memory.getVoiceDisplayName(finalVoice));

        // =============================================
        // 1. 先尝试 MP3 格式
        // =============================================
        try {
            log.info("🔄 尝试使用 MP3 格式合成语音，音色: {}", finalVoice);
            byte[] audioBytes = synthesizeSpeechViaHttp(text, finalVoice, "mp3");
            log.info("✅ MP3合成成功，音频大小: {} bytes", audioBytes.length);
            return "AUDIO:MP3:" + Base64.getEncoder().encodeToString(audioBytes);
        } catch (Exception e) {
            log.warn("⚠️ MP3合成失败: {}", e.getMessage());
            log.info("🔄 自动降级为 PCM 格式...");

            try {
                byte[] audioBytes = synthesizeSpeechViaHttp(text, finalVoice, "pcm");
                log.info("✅ PCM合成成功，音频大小: {} bytes", audioBytes.length);
                return "AUDIO:PCM:" + Base64.getEncoder().encodeToString(audioBytes);
            } catch (Exception e2) {
                log.error("❌ PCM合成也失败: {}", e2.getMessage());
                return "❌ 语音合成失败（MP3和PCM均失败）: " + e2.getMessage();
            }
        }
    }

    /**
     * 使用 HTTP 直调方式调用 Qwen-TTS
     */
    private byte[] synthesizeSpeechViaHttp(String text, String voice, String format) throws Exception {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", "qwen3-tts-flash");

        Map<String, Object> input = new HashMap<>();
        input.put("text", text);
        requestBody.put("input", input);

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("voice", voice);
        parameters.put("format", format);
        parameters.put("language_type", "Chinese");
        requestBody.put("parameters", parameters);

        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        String jsonBody = mapper.writeValueAsString(requestBody);
        log.info("TTS请求体 (格式: {}): {}", format, jsonBody);

        okhttp3.Request request = new okhttp3.Request.Builder()
                .url("https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation")
                .addHeader("Authorization", "Bearer " + dashScopeProperties.getApiKey())
                .addHeader("Content-Type", "application/json")
                .post(okhttp3.RequestBody.create(jsonBody, okhttp3.MediaType.parse("application/json")))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body().string();
            log.info("TTS响应状态码: {}", response.code());

            if (!response.isSuccessful()) {
                throw new Exception("TTS API调用失败: " + response.code() + ", " + responseBody);
            }

            Map<String, Object> result = mapper.readValue(responseBody, Map.class);

            if (result.containsKey("code")) {
                String code = (String) result.get("code");
                String message = (String) result.get("message");
                throw new Exception("TTS错误: " + code + " - " + message);
            }

            return extractAudioData(result);
        }
    }

    /**
     * 从响应中提取音频数据
     */
    @SuppressWarnings("unchecked")
    private byte[] extractAudioData(Map<String, Object> result) throws Exception {
        Map<String, Object> output = (Map<String, Object>) result.get("output");
        if (output == null) {
            throw new Exception("响应中没有output字段");
        }

        if (output.containsKey("audio")) {
            Object audioObj = output.get("audio");
            if (audioObj instanceof Map) {
                Map<String, Object> audioMap = (Map<String, Object>) audioObj;
                if (audioMap.containsKey("url")) {
                    String audioUrl = (String) audioMap.get("url");
                    return downloadAudio(audioUrl);
                }
                if (audioMap.containsKey("data")) {
                    String audioData = (String) audioMap.get("data");
                    return Base64.getDecoder().decode(audioData);
                }
            }
        }

        throw new Exception("无法从响应中提取音频数据");
    }

    /**
     * 下载音频文件
     */
    private byte[] downloadAudio(String audioUrl) throws Exception {
        log.info("下载音频: {}", audioUrl);

        okhttp3.Request request = new okhttp3.Request.Builder()
                .url(audioUrl)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                return response.body().bytes();
            } else {
                throw new Exception("下载音频失败，状态码: " + response.code());
            }
        }
    }
}