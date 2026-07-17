package com.example.spring.bailian;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.spring.memory.MemoryMessage;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class BailianChatService {
    private final BailianProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public BailianChatService(BailianProperties properties) {
        this(properties, new ObjectMapper(), HttpClient.newBuilder()
            .connectTimeout(properties.getConnectTimeout()).build());
    }

    BailianChatService(
        BailianProperties properties,
        ObjectMapper objectMapper,
        HttpClient httpClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    public String chat(String userId, String userText) throws IOException, InterruptedException {
        return chat(userId, userText, List.of());
    }

    public String chat(
        String userId,
        String userText,
        List<MemoryMessage> history) throws IOException, InterruptedException {
        requireConfigured();
        if (userText == null || userText.isBlank()) {
            throw new IllegalArgumentException("用户消息不能为空");
        }

        return callApi(history == null ? List.of() : history, userText.trim());
    }

    private String callApi(List<MemoryMessage> history, String userText)
        throws IOException, InterruptedException {
        List<Map<String, String>> messages = new ArrayList<>();
        if (properties.getSystemPrompt() != null && !properties.getSystemPrompt().isBlank()) {
            messages.add(message("system", properties.getSystemPrompt()));
        }
        for (MemoryMessage item : history) {
            if (item != null && item.content() != null && !item.content().isBlank()) {
                appendMessage(messages, item.role(), item.content());
            }
        }
        appendMessage(messages, "user", userText);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getChatModel());
        body.put("messages", messages);
        body.put("stream", false);

        HttpResponse<String> response = httpClient.send(
            authorizedPost(chatCompletionsUri(), objectMapper.writeValueAsString(body)),
            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        ensureSuccess(response);
        return BailianResponseParser.assistantText(objectMapper.readTree(response.body()));
    }

    private HttpRequest authorizedPost(URI uri, String json) {
        return HttpRequest.newBuilder(uri)
            .timeout(properties.getRequestTimeout())
            .header("Authorization", "Bearer " + properties.getApiKey().trim())
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
            .build();
    }

    private URI chatCompletionsUri() {
        String base = properties.getCompatibleBaseUrl();
        if (base == null || base.isBlank()) {
            throw new IllegalStateException("bailian.compatible-base-url 不能为空");
        }
        return URI.create((base.endsWith("/") ? base : base + "/") + "chat/completions");
    }

    private void requireConfigured() {
        if (!properties.isConfigured()) {
            throw new IllegalStateException("BAILIAN_API_KEY 未配置");
        }
    }

    private static void ensureSuccess(HttpResponse<String> response) throws IOException {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("百炼对话接口返回 HTTP " + response.statusCode()
                + "：" + abbreviate(response.body()));
        }
    }

    private static Map<String, String> message(String role, String content) {
        Map<String, String> message = new LinkedHashMap<>();
        message.put("role", role);
        message.put("content", content);
        return message;
    }

    private static void appendMessage(
        List<Map<String, String>> messages,
        String role,
        String content) {
        String normalizedRole = "assistant".equalsIgnoreCase(role) ? "assistant" : "user";
        if (!messages.isEmpty()) {
            Map<String, String> previous = messages.get(messages.size() - 1);
            if (normalizedRole.equals(previous.get("role"))) {
                previous.put("content", previous.get("content") + "\n" + content);
                return;
            }
        }
        messages.add(message(normalizedRole, content));
    }

    static String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= 500 ? text : text.substring(0, 500) + "...";
    }

}
