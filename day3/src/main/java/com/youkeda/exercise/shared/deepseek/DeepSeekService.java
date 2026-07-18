package com.youkeda.exercise.shared.deepseek;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * DeepSeek AI 服务 - 调用大模型API进行智能对话
 */
@Service
public class DeepSeekService {

    private static final String DEEPSEEK_API_URL = "https://api.deepseek.com/v1/chat/completions";

    @Value("${deepseek.api.key:}")
    private String apiKey;

    @Value("${deepseek.model:deepseek-chat}")
    private String model;

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public DeepSeekService() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 调用DeepSeek大模型进行对话
     *
     * @param userMessage 用户消息
     * @param systemPrompt 系统提示词（可自定义角色）
     * @return AI回复内容
     */
    public String chat(String userMessage, String systemPrompt) throws IOException {
        if (apiKey == null || apiKey.isEmpty()) {
            return "⚠️ DeepSeek API密钥未配置，请在 application.properties 中设置 deepseek.api.key";
        }

        // 构建请求体
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", model);
        requestBody.put("temperature", 0.7);
        requestBody.put("max_tokens", 2000);
        requestBody.put("stream", false);

        // 构建消息列表
        ArrayNode messages = objectMapper.createArrayNode();

        // 系统提示词
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            ObjectNode systemMsg = objectMapper.createObjectNode();
            systemMsg.put("role", "system");
            systemMsg.put("content", systemPrompt);
            messages.add(systemMsg);
        }

        // 用户消息
        ObjectNode userMsg = objectMapper.createObjectNode();
        userMsg.put("role", "user");
        userMsg.put("content", userMessage);
        messages.add(userMsg);

        requestBody.set("messages", messages);

        // 构建HTTP请求
        Request request = new Request.Builder()
                .url(DEEPSEEK_API_URL)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(
                        MediaType.parse("application/json"),
                        objectMapper.writeValueAsString(requestBody)
                ))
                .build();

        // 发送请求
        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";

            if (!response.isSuccessful()) {
                return "❌ API调用失败 (HTTP " + response.code() + "): " + responseBody;
            }

            // 解析响应
            JsonNode root = objectMapper.readTree(responseBody);

            // 检查是否有错误
            if (root.has("error")) {
                JsonNode error = root.get("error");
                String errorMsg = error.has("message") ? error.get("message").asText() : "未知错误";
                return "❌ DeepSeek API错误: " + errorMsg;
            }

            // 提取回复内容
            JsonNode choices = root.get("choices");
            if (choices != null && choices.isArray() && choices.size() > 0) {
                JsonNode firstChoice = choices.get(0);
                JsonNode message = firstChoice.get("message");
                if (message != null && message.has("content")) {
                    return message.get("content").asText();
                }
            }

            return "❌ 无法解析AI回复，请稍后重试";
        }
    }

    /**
     * 简单对话（使用默认系统提示）
     */
    public String chat(String userMessage) throws IOException {
        String systemPrompt = "你是一个友好的微信机器人助手，名叫「小天气」。\n" +
                "你的职责是帮助用户查询天气、解答问题、闲聊陪伴。\n" +
                "回答要简洁、亲切、有用，适合在微信对话中展示。\n" +
                "如果用户询问天气相关问题，建议他们使用「天气 城市名」命令获取专业天气数据。";
        return chat(userMessage, systemPrompt);
    }

    /**
     * 检查DeepSeek服务是否可用
     */
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isEmpty();
    }
}