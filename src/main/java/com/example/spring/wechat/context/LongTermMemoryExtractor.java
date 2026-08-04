package com.example.spring.wechat.context;

import com.example.spring.chat.ChatService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class LongTermMemoryExtractor {

    private final ChatService chatService;
    private final ObjectMapper objectMapper;

    @Autowired
    public LongTermMemoryExtractor(ChatService chatService) {
        this(chatService, new ObjectMapper());
    }

    LongTermMemoryExtractor(ChatService chatService, ObjectMapper objectMapper) {
        this.chatService = chatService;
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
    }

    public List<MemoryGraphNodeDraft> extract(String sessionKey, Long conversationId, String transcript) {
        if (chatService == null || transcript == null || transcript.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(cleanJson(chatService.reply(prompt(transcript))));
            if (!root.isArray()) {
                return List.of();
            }
            List<MemoryGraphNodeDraft> drafts = new ArrayList<>();
            for (JsonNode item : root) {
                String title = item.path("title").asText("");
                String content = item.path("content").asText("");
                double confidence = item.path("confidence").asDouble(0);
                if (allowed(content, confidence)) {
                    drafts.add(new MemoryGraphNodeDraft(
                            sessionKey,
                            conversationId,
                            MemoryNodeType.LONG_TERM_MEMORY,
                            topicKey(title),
                            title,
                            content,
                            content,
                            0.9,
                            0,
                            confidence,
                            null,
                            null,
                            "conversation",
                            conversationId == null ? "" : "conversation://" + conversationId,
                            "memory_type:long_term_memory",
                            null));
                }
            }
            return drafts;
        } catch (Exception exception) {
            return List.of();
        }
    }

    private String prompt(String transcript) {
        return """
                你是微信 Agent 的长期记忆抽取器。
                只抽取稳定事实、长期偏好、持续项目背景。
                不要抽取临时任务、提醒、天气、价格、账号、密码、token、API key、支付信息、敏感医疗细节。
                只输出 JSON 数组，每项包含 type、title、content、confidence。

                对话内容：
                %s
                """.formatted(transcript);
    }

    private boolean allowed(String content, double confidence) {
        String text = content == null ? "" : content.strip().toLowerCase(Locale.ROOT);
        if (text.isBlank() || confidence < 0.75) {
            return false;
        }
        return !containsAny(text,
                "提醒", "明天", "今天", "天气", "价格", "api key", "token", "密码", "密钥",
                "支付", "银行卡", "医疗诊断");
    }

    private boolean containsAny(String text, String... markers) {
        for (String marker : markers) {
            if (text.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private String topicKey(String title) {
        return title == null ? "" : title.strip().replaceAll("\\s+", "-").toLowerCase(Locale.ROOT);
    }

    private String cleanJson(String value) {
        String text = value == null ? "" : value.strip();
        if (text.startsWith("```")) {
            text = text.replaceFirst("^```[a-zA-Z]*", "").replaceFirst("```$", "").strip();
        }
        return text;
    }
}
