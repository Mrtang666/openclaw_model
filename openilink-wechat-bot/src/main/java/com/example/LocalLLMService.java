package com.example;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 大模型调用服务
 * <p>
 * 通过 OpenAI 兼容的 API 格式调用云端或本地大模型（如 SiliconFlow、Ollama、LocalAI 等），
 * 并维护每个用户的对话历史，实现多轮对话记忆。
 */
public class LocalLLMService {

    private static final Logger log = LoggerFactory.getLogger(LocalLLMService.class);

    private static final int MAX_HISTORY = 20;

    private final String apiUrl;
    private final String apiKey;
    private final String model;
    private final String visionModel;
    private final double temperature;
    private final int maxTokens;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    private final ConcurrentHashMap<String, List<Map<String, String>>> conversationHistory;

    public LocalLLMService() {
        this(
                getEnv("LLM_API_URL", "llm.api.url", "https://api.siliconflow.cn/v1/chat/completions"),
                getEnv("LLM_API_KEY", "llm.api.key", ""),
                getEnv("LLM_MODEL", "llm.model", "Qwen/Qwen2.5-7B-Instruct"),
                getEnv("LLM_VISION_MODEL", "llm.vision.model", "Qwen/Qwen2.5-VL-72B-Instruct"),
                Double.parseDouble(getEnv("LLM_TEMPERATURE", "llm.temperature", "0.7")),
                Integer.parseInt(getEnv("LLM_MAX_TOKENS", "llm.max.tokens", "2048"))
        );
    }

    public LocalLLMService(String apiUrl, String apiKey, String model, String visionModel, double temperature, int maxTokens) {
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.visionModel = visionModel;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.conversationHistory = new ConcurrentHashMap<>();
    }

    private static String getEnv(String envKey, String propKey, String defaultValue) {
        String value = System.getenv(envKey);
        if (value != null && !value.isEmpty()) return value;
        if (propKey != null) {
            String propVal = configProps.getProperty(propKey);
            if (propVal != null && !propVal.isEmpty()) return propVal;
        }
        return defaultValue;
    }

    private static final Properties configProps = new Properties();
    static {
        try (InputStream in = Files.newInputStream(Paths.get("config.properties"))) {
            configProps.load(in);
        } catch (Exception e) {
            // config.properties 不存在则使用默认值
        }
    }

    public static class Config {
        private final Properties props;
        Config(Properties props) { this.props = props; }
        public String getApiKey() { return props.getProperty("llm.api.key", ""); }
        public String getApiUrl() { return props.getProperty("llm.api.url", "https://api.siliconflow.cn/v1/chat/completions"); }
        public String getModel() {
            return props.getProperty("llm.model", "Qwen/Qwen2.5-7B-Instruct");
        }
        public String getTranscriptionUrl() {
            return props.getProperty("llm.transcription.url", "https://api.siliconflow.cn/v1/audio/transcriptions");
        }
        public String getSttModel() {
            return props.getProperty("llm.stt.model", "FunAudioLLM/SenseVoiceSmall");
        }
        public String getVisionModel() {
            return props.getProperty("llm.vision.model", "Qwen/Qwen3-VL-8B-Instruct");
        }
        public String getImageGenModel() {
            return props.getProperty("llm.imagegen.model", "Qwen/Qwen-Image");
        }
        public String getBaiduMapAk() {
            return props.getProperty("map.baidu.ak", "");
        }
        public String getQWeatherApiHost() {
            return props.getProperty("qweather.api.host", "devapi.qweather.com");
        }
        public String getQWeatherApiKey() {
            return props.getProperty("qweather.api.key", "");
        }
        public String getTtsApiKey() {
            return props.getProperty("tts.api.key", "");
        }
        public String getTtsUrl() {
            return props.getProperty("tts.api.url", "https://api.siliconflow.cn/v1/audio/speech");
        }
        public String getTtsModel() {
            return props.getProperty("tts.model", "FunAudioLLM/CosyVoice2-0.5B");
        }
        public String getTtsVoice() {
            return props.getProperty("tts.voice", "FunAudioLLM/CosyVoice2-0.5B:alex");
        }
        public String getTtsResponseFormat() {
            return props.getProperty("tts.response.format", "pcm");
        }
        public int getTtsSampleRate() {
            return Integer.parseInt(props.getProperty("tts.sample.rate", "16000"));
        }
        public double getTtsSpeed() {
            return Double.parseDouble(props.getProperty("tts.speed", "1.0"));
        }
        public double getTtsGain() {
            return Double.parseDouble(props.getProperty("tts.gain", "0.0"));
        }
    }

    private static final Config cachedConfig = new Config(configProps);
    public static Config getConfig() { return cachedConfig; }

    public String chat(String userId, String message) {
        return chatInternal(userId, message, temperature, maxTokens, false);
    }

    /** Uses conservative sampling for long-form document content. */
    public String chatForDocument(String userId, String message) {
        return chatInternal(userId, message, 0.25, Math.max(maxTokens, 3072), true);
    }

    private String chatInternal(String userId, String message,
                                 double requestTemperature, int requestMaxTokens,
                                 boolean isolated) {
        try {
            List<Map<String, String>> history;
            if (isolated) {
                history = new ArrayList<>();
                history.add(systemMessage());
            } else {
                history = conversationHistory.computeIfAbsent(userId, k -> {
                    List<Map<String, String>> list = new ArrayList<>();
                    list.add(systemMessage());
                    return list;
                });
            }

            history.add(Map.of("role", "user", "content", message));

            if (!isolated && history.size() > MAX_HISTORY) {
                List<Map<String, String>> trimmed = new ArrayList<>();
                trimmed.add(history.get(0));
                trimmed.addAll(history.subList(history.size() - MAX_HISTORY + 1, history.size()));
                conversationHistory.put(userId, trimmed);
                history = trimmed;
            }

            ChatRequest request = new ChatRequest(model, history, requestTemperature, requestMaxTokens);
            String requestBody = objectMapper.writeValueAsString(request);

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(60));
            if (apiKey != null && !apiKey.isEmpty()) {
                requestBuilder.header("Authorization", "Bearer " + apiKey);
            }
            HttpRequest httpRequest = requestBuilder
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("LLM API error: status={}, body={}", response.statusCode(), response.body());
                return "抱歉，我现在无法思考，请稍后再试。";
            }

            ChatResponse chatResponse = objectMapper.readValue(response.body(), ChatResponse.class);
            String reply = chatResponse.getReply();

            if (reply == null || reply.isBlank()) {
                return "抱歉，我没有理解你的意思。";
            }

            if (!isolated) {
                history.add(Map.of("role", "assistant", "content", reply));
            }
            return reply;

        } catch (Exception e) {
            log.error("调用本地大模型失败", e);
            return "抱歉，我连接不到大脑，请检查本地模型服务是否启动。";
        }
    }

    private Map<String, String> systemMessage() {
        return Map.of("role", "system", "content",
                "你是一个友好的微信聊天机器人助手。"
                        + "当前消息已经由程序路由层判断为普通聊天，请只用自然中文回答用户问题。"
                        + "如果完成用户请求所必需的信息不足，请先主动提出简洁的澄清问题，不要自行猜测；信息足够时直接回答或执行。"
                        + "不要输出 IMAGE_GEN 标记，不要主动触发图片生成、路线图生成、天气查询或语音模式切换。");
    }

    public String chatWithImage(String userId, String textPrompt, byte[] imageBytes, String imageMimeType) {
        try {
            String base64 = java.util.Base64.getEncoder().encodeToString(imageBytes);
            String dataUrl = "data:" + imageMimeType + ";base64," + base64;

            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", visionModel);
            body.put("temperature", temperature);
            body.put("max_tokens", maxTokens);
            body.put("stream", false);

            ArrayNode messages = body.putArray("messages");

            ObjectNode systemMsg = messages.addObject();
            systemMsg.put("role", "system");
            systemMsg.put("content", "你是一个能看懂图片的AI助手。请用中文详细描述用户发送的图片内容，包括主体、颜色、动作、场景等。");

            ObjectNode userMsg = messages.addObject();
            userMsg.put("role", "user");
            ArrayNode content = userMsg.putArray("content");

            ObjectNode textPart = content.addObject();
            textPart.put("type", "text");
            textPart.put("text", textPrompt != null && !textPrompt.isEmpty() ? textPrompt : "请描述这张图片的内容");

            ObjectNode imagePart = content.addObject();
            imagePart.put("type", "image_url");
            ObjectNode imageUrl = imagePart.putObject("image_url");
            imageUrl.put("url", dataUrl);

            String requestBody = objectMapper.writeValueAsString(body);

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(120));
            if (apiKey != null && !apiKey.isEmpty()) {
                requestBuilder.header("Authorization", "Bearer " + apiKey);
            }
            HttpRequest httpRequest = requestBuilder
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("VLM API error: status={}, body={}", response.statusCode(), response.body());
                return "抱歉，我看不懂这张图片。";
            }

            ChatResponse chatResponse = objectMapper.readValue(response.body(), ChatResponse.class);
            String reply = chatResponse.getReply();
            return reply != null && !reply.isBlank() ? reply : "抱歉，我看不懂这张图片。";

        } catch (Exception e) {
            log.error("图片识别失败", e);
            return "抱歉，识别图片时出错了。";
        }
    }

    public void clearHistory(String userId) {
        conversationHistory.remove(userId);
    }

    // ======================== 请求/响应 DTO ========================

    static class ChatRequest {
        private String model;
        private List<Map<String, String>> messages;
        private double temperature;
        private int maxTokens;
        private boolean stream = false;

        ChatRequest() {}

        ChatRequest(String model, List<Map<String, String>> messages, double temperature, int maxTokens) {
            this.model = model;
            this.messages = messages;
            this.temperature = temperature;
            this.maxTokens = maxTokens;
        }

        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public List<Map<String, String>> getMessages() { return messages; }
        public void setMessages(List<Map<String, String>> messages) { this.messages = messages; }
        public double getTemperature() { return temperature; }
        public void setTemperature(double temperature) { this.temperature = temperature; }
        @JsonProperty("max_tokens")
        public int getMaxTokens() { return maxTokens; }
        @JsonProperty("max_tokens")
        public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
        public boolean isStream() { return stream; }
        public void setStream(boolean stream) { this.stream = stream; }
    }

    static class ChatResponse {
        private List<Choice> choices;

        public List<Choice> getChoices() { return choices; }
        public void setChoices(List<Choice> choices) { this.choices = choices; }

        String getReply() {
            if (choices != null && !choices.isEmpty()) {
                Choice first = choices.get(0);
                if (first.message != null && first.message.content != null) {
                    return first.message.content;
                }
                if (first.text != null) {
                    return first.text;
                }
            }
            return null;
        }

        static class Choice {
            private Message message;
            private String text;

            public Message getMessage() { return message; }
            public void setMessage(Message message) { this.message = message; }
            public String getText() { return text; }
            public void setText(String text) { this.text = text; }
        }

        static class Message {
            private String role;
            private String content;

            public String getRole() { return role; }
            public void setRole(String role) { this.role = role; }
            public String getContent() { return content; }
            public void setContent(String content) { this.content = content; }
        }
    }
}
