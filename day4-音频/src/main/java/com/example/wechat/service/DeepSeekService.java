package com.example.wechat.service;

import com.example.wechat.memory.ConversationMemory;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DeepSeekService {

    @Value("${deepseek.api-key}")
    private String apiKey;

    private final OkHttpClient httpClient;
    private final ConversationMemory memory;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String DEEPSEEK_API_URL = "https://api.deepseek.com/v1/chat/completions";

    public DeepSeekService(OkHttpClient httpClient, ConversationMemory memory) {
        this.httpClient = httpClient;
        this.memory = memory;
    }

    public String chat(String userId, String userMessage) {
        try {
            // 获取完整的对话历史（包含系统提示）
            List<Map<String, String>> messages = memory.getMessagesForModel(userId);

            // 调试：打印消息数量
            System.out.println("对话历史长度: " + messages.size() + " 条消息");

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", "deepseek-chat");
            requestBody.put("messages", messages);
            requestBody.put("stream", false);
            requestBody.put("temperature", 0.7);
            requestBody.put("max_tokens", 2000);

            String json = objectMapper.writeValueAsString(requestBody);
            System.out.println("请求DeepSeek: " + json);

            Request request = new Request.Builder()
                    .url(DEEPSEEK_API_URL)
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(json, MediaType.parse("application/json")))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    return "调用DeepSeek失败: " + response.code() + "\n" + response.body().string();
                }

                String responseBody = response.body().string();
                System.out.println("DeepSeek响应: " + responseBody);

                Map<String, Object> result = objectMapper.readValue(responseBody, Map.class);

                List<Map<String, Object>> choices = (List<Map<String, Object>>) result.get("choices");
                if (choices != null && !choices.isEmpty()) {
                    Map<String, Object> choice = choices.get(0);
                    Map<String, Object> message = (Map<String, Object>) choice.get("message");
                    return (String) message.get("content");
                }

                return "未能获取有效回复";
            }

        } catch (IOException e) {
            e.printStackTrace();
            return "DeepSeek服务异常: " + e.getMessage();
        }
    }
}